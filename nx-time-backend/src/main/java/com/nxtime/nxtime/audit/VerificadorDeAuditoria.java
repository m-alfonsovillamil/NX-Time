package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.AuditCheckpoint;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.repository.AuditCheckpointRepository;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recorre la traza de auditoría y dice si sigue intacta.
 *
 * El RD-ley 8/2019 exige conservar el registro de jornada cuatro años y que sea
 * fiable, y el Real Decreto de registro horario digital que se tramita en 2026
 * concreta esa fiabilidad en "integridad demostrable". **Demostrable** es la
 * palabra: una cadena de hashes que nadie comprueba nunca no demuestra nada, y
 * hasta septiembre de 2026 nadie podía comprobarla (ver {@link
 * HuellaDeAuditoria}).
 *
 * Comprueba dos cosas distintas, y las cuenta por separado porque significan
 * cosas distintas:
 *
 * <ol>
 *   <li><b>El enlace.</b> Cada fila guarda el hash de la anterior. Si no
 *       coincide, es que falta una fila, sobra una o se han reordenado. Esto
 *       se puede comprobar en TODAS las filas, también en las viejas.</li>
 *   <li><b>El contenido.</b> Se recalcula el hash de la fila y se compara con
 *       el guardado. Si no coincide, a esa fila le han cambiado algo por
 *       debajo. Solo se puede en las filas de la versión 2 en adelante.</li>
 * </ol>
 *
 * Las filas viejas no se dan por buenas ni por rotas: se cuentan aparte, como
 * "no comprobables". Decir que algo está verificado cuando no se ha podido
 * verificar es peor que no tener verificador.
 *
 * Se para en el primer problema. Un fallo en la fila 40 hace que todas las
 * siguientes fallen también --así funciona una cadena--, y una lista de mil
 * errores esconde el único dato que importa: dónde empezó.
 *
 * <h2>Por bloques, y con puntos de control (Fase A4)</h2>
 *
 * Antes se leía la tabla entera de una vez. Con cuatro años de traza de todas
 * las empresas y dos columnas JSONB por fila, eso solo podía ir a peor — y
 * tenía un efecto de segundo orden más grave que el consumo de memoria: como
 * costaba, se pedía poco, y una manipulación podía tardar meses en salir a la
 * luz.
 *
 * Ahora la traza se recorre en bloques, soltando cada uno antes de pedir el
 * siguiente ({@code entityManager.clear()}), y cada comprobación completa deja
 * un {@link AuditCheckpoint}. La verificación de cada noche arranca desde ahí y
 * solo mira lo nuevo.
 *
 * Un punto de control no se cree a sí mismo más de lo que debe: solo se escribe
 * cuando la cadena ha salido intacta, y dice hasta qué fila. Si algo falla, no
 * se escribe ninguno, así que la próxima verificación volverá a pasar por el
 * tramo malo en vez de darlo por bueno.
 */
@Service
@Transactional(readOnly = true)
public class VerificadorDeAuditoria {

    private static final Logger log = LoggerFactory.getLogger(VerificadorDeAuditoria.class);

    private final TimeEntryAuditRepository auditRepository;
    private final AuditCheckpointRepository checkpointRepository;
    private final HuellaDeAuditoria huella;
    private final EntityManager entityManager;

    /**
     * Filas por bloque. Suficientemente grande para que no sean miles de
     * consultas y suficientemente pequeño para que el bloque quepa holgado en
     * memoria aunque las filas traigan JSON grande.
     *
     * Es configurable para que los tests puedan bajarlo y cruzar varios bloques
     * sin sembrar miles de filas. Un recorrido que solo se prueba con un bloque
     * no prueba el recorrido.
     */
    private final int filasPorBloque;

    public VerificadorDeAuditoria(
            TimeEntryAuditRepository auditRepository,
            AuditCheckpointRepository checkpointRepository,
            HuellaDeAuditoria huella,
            EntityManager entityManager,
            @Value("${application.auditoria.filas-por-bloque:1000}") int filasPorBloque) {
        this.auditRepository = auditRepository;
        this.checkpointRepository = checkpointRepository;
        this.huella = huella;
        this.entityManager = entityManager;
        this.filasPorBloque = filasPorBloque;
    }

    /**
     * Verifica la traza entera, en orden de escritura, sin usar puntos de
     * control.
     *
     * La cadena es global (cada fila enlaza con la anterior de la tabla, sea
     * de la empresa que sea), así que verificar "solo mi empresa" no tendría
     * sentido: el enlace se rompería en cada salto. Es una consecuencia de
     * cómo se encadena, y por eso esto lo puede pedir RRHH, no cualquiera.
     */
    public AuditIntegrityResponse verificar() {
        return recorrer(Desde.elPrincipio());
    }

    /**
     * Verifica solo lo escrito desde el último punto de control.
     *
     * Es lo que corre cada noche. Si no hay punto de control todavía --primera
     * vez, o la comprobación anterior encontró algo-- recorre la traza entera,
     * que es lo correcto: un punto de control que no existe no se puede dar por
     * bueno.
     */
    @Transactional
    public AuditIntegrityResponse verificarLoNuevoYAnotar() {
        Desde desde = checkpointRepository.findTopByOrderByHastaIdDesc()
                .map(Desde::desdeElPuntoDeControl)
                .orElseGet(Desde::elPrincipio);

        AuditIntegrityResponse resultado = recorrer(desde);
        if (resultado.intacta()) {
            anotarPuntoDeControl(resultado, desde);
        }
        return resultado;
    }

