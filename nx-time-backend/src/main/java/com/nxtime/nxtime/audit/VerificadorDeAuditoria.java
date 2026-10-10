package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.AuditCheckpoint;
import com.nxtime.nxtime.dto.AuditCheckpointResponse;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.dto.PlatformIntegrityResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.AuditCheckpointRepository;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 * Ahora la traza se recorre en bloques, y cada comprobación completa deja un
 * {@link AuditCheckpoint}. La verificación de cada noche arranca desde ahí y
 * solo mira lo nuevo.
 *
 * Desde octubre de 2026 un bloque es una sola consulta que trae lo justo
 * ({@link FilaDeLaCadena}), no entidades: cargándolas, cada movimiento traía
 * detrás su fichaje y su persona de uno en uno, y millón y medio de movimientos
 * eran 46 minutos (docs/PRUEBA-DE-CARGA.md).
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
    private final UserRepository userRepository;
    private final HuellaDeAuditoria huella;

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

    /**
     * Cuánto vale un recorrido completo para quien lo pida después (ADR 039).
     * Medio minuto: lo bastante para que veinte personas pulsando «Comprobar»
     * a la vez sean un recorrido y no veinte, y lo bastante poco para que nadie
     * lea como «ahora» algo de hace un rato. A cero, cada petición recorre.
     */
    private final Duration vigenciaDelRecorrido;

    /** Lo que espera una petición a que termine el recorrido de otra antes de rendirse. */
    private final Duration esperaMaxima;

    /** Un recorrido completo a la vez en esta instancia. */
    private final ReentrantLock unoALaVez = new ReentrantLock();

    /** El último recorrido completo, para repartirlo mientras siga vigente. */
    private volatile RecorridoCompleto ultimoRecorrido;

    private final AtomicLong recorridosCompletos = new AtomicLong();

    public VerificadorDeAuditoria(
            TimeEntryAuditRepository auditRepository,
            AuditCheckpointRepository checkpointRepository,
            UserRepository userRepository,
            HuellaDeAuditoria huella,
            @Value("${application.auditoria.filas-por-bloque:1000}") int filasPorBloque,
            @Value("${application.auditoria.vigencia-del-recorrido:30s}") Duration vigenciaDelRecorrido,
            @Value("${application.auditoria.espera-maxima:20s}") Duration esperaMaxima) {
        this.auditRepository = auditRepository;
        this.checkpointRepository = checkpointRepository;
        this.userRepository = userRepository;
        this.huella = huella;
        this.filasPorBloque = filasPorBloque;
        this.vigenciaDelRecorrido = vigenciaDelRecorrido;
        this.esperaMaxima = esperaMaxima;
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
        return recorrer(Desde.elPrincipio()).resultado();
    }

    /**
     * Lo mismo, contado para una empresa: es lo que ve quien lo pide desde la
     * pantalla de integridad.
     *
     * <b>Se recorre la cadena entera igual</b> --es una sola, y comprobar un
     * trozo daría un enlace roto en cada borde--, pero en las cifras solo
     * entran los movimientos de esa empresa. Hasta octubre de 2026 se devolvían
     * los totales de la instalación, y una empresa recién registrada, sin un
     * solo fichaje, veía «74 movimientos revisados»: los de todas las demás. No
     * era el contenido de nadie, pero sí cuánto se usa el servicio, y se leía
     * como si fueran suyos.
     *
     * Si la cadena falla en un movimiento de otra empresa, se dice que falla
     * pero no dónde: ver {@link AuditIntegrityResponse#rotaEnOtraParte}.
     *
     * <b>Sin transacción propia</b> ({@code NOT_SUPPORTED}), y es lo que hace
     * que esto no se pueda usar para tumbar el servicio: ver
     * {@link #recorridoCompartido}.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuditIntegrityResponse verificarPara(long empresaId) {
        return recorridoCompartido().para(empresaId);
    }

    /** {@link #verificarPara(long)} para la empresa de quien tiene la sesión. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuditIntegrityResponse verificarParaLaEmpresaDe(String emailDelActor) {
        return verificarPara(empresaDe(emailDelActor));
    }

    /**
     * Lo mismo, para quien mantiene el servicio (ADR 040): las cifras de toda
     * la instalación y, si la cadena está rota, la fila y de qué empresa es.
     *
     * Es el mismo recorrido compartido que el de las empresas, con sus mismos
     * límites: pedirlo desde el panel de plataforma no es otra forma de recorrer
     * la cadena dos veces.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PlatformIntegrityResponse verificarParaLaPlataforma() {
        return recorridoCompartido().paraLaPlataforma();
    }

    /** Cuántos movimientos tiene la traza, comprobados o no. */
    public long movimientosEnTotal() {
        return auditRepository.count();
    }

    /**
     * El recorrido completo de la cadena, uno a la vez y compartido (ADR 039).
     *
     * Recorrer la cadena entera es lo más caro que se le puede pedir a este
     * servicio, y lo puede pedir cualquier RRHH o ADMIN de cualquier empresa
     * --que, con el registro abierto, es cualquiera--. Antes cada petición hacía
     * su propio recorrido, con su hilo y su conexión del pool mientras durase:
     * con cinco conexiones, cinco peticiones a la vez dejaban sin base de datos
     * a todo lo demás, fichar incluido.
     *
     * Ahora:
     * <ul>
     *   <li><b>Uno a la vez.</b> Quien llega mientras otro recorre, espera a
     *       que termine y se lleva ESE resultado: la cadena es una, así que el
     *       recorrido vale para todas las empresas, y a cada una se le dan sus
     *       cifras.</li>
     *   <li><b>Vale un rato</b> ({@link #vigenciaDelRecorrido}): pulsar el botón
     *       veinte veces es un recorrido.</li>
     *   <li><b>Quien espera no ocupa una conexión.</b> Por eso este método se
     *       llama desde fuera de toda transacción, y quien recorre coge una
     *       solo para cada bloque: si la espera fuese dentro de una, cada
     *       petición en cola retendría su conexión sin usarla, y el pool se
     *       agotaría igual.</li>
     *   <li><b>La espera tiene tope.</b> Si el recorrido de otro tarda más de
     *       {@link #esperaMaxima}, se responde 503 y que vuelva a intentarlo: un
     *       hilo esperando sin límite es otra forma de quedarse sin hilos.</li>
     * </ul>
     *
     * El cerrojo es de esta instancia. Con varias, cada una haría el suyo: es
     * una de las cosas que hay que mover antes de poner una segunda (ADR 039).
     */
    private RecorridoCompleto recorridoCompartido() {
        RecorridoCompleto vigente = ultimoRecorrido;
        if (sirve(vigente)) {
            return vigente;
        }
        try {
            if (!unoALaVez.tryLock(esperaMaxima.toMillis(), TimeUnit.MILLISECONDS)) {
                throw comprobacionEnMarcha();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw comprobacionEnMarcha();
        }
        try {
            // Lo ha podido terminar otro mientras se esperaba el turno.
            vigente = ultimoRecorrido;
            if (sirve(vigente)) {
                return vigente;
            }
            vigente = recorrerParaTodas();
            ultimoRecorrido = vigente;
            recorridosCompletos.incrementAndGet();
            return vigente;
        } finally {
            unoALaVez.unlock();
        }
    }

    private boolean sirve(RecorridoCompleto recorrido) {
        return recorrido != null
                && Duration.between(recorrido.cuando(), Instant.now()).compareTo(vigenciaDelRecorrido) < 0;
    }

    private static BusinessException comprobacionEnMarcha() {
        return new BusinessException(
                "Ya hay una comprobación de la traza en marcha y está tardando. Vuelve a intentarlo en un momento.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** Cuántos recorridos completos se han hecho. Para los tests. */
    long recorridosCompletos() {
        return recorridosCompletos.get();
    }

    /** Cuántas peticiones esperan a que termine el recorrido en curso. Para los tests. */
    int esperandoTurno() {
        return unoALaVez.getQueueLength();
    }

    /**
     * La última comprobación automática, con las cifras de una empresa: cuántos
     * de SUS movimientos entraron en ella y cuántos ha escrito después. La
     * fecha es la de la comprobación, que es común.
     */
    public Optional<AuditCheckpointResponse> ultimaComprobacionPara(long empresaId) {
        return checkpointRepository.findTopByOrderByHastaIdDesc().map(punto -> {
            long comprobados = auditRepository.contarComprobablesDeEmpresaHasta(
                    empresaId, punto.getHastaId(), HuellaDeAuditoria.VERSION_VERIFICABLE);
            long soloEnlace = auditRepository.contarSoloEnlaceDeEmpresaHasta(
                    empresaId, punto.getHastaId(), HuellaDeAuditoria.VERSION_VERIFICABLE);
            return new AuditCheckpointResponse(
                    punto.getVerificadoEn(),
                    comprobados + soloEnlace,
                    comprobados,
                    soloEnlace,
                    auditRepository.contarDeEmpresaDesde(empresaId, punto.getHastaId()));
        });
    }

    /** {@link #ultimaComprobacionPara(long)} para la empresa de quien tiene la sesión. */
    public Optional<AuditCheckpointResponse> ultimaComprobacionParaLaEmpresaDe(String emailDelActor) {
        return ultimaComprobacionPara(empresaDe(emailDelActor));
    }

    /**
     * Con la consulta que suelta la conexión al acabar, y no cargando a la
     * persona: desde aquí se pasa a esperar turno para recorrer, y esa espera
     * no puede ir con una conexión cogida (ver el repositorio).
     */
    private long empresaDe(String emailDelActor) {
        return userRepository.findEmpresaIdByEmail(emailDelActor)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + emailDelActor));
    }

    /**
     * Verifica solo lo escrito desde el último punto de control.
     *
     * Es lo que corre cada noche. Si no hay punto de control todavía --primera
     * vez, o la comprobación anterior encontró algo-- recorre la traza entera,
     * que es lo correcto: un punto de control que no existe no se puede dar por
     * bueno.
     *
     * <b>Lo que esto no ve</b>: un cambio en una fila anterior al punto de
     * control. Para eso está {@link #verificarTodoYAnotar}, una vez por semana.
     * Lo que sí mira del pasado es que la fila del punto de control siga ahí y
     * sea la misma: es una consulta, y es lo que delata que a la traza le han
     * quitado el final.
     */
    @Transactional
    public AuditIntegrityResponse verificarLoNuevoYAnotar() {
        Optional<AuditCheckpoint> punto = checkpointRepository.findTopByOrderByHastaIdDesc();
        if (punto.isPresent() && !sigueDondeEstaba(punto.get())) {
            return faltaLoYaComprobado(punto.get(),
                    punto.get().getFilas(), punto.get().getComprobadas(), punto.get().getSoloEnlace());
        }

        Recorrido recorrido = recorrer(punto.map(Desde::desdeElPuntoDeControl).orElseGet(Desde::elPrincipio));
        if (recorrido.resultado().intacta()) {
            anotarPuntoDeControl(recorrido, punto);
        }
        return recorrido.resultado();
    }

    /**
     * Verifica la traza ENTERA, sin fiarse de ningún punto de control, y anota
     * uno si hay movimientos nuevos (ADR 042).
     *
     * La comprobación de cada noche solo mira lo escrito desde la anterior, y
     * por eso es barata; pero quiere decir que una fila ya comprobada no se
     * vuelve a mirar nunca, salvo que alguien pulse «Comprobar» en la pantalla
     * de integridad. Esto es lo que la vuelve a mirar sin depender de nadie:
     * la tarea nocturna lo usa una vez por semana.
     *
     * Va en una transacción, con una conexión cogida mientras dura: con millón
     * y medio de movimientos es menos de medio minuto de madrugada (ADR 042).
     * El recorrido que se pide desde la pantalla no puede permitirse eso y va
     * por {@link #recorridoCompartido}.
     */
    @Transactional
    public AuditIntegrityResponse verificarTodoYAnotar() {
        Optional<AuditCheckpoint> punto = checkpointRepository.findTopByOrderByHastaIdDesc();

        Recorrido recorrido = recorrer(Desde.elPrincipio());
        AuditIntegrityResponse resultado = recorrido.resultado();
        if (!resultado.intacta()) {
            return resultado;
        }
        if (punto.isPresent() && !sigueDondeEstaba(punto.get())) {
            return faltaLoYaComprobado(punto.get(),
                    resultado.movimientos(), resultado.comprobados(), resultado.soloEnlace());
        }
        anotarPuntoDeControl(recorrido, punto);
        return resultado;
    }

    /**
     * Una cadena a la que le quitan las últimas filas sigue cuadrando: cada
     * fila que queda enlaza bien con la anterior. Lo único que dice que había
     * más es el punto de control, que apuntó hasta dónde llegaba.
     */
    private boolean sigueDondeEstaba(AuditCheckpoint punto) {
        return auditRepository.existsByIdAndHash(punto.getHastaId(), punto.getHash());
    }

    private AuditIntegrityResponse faltaLoYaComprobado(
            AuditCheckpoint punto, long filas, long comprobadas, long soloEnlace) {
        return AuditIntegrityResponse.rota(filas, comprobadas, soloEnlace, punto.getHastaId(),
                "el movimiento hasta el que llegó la comprobación del " + punto.getVerificadoEn()
                        + " ya no está o ya no es el mismo: a la traza le han quitado o cambiado el final");
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

    /**
     * Un recorrido de la cadena entera, con las cifras de CADA empresa: lo que
     * hace falta para contestarle a cualquiera sin volver a recorrer.
     *
     * @param porEmpresa      por id de empresa, {comprobados, soloEnlace}
     * @param filaRota        la primera fila con problemas, o null si está intacta
     * @param empresaDeLaRota de quién es esa fila, o null si no se sabe
     */
    private record RecorridoCompleto(
            Instant cuando, Map<Long, long[]> porEmpresa, Long filaRota, Long empresaDeLaRota, String problema) {

        AuditIntegrityResponse para(long empresaId) {
            long[] suyas = porEmpresa.getOrDefault(empresaId, new long[2]);
            long comprobados = suyas[0];
            long soloEnlace = suyas[1];
            if (filaRota == null) {
                return AuditIntegrityResponse.intacta(comprobados + soloEnlace, comprobados, soloEnlace);
            }
            // El número del movimiento, solo a su dueña: ver rotaEnOtraParte.
            return Objects.equals(empresaDeLaRota, empresaId)
                    ? AuditIntegrityResponse.rota(comprobados + soloEnlace, comprobados, soloEnlace, filaRota, problema)
                    : AuditIntegrityResponse.rotaEnOtraParte(comprobados + soloEnlace, comprobados, soloEnlace);
        }

        /** Todo, sin recortar: solo para quien puede ver la instalación entera. */
        PlatformIntegrityResponse paraLaPlataforma() {
            long comprobados = porEmpresa.values().stream().mapToLong(suyas -> suyas[0]).sum();
            long soloEnlace = porEmpresa.values().stream().mapToLong(suyas -> suyas[1]).sum();
            return new PlatformIntegrityResponse(filaRota == null, comprobados + soloEnlace, comprobados,
                    soloEnlace, filaRota, empresaDeLaRota, problema, cuando);
        }
    }

    private RecorridoCompleto recorrerParaTodas() {
        long ultimoId = 0L;
        // La primera fila de la tabla no enlaza con nada.
        String hashDeLaAnterior = null;
        Map<Long, long[]> porEmpresa = new HashMap<>();

        List<FilaDeLaCadena> bloque;
        while (!(bloque = siguienteBloque(ultimoId)).isEmpty()) {
            for (FilaDeLaCadena fila : bloque) {
                Long empresa = fila.empresaId();
                String problema = revisar(fila, hashDeLaAnterior);
                if (problema != null) {
                    // El detalle, para quien mantiene el servicio: a quien
                    // pregunte desde otra empresa no se le va a dar.
                    log.warn("Cadena de auditoría rota en la fila {}, de la empresa {}: {}.",
                            fila.id(), empresa, problema);
                    return new RecorridoCompleto(Instant.now(), porEmpresa, fila.id(), empresa, problema);
                }
                if (empresa != null) {
                    long[] suyas = porEmpresa.computeIfAbsent(empresa, id -> new long[2]);
                    suyas[fila.versionHash() >= HuellaDeAuditoria.VERSION_VERIFICABLE ? 0 : 1]++;
                }
                hashDeLaAnterior = fila.hash();
                ultimoId = fila.id();
            }
        }
        return new RecorridoCompleto(Instant.now(), porEmpresa, null, null, null);
    }

    /**
     * Lo que sale de un recorrido: el resultado y hasta qué fila llegó, con su
     * hash. Lo segundo es lo que firma el punto de control.
     */
    private record Recorrido(AuditIntegrityResponse resultado, long ultimoId, String ultimoHash) {
    }

    private Recorrido recorrer(Desde desde) {
        long ultimoId = desde.ultimoId();
        String hashDeLaAnterior = desde.hashDeLaAnterior();
        long filas = desde.filas();
        long comprobadas = desde.comprobadas();
        long soloEnlace = desde.soloEnlace();

        List<FilaDeLaCadena> bloque;
        while (!(bloque = siguienteBloque(ultimoId)).isEmpty()) {
            for (FilaDeLaCadena fila : bloque) {
                String problema = revisar(fila, hashDeLaAnterior);
                if (problema != null) {
                    return new Recorrido(
                            AuditIntegrityResponse.rota(filas, comprobadas, soloEnlace, fila.id(), problema),
                            ultimoId, hashDeLaAnterior);
                }
                filas++;
                if (fila.versionHash() >= HuellaDeAuditoria.VERSION_VERIFICABLE) {
                    comprobadas++;
                } else {
                    soloEnlace++;
                }
                hashDeLaAnterior = fila.hash();
                ultimoId = fila.id();
            }
        }

        return new Recorrido(
                AuditIntegrityResponse.intacta(filas, comprobadas, soloEnlace), ultimoId, hashDeLaAnterior);
    }

    /**
     * Los movimientos que siguen a uno dado, en orden de escritura.
     *
     * Una consulta por bloque, y cada una en su propia transacción corta (la
     * que declara el repositorio: ver allí por qué hace falta declararla). El
     * recorrido completo iba antes en una sola, y con millón y medio de
     * movimientos esa transacción duraba tres cuartos de hora con una de las
     * cinco conexiones cogida (docs/PRUEBA-DE-CARGA.md).
     *
     * No son entidades, así que no hay sesión de JPA que vaciar entre bloque y
     * bloque.
     */
    private List<FilaDeLaCadena> siguienteBloque(long desdeId) {
        return auditRepository.findBloqueDeLaCadena(desdeId, PageRequest.of(0, filasPorBloque));
    }

    /**
     * Anota hasta dónde ha llegado un recorrido que ha salido intacto.
     *
     * <b>Firma la última fila RECORRIDA</b>, no la última de la tabla. Hasta
     * octubre de 2026 se releía la última de la tabla al acabar, y si alguien
     * fichaba entre el final del recorrido y esa lectura, el punto de control
     * daba por comprobada una fila que nadie había mirado: la noche siguiente
     * se empezaba después de ella.
     */
    private void anotarPuntoDeControl(Recorrido recorrido, Optional<AuditCheckpoint> ultimoPunto) {
        if (recorrido.ultimoHash() == null) {
            // La traza está vacía: no hay nada que firmar.
            return;
        }
        if (ultimoPunto.isPresent() && ultimoPunto.get().getHastaId() >= recorrido.ultimoId()) {
            // No ha crecido nada desde la última vez: volver a anotar el mismo
            // punto chocaría con uq_punto_control_hasta, y además no diría nada
            // nuevo.
            return;
        }
        AuditIntegrityResponse resultado = recorrido.resultado();
        checkpointRepository.save(AuditCheckpoint.builder()
                .hastaId(recorrido.ultimoId())
                .hash(recorrido.ultimoHash())
                .filas(resultado.movimientos())
                .comprobadas(resultado.comprobados())
                .soloEnlace(resultado.soloEnlace())
                .verificadoEn(Instant.now())
                .build());
        log.info("Cadena de auditoría comprobada hasta la fila {}: {} movimientos, {} recalculados.",
                recorrido.ultimoId(), resultado.movimientos(), resultado.comprobados());
    }

    /** Qué le pasa a esta fila, o null si está bien. */
    private String revisar(FilaDeLaCadena fila, String hashDeLaAnterior) {
        // El enlace: lo que la fila dice que había antes contra lo que había
        // antes de verdad. La primera de todas no enlaza con nada.
        if (!Objects.equals(fila.hashAnterior(), hashDeLaAnterior)) {
            return "el enlace con la fila anterior no cuadra: falta una fila, sobra una o se han reordenado";
        }
        if (fila.versionHash() < HuellaDeAuditoria.VERSION_VERIFICABLE) {
            return null;
        }
        if (!huella.calcular(fila, hashDeLaAnterior).equals(fila.hash())) {
            return "el contenido de la fila no coincide con su hash: le han cambiado algo";
        }
        return null;
    }
}
