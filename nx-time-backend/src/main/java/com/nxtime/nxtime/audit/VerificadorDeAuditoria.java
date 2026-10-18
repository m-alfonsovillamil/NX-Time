package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.AuditCheckpoint;
import com.nxtime.nxtime.domain.EslabonRetirado;
import com.nxtime.nxtime.dto.AuditCheckpointResponse;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.dto.PlatformIntegrityResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.AuditCheckpointRepository;
import com.nxtime.nxtime.repository.EslabonRetiradoRepository;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
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
 * Se para en el primer problema de cada cadena. Un fallo en la fila 40 hace que
 * todas las siguientes fallen también --así funciona una cadena--, y una lista
 * de mil errores esconde el único dato que importa: dónde empezó.
 *
 * <h2>Una cadena por empresa (V46, ADR 050)</h2>
 *
 * Hasta octubre de 2026 la cadena era una sola para todas las empresas. Desde
 * V46 hay:
 *
 * <ul>
 *   <li><b>El tramo común</b>: las filas de antes (versiones 1 y 2), que
 *       siguen encadenadas entre sí como se escribieron. No se reescribe ni un
 *       hash. Cuando una empresa se borra, sus filas de este tramo dejan una
 *       lápida ({@link EslabonRetirado}) con su sitio y sus hashes, y el tramo
 *       se sigue recorriendo enlace a enlace.</li>
 *   <li><b>Una cadena por empresa</b> (versión 3): cada fila enlaza con la
 *       anterior de SU empresa, y la primera con su génesis. Una cadena rota en
 *       una empresa no deja sin comprobar a las demás.</li>
 * </ul>
 *
 * <h2>Por bloques, y con puntos de control (Fase A4)</h2>
 *
 * Cada cadena se recorre en bloques, y cada comprobación completa deja un
 * {@link AuditCheckpoint} de esa cadena. La verificación de cada noche arranca
 * desde ahí y solo mira lo nuevo.
 *
 * Un bloque es una sola consulta que trae lo justo ({@link FilaDeLaCadena}), no
 * entidades: cargándolas, cada movimiento traía detrás su fichaje y su persona
 * de uno en uno, y millón y medio de movimientos eran 46 minutos
 * (docs/PRUEBA-DE-CARGA.md).
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
    private final EslabonRetiradoRepository lapidas;
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
            EslabonRetiradoRepository lapidas,
            UserRepository userRepository,
            HuellaDeAuditoria huella,
            @Value("${application.auditoria.filas-por-bloque:1000}") int filasPorBloque,
            @Value("${application.auditoria.vigencia-del-recorrido:30s}") Duration vigenciaDelRecorrido,
            @Value("${application.auditoria.espera-maxima:20s}") Duration esperaMaxima) {
        this.auditRepository = auditRepository;
        this.checkpointRepository = checkpointRepository;
        this.lapidas = lapidas;
        this.userRepository = userRepository;
        this.huella = huella;
        this.filasPorBloque = filasPorBloque;
        this.vigenciaDelRecorrido = vigenciaDelRecorrido;
        this.esperaMaxima = esperaMaxima;
    }

    // ------------------------------------------------------------------
    // Las cadenas
    // ------------------------------------------------------------------

    /** Una cadena: el tramo común ({@code empresaId} null) o la de una empresa. */
    record Cadena(Long empresaId) {

        static Cadena comun() {
            return new Cadena(null);
        }

        static Cadena de(long empresaId) {
            return new Cadena(empresaId);
        }

        boolean esComun() {
            return empresaId == null;
        }

        /** Con qué enlaza su primera fila: nada en el tramo común, la génesis en una empresa. */
        String inicio() {
            return esComun() ? null : HuellaDeAuditoria.genesis(empresaId);
        }
    }

    /** Todas las cadenas que hay: el tramo común y las de las empresas que ya tienen alguna fila suya. */
    private List<Cadena> cadenas() {
        List<Cadena> todas = new ArrayList<>();
        todas.add(Cadena.comun());
        auditRepository.empresasConCadena().forEach(id -> todas.add(Cadena.de(id)));
        return todas;
    }

    // ------------------------------------------------------------------
    // Verificar
    // ------------------------------------------------------------------

    /**
     * Verifica todas las cadenas enteras, en orden de escritura, sin usar
     * puntos de control. Las cifras son las de la instalación; si alguna está
     * rota, la primera que se encuentre.
     */
    public AuditIntegrityResponse verificar() {
        return sumar(cadenas().stream().map(cadena -> recorrer(cadena, Desde.elPrincipio(cadena))).toList());
    }

    /**
     * Lo que ve una empresa desde la pantalla de integridad: sus movimientos
     * del tramo común y su propia cadena.
     *
     * Si el tramo común falla en un movimiento de otra empresa, se dice que
     * falla pero no dónde: ver {@link AuditIntegrityResponse#rotaEnOtraParte}.
     * Si falla la cadena de otra empresa, no le afecta: es otra cadena.
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
     * la instalación y, si algo está roto, la fila y de qué empresa es.
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
     * El recorrido completo de todas las cadenas, uno a la vez y compartido
     * (ADR 039).
     *
     * Recorrer la traza entera es lo más caro que se le puede pedir a este
     * servicio, y lo puede pedir cualquier RRHH o ADMIN de cualquier empresa.
     * Así que:
     * <ul>
     *   <li><b>Uno a la vez.</b> Quien llega mientras otro recorre, espera a
     *       que termine y se lleva ESE resultado, con las cifras de su
     *       empresa.</li>
     *   <li><b>Vale un rato</b> ({@link #vigenciaDelRecorrido}).</li>
     *   <li><b>Quien espera no ocupa una conexión.</b> Por eso este método se
     *       llama desde fuera de toda transacción, y quien recorre coge una
     *       solo para cada bloque.</li>
     *   <li><b>La espera tiene tope</b> ({@link #esperaMaxima}): después, 503.</li>
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

    // ------------------------------------------------------------------
    // Lo que dejó dicho la última comprobación
    // ------------------------------------------------------------------

    /**
     * La última comprobación automática, con las cifras de una empresa: lo
     * comprobado de sus movimientos del tramo común y de su cadena, y lo que ha
     * escrito después. La fecha es la de la comprobación más reciente de las
     * dos.
     */
    public Optional<AuditCheckpointResponse> ultimaComprobacionPara(long empresaId) {
        Optional<AuditCheckpoint> comun = checkpointRepository.findTopByEmpresaIdIsNullOrderByHastaIdDesc();
        Optional<AuditCheckpoint> propio = checkpointRepository.findTopByEmpresaIdOrderByHastaIdDesc(empresaId);
        if (comun.isEmpty() && propio.isEmpty()) {
            // Ninguna cadena suya está comprobada todavía. Si la comprobación de
            // anoche existió (de otras cadenas), lo suyo es «nada comprobado y
            // esto pendiente», no «nunca se ha comprobado nada».
            return checkpointRepository.findTopByOrderByVerificadoEnDesc().map(otro -> {
                long pendientes = auditRepository.contarDelTramoComunDeEmpresaDesde(empresaId, 0L)
                        + auditRepository.contarDeLaCadenaDeEmpresaDesde(empresaId, 0L);
                return new AuditCheckpointResponse(otro.getVerificadoEn(), 0, 0, 0, pendientes);
            });
        }
        long hastaComun = comun.map(AuditCheckpoint::getHastaId).orElse(0L);
        long comprobados = comun.map(p -> auditRepository.contarComprobablesDeEmpresaHasta(
                empresaId, p.getHastaId(), HuellaDeAuditoria.VERSION_VERIFICABLE)).orElse(0L)
                + propio.map(AuditCheckpoint::getComprobadas).orElse(0L);
        long soloEnlace = comun.map(p -> auditRepository.contarSoloEnlaceDeEmpresaHasta(
                empresaId, p.getHastaId(), HuellaDeAuditoria.VERSION_VERIFICABLE)).orElse(0L)
                + propio.map(AuditCheckpoint::getSoloEnlace).orElse(0L);
        long pendientes = auditRepository.contarDelTramoComunDeEmpresaDesde(empresaId, hastaComun)
                + auditRepository.contarDeLaCadenaDeEmpresaDesde(empresaId,
                        propio.map(AuditCheckpoint::getHastaId).orElse(0L));
        Instant cuando = Stream.of(comun, propio).flatMap(Optional::stream)
                .map(AuditCheckpoint::getVerificadoEn).max(Comparator.naturalOrder()).orElseThrow();
        return Optional.of(new AuditCheckpointResponse(cuando, comprobados + soloEnlace, comprobados, soloEnlace, pendientes));
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

    /** Lo de toda la instalación, para el panel de plataforma: cuándo, cuánto comprobado y cuánto pendiente. */
    public record Resumen(Instant ultimaComprobacion, long comprobados, long sinRevisar) {
    }

    public Resumen resumenDeLaInstalacion() {
        List<AuditCheckpoint> ultimos = checkpointRepository.ultimosDeCadaCadena();
        Map<Long, Long> hastaPorEmpresa = new HashMap<>();
        long hastaComun = 0L;
        long comprobados = 0L;
        Instant cuando = null;
        for (AuditCheckpoint punto : ultimos) {
            comprobados += punto.getFilas();
            if (cuando == null || punto.getVerificadoEn().isAfter(cuando)) {
                cuando = punto.getVerificadoEn();
            }
            if (punto.getEmpresaId() == null) {
                hastaComun = punto.getHastaId();
            } else {
                hastaPorEmpresa.put(punto.getEmpresaId(), punto.getHastaId());
            }
        }
        long sinRevisar = auditRepository.contarDelTramoComunDesde(hastaComun);
        for (Long empresa : auditRepository.empresasConCadena()) {
            sinRevisar += auditRepository.contarDeLaCadenaDeEmpresaDesde(empresa, hastaPorEmpresa.getOrDefault(empresa, 0L));
        }
        return new Resumen(cuando, comprobados, sinRevisar);
    }

    // ------------------------------------------------------------------
    // La comprobación de cada noche
    // ------------------------------------------------------------------

    /**
     * Verifica solo lo escrito desde el último punto de control de cada cadena.
     *
     * Es lo que corre cada noche. Una cadena sin punto de control --la primera
     * vez, o la anterior encontró algo-- se recorre entera, que es lo correcto:
     * un punto de control que no existe no se puede dar por bueno.
     *
     * <b>Lo que esto no ve</b>: un cambio en una fila anterior al punto de
     * control. Para eso está {@link #verificarTodoYAnotar}, una vez por semana.
     * Lo que sí mira del pasado es que la fila de cada punto de control siga ahí
     * y sea la misma (o su lápida, si su empresa se borró): es lo que delata que
     * a una cadena le han quitado el final.
     */
    @Transactional
    public AuditIntegrityResponse verificarLoNuevoYAnotar() {
        List<AuditIntegrityResponse> resultados = new ArrayList<>();
        for (Cadena cadena : cadenas()) {
            Optional<AuditCheckpoint> punto = ultimoPunto(cadena);
            if (punto.isPresent() && !sigueDondeEstaba(punto.get())) {
                resultados.add(faltaLoYaComprobado(punto.get(),
                        punto.get().getFilas(), punto.get().getComprobadas(), punto.get().getSoloEnlace()));
                continue;
            }
            Recorrido recorrido = recorrer(cadena, punto.map(Desde::desdeElPuntoDeControl)
                    .orElseGet(() -> Desde.elPrincipio(cadena)));
            if (recorrido.resultado().intacta()) {
                anotarPuntoDeControl(cadena, recorrido, punto);
            }
            resultados.add(recorrido.resultado());
        }
        return sumarResultados(resultados);
    }

    /**
     * Verifica todas las cadenas ENTERAS, sin fiarse de ningún punto de
     * control, y anota uno en cada cadena que haya crecido (ADR 042). La tarea
     * nocturna lo usa una vez por semana.
     */
    @Transactional
    public AuditIntegrityResponse verificarTodoYAnotar() {
        List<AuditIntegrityResponse> resultados = new ArrayList<>();
        for (Cadena cadena : cadenas()) {
            Optional<AuditCheckpoint> punto = ultimoPunto(cadena);
            Recorrido recorrido = recorrer(cadena, Desde.elPrincipio(cadena));
            AuditIntegrityResponse resultado = recorrido.resultado();
            if (resultado.intacta() && punto.isPresent() && !sigueDondeEstaba(punto.get())) {
                resultado = faltaLoYaComprobado(punto.get(),
                        resultado.movimientos(), resultado.comprobados(), resultado.soloEnlace());
            } else if (resultado.intacta()) {
                anotarPuntoDeControl(cadena, recorrido, punto);
            }
            resultados.add(resultado);
        }
        return sumarResultados(resultados);
    }

    private Optional<AuditCheckpoint> ultimoPunto(Cadena cadena) {
        return cadena.esComun()
                ? checkpointRepository.findTopByEmpresaIdIsNullOrderByHastaIdDesc()
                : checkpointRepository.findTopByEmpresaIdOrderByHastaIdDesc(cadena.empresaId());
    }

    /**
     * Una cadena a la que le quitan las últimas filas sigue cuadrando: cada
     * fila que queda enlaza bien con la anterior. Lo único que dice que había
     * más es el punto de control, que apuntó hasta dónde llegaba. En el tramo
     * común, la fila puede ser ya una lápida: su empresa se borró, y eso no es
     * quitarle el final.
     */
    private boolean sigueDondeEstaba(AuditCheckpoint punto) {
        return auditRepository.existsByIdAndHash(punto.getHastaId(), punto.getHash())
                || (punto.getEmpresaId() == null && lapidas.existsByIdAndHash(punto.getHastaId(), punto.getHash()));
    }

    private AuditIntegrityResponse faltaLoYaComprobado(
            AuditCheckpoint punto, long filas, long comprobadas, long soloEnlace) {
        return AuditIntegrityResponse.rota(filas, comprobadas, soloEnlace, punto.getHastaId(),
                "el movimiento hasta el que llegó la comprobación del " + punto.getVerificadoEn()
                        + " ya no está o ya no es el mismo: a la traza le han quitado o cambiado el final");
    }

    /** El punto de control más reciente, sea de la cadena que sea: «comprobada anoche». */
    public Optional<AuditCheckpoint> ultimoPuntoDeControl() {
        return checkpointRepository.findTopByOrderByVerificadoEnDesc();
    }

    // ------------------------------------------------------------------

    /**
     * Desde dónde arranca un recorrido: qué fila ya está dada por buena, con
     * qué hash enlaza la siguiente y cuántas filas quedaron contadas por
     * detrás.
     */
    private record Desde(long ultimoId, String hashDeLaAnterior, long filas, long comprobadas, long soloEnlace) {

        static Desde elPrincipio(Cadena cadena) {
            return new Desde(0L, cadena.inicio(), 0, 0, 0);
        }

        static Desde desdeElPuntoDeControl(AuditCheckpoint punto) {
            return new Desde(punto.getHastaId(), punto.getHash(),
                    punto.getFilas(), punto.getComprobadas(), punto.getSoloEnlace());
        }
    }

    /** Dónde se rompió una cadena: la fila, de qué empresa es y qué le pasa. */
    private record Rotura(long fila, Long empresa, String problema) {
    }

    /**
     * Un recorrido de todas las cadenas, con las cifras de CADA empresa: lo que
     * hace falta para contestarle a cualquiera sin volver a recorrer.
     *
     * @param porEmpresa    por id de empresa, {comprobados, soloEnlace}
     * @param delComun      dónde se rompió el tramo común, o null
     * @param deLasEmpresas dónde se rompió la cadena de cada empresa que esté rota
     */
    private record RecorridoCompleto(
            Instant cuando, Map<Long, long[]> porEmpresa, Rotura delComun, Map<Long, Rotura> deLasEmpresas) {

        AuditIntegrityResponse para(long empresaId) {
            long[] suyas = porEmpresa.getOrDefault(empresaId, new long[2]);
            long comprobados = suyas[0];
            long soloEnlace = suyas[1];
            Rotura propia = deLasEmpresas.get(empresaId);
            if (propia != null) {
                return AuditIntegrityResponse.rota(comprobados + soloEnlace, comprobados, soloEnlace,
                        propia.fila(), propia.problema());
            }
            if (delComun == null) {
                return AuditIntegrityResponse.intacta(comprobados + soloEnlace, comprobados, soloEnlace);
            }
            // El número del movimiento, solo a su dueña: ver rotaEnOtraParte.
            return Objects.equals(delComun.empresa(), empresaId)
                    ? AuditIntegrityResponse.rota(comprobados + soloEnlace, comprobados, soloEnlace,
                            delComun.fila(), delComun.problema())
                    : AuditIntegrityResponse.rotaEnOtraParte(comprobados + soloEnlace, comprobados, soloEnlace);
        }

        /** Todo, sin recortar: solo para quien puede ver la instalación entera. */
        PlatformIntegrityResponse paraLaPlataforma() {
            long comprobados = porEmpresa.values().stream().mapToLong(suyas -> suyas[0]).sum();
            long soloEnlace = porEmpresa.values().stream().mapToLong(suyas -> suyas[1]).sum();
            Rotura primera = delComun != null ? delComun : deLasEmpresas.values().stream().findFirst().orElse(null);
            return new PlatformIntegrityResponse(primera == null, comprobados + soloEnlace, comprobados,
                    soloEnlace, primera == null ? null : primera.fila(), primera == null ? null : primera.empresa(),
                    primera == null ? null : primera.problema(), cuando);
        }
    }

    private RecorridoCompleto recorrerParaTodas() {
        Map<Long, long[]> porEmpresa = new HashMap<>();
        Rotura delComun = null;
        Map<Long, Rotura> deLasEmpresas = new LinkedHashMap<>();
        for (Cadena cadena : cadenas()) {
            Rotura rotura = recorrerContando(cadena, porEmpresa);
            if (rotura == null) {
                continue;
            }
            // El detalle, para quien mantiene el servicio: a quien pregunte
            // desde otra empresa no se le va a dar.
            log.warn("Cadena de auditoría {} rota en la fila {}, de la empresa {}: {}.",
                    cadena.esComun() ? "del tramo común" : "de la empresa " + cadena.empresaId(),
                    rotura.fila(), rotura.empresa(), rotura.problema());
            if (cadena.esComun()) {
                delComun = rotura;
            } else {
                deLasEmpresas.put(cadena.empresaId(), rotura);
            }
        }
        return new RecorridoCompleto(Instant.now(), porEmpresa, delComun, deLasEmpresas);
    }

    /** Recorre una cadena entera sumando a cada empresa lo suyo; devuelve dónde se rompió, o null. */
    private Rotura recorrerContando(Cadena cadena, Map<Long, long[]> porEmpresa) {
        long ultimoId = 0L;
        String hashDeLaAnterior = cadena.inicio();
        List<FilaDeLaCadena> bloque;
        while (!(bloque = siguienteBloque(cadena, ultimoId)).isEmpty()) {
            for (FilaDeLaCadena fila : bloque) {
                String problema = revisar(fila, hashDeLaAnterior);
                if (problema != null) {
                    return new Rotura(fila.id(), fila.empresaId(), problema);
                }
                // Las lápidas no cuentan para nadie: su empresa ya no existe.
                if (!fila.retirada() && fila.empresaId() != null) {
                    long[] suyas = porEmpresa.computeIfAbsent(fila.empresaId(), id -> new long[2]);
                    suyas[comprobable(fila) ? 0 : 1]++;
                }
                hashDeLaAnterior = fila.hash();
                ultimoId = fila.id();
            }
        }
        return null;
    }

    /**
     * Lo que sale de un recorrido: el resultado y hasta qué fila llegó, con su
     * hash. Lo segundo es lo que firma el punto de control.
     */
    private record Recorrido(AuditIntegrityResponse resultado, long ultimoId, String ultimoHash) {
    }

    private Recorrido recorrer(Cadena cadena, Desde desde) {
        long ultimoId = desde.ultimoId();
        String hashDeLaAnterior = desde.hashDeLaAnterior();
        long filas = desde.filas();
        long comprobadas = desde.comprobadas();
        long soloEnlace = desde.soloEnlace();

        List<FilaDeLaCadena> bloque;
        while (!(bloque = siguienteBloque(cadena, ultimoId)).isEmpty()) {
            for (FilaDeLaCadena fila : bloque) {
                String problema = revisar(fila, hashDeLaAnterior);
                if (problema != null) {
                    return new Recorrido(
                            AuditIntegrityResponse.rota(filas, comprobadas, soloEnlace, fila.id(), problema),
                            ultimoId, hashDeLaAnterior);
                }
                filas++;
                if (comprobable(fila)) {
                    comprobadas++;
                } else {
                    soloEnlace++;
                }
                hashDeLaAnterior = fila.hash();
                ultimoId = fila.id();
            }
        }

        // ultimoHash null: la cadena no tiene ninguna fila (o el tramo común
        // está vacío), y no hay nada que firmar.
        return new Recorrido(AuditIntegrityResponse.intacta(filas, comprobadas, soloEnlace), ultimoId,
                ultimoId == desde.ultimoId() && desde.ultimoId() == 0L ? null : hashDeLaAnterior);
    }

    /** Las cifras de varias cadenas en una respuesta; si alguna está rota, la primera rota. */
    private static AuditIntegrityResponse sumar(List<Recorrido> recorridos) {
        return sumarResultados(recorridos.stream().map(Recorrido::resultado).toList());
    }

    private static AuditIntegrityResponse sumarResultados(List<AuditIntegrityResponse> resultados) {
        long movimientos = resultados.stream().mapToLong(AuditIntegrityResponse::movimientos).sum();
        long comprobados = resultados.stream().mapToLong(AuditIntegrityResponse::comprobados).sum();
        long soloEnlace = resultados.stream().mapToLong(AuditIntegrityResponse::soloEnlace).sum();
        return resultados.stream().filter(r -> !r.intacta()).findFirst()
                .map(rota -> new AuditIntegrityResponse(false, movimientos, comprobados, soloEnlace,
                        rota.primerFallo(), rota.motivo()))
                .orElseGet(() -> AuditIntegrityResponse.intacta(movimientos, comprobados, soloEnlace));
    }

    /**
     * Los movimientos de una cadena que siguen a uno dado, en orden de
     * escritura.
     *
     * Una consulta por bloque, y cada una en su propia transacción corta (la
     * que declara el repositorio). En el tramo común se intercalan las lápidas
     * de las empresas borradas, cada una en el sitio que tenía su fila: se
     * pide un bloque de cada y se queda con los primeros por id.
     */
    private List<FilaDeLaCadena> siguienteBloque(Cadena cadena, long desdeId) {
        PageRequest bloque = PageRequest.of(0, filasPorBloque);
        if (!cadena.esComun()) {
            return auditRepository.findBloqueDeLaCadenaDeEmpresa(cadena.empresaId(), desdeId, bloque);
        }
        List<FilaDeLaCadena> filas = auditRepository.findBloqueDelTramoComun(desdeId, bloque);
        List<EslabonRetirado> retiradas = lapidas.findByIdGreaterThanOrderByIdAsc(desdeId, bloque);
        if (retiradas.isEmpty()) {
            return filas;
        }
        List<FilaDeLaCadena> juntas = new ArrayList<>(filas);
        retiradas.forEach(l -> juntas.add(FilaDeLaCadena.lapida(l.getId(), l.getHashAnterior(), l.getHash(), l.getEmpresaId())));
        juntas.sort(Comparator.comparingLong(FilaDeLaCadena::id));
        return juntas.size() > filasPorBloque ? juntas.subList(0, filasPorBloque) : juntas;
    }

    /**
     * Anota hasta dónde ha llegado un recorrido que ha salido intacto.
     *
     * <b>Firma la última fila RECORRIDA</b>, no la última de la tabla: si
     * alguien fichaba entre el final del recorrido y esa lectura, el punto de
     * control daba por comprobada una fila que nadie había mirado.
     */
    private void anotarPuntoDeControl(Cadena cadena, Recorrido recorrido, Optional<AuditCheckpoint> ultimoPunto) {
        if (recorrido.ultimoHash() == null) {
            // La cadena está vacía: no hay nada que firmar.
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
                .empresaId(cadena.empresaId())
                .build());
        log.info("Cadena de auditoría {} comprobada hasta la fila {}: {} movimientos, {} recalculados.",
                cadena.esComun() ? "del tramo común" : "de la empresa " + cadena.empresaId(),
                recorrido.ultimoId(), resultado.movimientos(), resultado.comprobados());
    }

    /** Si de esta fila se recalcula el hash (versión 2 en adelante, y que no sea una lápida). */
    private static boolean comprobable(FilaDeLaCadena fila) {
        return !fila.retirada() && fila.versionHash() >= HuellaDeAuditoria.VERSION_VERIFICABLE;
    }

    /** Qué le pasa a esta fila, o null si está bien. */
    private String revisar(FilaDeLaCadena fila, String hashDeLaAnterior) {
        // El enlace: lo que la fila dice que había antes contra lo que había
        // antes de verdad. La primera del tramo común no enlaza con nada; la
        // primera de una empresa, con su génesis.
        if (!Objects.equals(fila.hashAnterior(), hashDeLaAnterior)) {
            return "el enlace con la fila anterior no cuadra: falta una fila, sobra una o se han reordenado";
        }
        if (!comprobable(fila)) {
            return null;
        }
        if (!huella.calcular(fila, hashDeLaAnterior).equals(fila.hash())) {
            return "el contenido de la fila no coincide con su hash: le han cambiado algo";
        }
        return null;
    }
}