    /** El último punto de control, para poder decir "comprobada anoche, intacta". */
    public Optional<AuditCheckpoint> ultimoPuntoDeControl() {
        return checkpointRepository.findTopByOrderByHastaIdDesc();
    }

    /**
     * Cuántos movimientos se han escrito después de ese punto de control.
     *
     * Va junto al "comprobada anoche" porque sin esto la frase engaña: si desde
     * anoche se han fichado doscientas jornadas, lo comprobado es el pasado, no
     * el presente.
     */
    public long movimientosSinRevisar(AuditCheckpoint punto) {
        return auditRepository.countByIdGreaterThan(punto.getHastaId());
    }

    // ------------------------------------------------------------------

    /**
     * Desde dónde arranca un recorrido: qué fila ya está dada por buena, con
     * qué hash enlaza la siguiente y cuántas filas quedaron contadas por
     * detrás.
     *
     * Existe para que {@link #recorrer} no tenga que saber si viene de un punto
     * de control o del principio de la traza.
     */
    private record Desde(long ultimoId, String hashDeLaAnterior, long filas, long comprobadas, long soloEnlace) {

        static Desde elPrincipio() {
            // La primera fila de la tabla no enlaza con nada: su hashAnterior
            // es null, y eso es lo que hay que comparar.
            return new Desde(0L, null, 0, 0, 0);
        }

        static Desde desdeElPuntoDeControl(AuditCheckpoint punto) {
            return new Desde(punto.getHastaId(), punto.getHash(),
                    punto.getFilas(), punto.getComprobadas(), punto.getSoloEnlace());
        }
    }

    private AuditIntegrityResponse recorrer(Desde desde) {
        long ultimoId = desde.ultimoId();
        String hashDeLaAnterior = desde.hashDeLaAnterior();
        long filas = desde.filas();
        long comprobadas = desde.comprobadas();
        long soloEnlace = desde.soloEnlace();

        List<TimeEntryAudit> bloque;
        while (!(bloque = auditRepository.findBloqueDesde(ultimoId, PageRequest.of(0, filasPorBloque))).isEmpty()) {
            for (TimeEntryAudit fila : bloque) {
                String problema = revisar(fila, hashDeLaAnterior);
                if (problema != null) {
                    return AuditIntegrityResponse.rota(filas, comprobadas, soloEnlace, fila.getId(), problema);
                }
                filas++;
                if (fila.getVersionHash() >= HuellaDeAuditoria.VERSION_VERIFICABLE) {
                    comprobadas++;
                } else {
                    soloEnlace++;
                }
                hashDeLaAnterior = fila.getHash();
                ultimoId = fila.getId();
            }
            // Sin esto, la sesión de JPA acumularía todas las filas de todos
            // los bloques y el recorrido por bloques no serviría de nada: el
            // problema de memoria seguiría ahí, solo que más repartido.
            entityManager.clear();
        }

        return AuditIntegrityResponse.intacta(filas, comprobadas, soloEnlace);
    }

    private void anotarPuntoDeControl(AuditIntegrityResponse resultado, Desde desde) {
        if (resultado.movimientos() == desde.filas()) {
            // No ha crecido nada desde la última vez: volver a anotar el mismo
            // punto chocaría con uq_punto_control_hasta, y además no diría nada
            // nuevo.
            return;
        }
        // Se relee la última fila para quedarse con su hash. Es una consulta
        // más, pero deja claro qué se está firmando; el recorrido ya soltó su
        // bloque con el clear().
        TimeEntryAudit ultima = auditRepository.findTopByOrderByIdDesc().orElse(null);
        if (ultima == null) {
            return;
        }
        checkpointRepository.save(AuditCheckpoint.builder()
                .hastaId(ultima.getId())
                .hash(ultima.getHash())
                .filas(resultado.movimientos())
                .comprobadas(resultado.comprobados())
                .soloEnlace(resultado.soloEnlace())
                .verificadoEn(Instant.now())
                .build());
        log.info("Cadena de auditoría comprobada hasta la fila {}: {} movimientos, {} recalculados.",
                ultima.getId(), resultado.movimientos(), resultado.comprobados());
    }

    /** Qué le pasa a esta fila, o null si está bien. */
    private String revisar(TimeEntryAudit fila, String hashDeLaAnterior) {
        // El enlace: lo que la fila dice que había antes contra lo que había
        // antes de verdad. La primera de todas no enlaza con nada.
        if (!Objects.equals(fila.getHashAnterior(), hashDeLaAnterior)) {
            return "el enlace con la fila anterior no cuadra: falta una fila, sobra una o se han reordenado";
        }
        if (fila.getVersionHash() < HuellaDeAuditoria.VERSION_VERIFICABLE) {
            return null;
        }
        if (!huella.calcular(fila, hashDeLaAnterior).equals(fila.getHash())) {
            return "el contenido de la fila no coincide con su hash: le han cambiado algo";
        }
        return null;
    }
}
