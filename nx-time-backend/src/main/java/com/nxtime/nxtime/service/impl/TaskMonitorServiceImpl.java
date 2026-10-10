package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.domain.ScheduledTaskRun;
import com.nxtime.nxtime.dto.SystemStatusResponse;
import com.nxtime.nxtime.dto.SystemStatusResponse.TaskStatus;
import com.nxtime.nxtime.repository.ScheduledTaskRunRepository;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ver {@link TaskMonitorService}.
 *
 * <b>"¿Ha corrido?" significa "¿terminó bien alguna ejecución desde la
 * última vez que le tocaba?"</b>, y no "en las últimas 26 horas". Un margen
 * fijo no funciona con dos tareas a horas distintas y el cambio de hora:
 * la comprobación diaria corre a la misma hora UTC todo el año, así que en
 * invierno una tarea perdida de las 3:30 seguía dentro de las 26 horas y
 * no se avisaba hasta el día siguiente.
 */
@Service
public class TaskMonitorServiceImpl implements TaskMonitorService {

    /**
     * Cuánto se espera tras la hora programada antes de exigir que la
     * tarea haya terminado. Las dos tardan segundos; el margen evita que
     * una consulta que coincide con la ejecución avise en falso.
     */
    static final Duration GRACIA = Duration.ofMinutes(15);

    /** Lo que cabe en la columna {@code detalle}. */
    static final int MAXIMO_DETALLE = 2000;

    /**
     * Cuánto pueden diferir los relojes de dos instancias, o adelantarse el
     * disparo de una tarea a su hora, sin que el turno se calcule mal.
     *
     * Sin esto, una tarea que se dispare unas milésimas antes de las 3:00
     * según su reloj buscaría en el turno de AYER, encontraría la ejecución de
     * ayer --que tras un despliegue es de otra instancia-- y no correría. Un
     * minuto sobra para relojes sincronizados y queda lejísimos del turno
     * anterior, que es de hace un día.
     */
    static final Duration HOLGURA_DEL_TURNO = Duration.ofMinutes(1);

    /** El primer entero de la clave del cerrojo de los turnos: ver {@code pedirTurno}. */
    static final int ESPACIO_DE_LOS_TURNOS = 8_2019;

    private static final Logger log = LoggerFactory.getLogger(TaskMonitorServiceImpl.class);

    private static final ZoneId ZONA = ZoneId.of(ScheduledTask.ZONA);

    private final ScheduledTaskRunRepository repository;
    private final Clock clock;
    private final TransactionOperations transaccion;

    /** Quién es esta instancia: al azar, y distinto en cada arranque. */
    private final String instancia;

    @Autowired
    public TaskMonitorServiceImpl(ScheduledTaskRunRepository repository, TransactionTemplate transaccion) {
        this(repository, Clock.systemUTC(), transaccion, UUID.randomUUID().toString());
    }

    TaskMonitorServiceImpl(ScheduledTaskRunRepository repository, Clock clock) {
        this(repository, clock, TransactionOperations.withoutTransaction(), UUID.randomUUID().toString());
    }

    TaskMonitorServiceImpl(
            ScheduledTaskRunRepository repository, Clock clock, TransactionOperations transaccion, String instancia) {
        this.repository = repository;
        this.clock = clock;
        this.transaccion = transaccion;
        this.instancia = instancia;
    }

    @Override
    public void ejecutar(ScheduledTask tarea, Supplier<String> trabajo) {
        // Si esto falla (la base caída), la tarea no llega a correr, y está
        // bien: las dos trabajan contra la misma base.
        ScheduledTaskRun ejecucion = transaccion.execute(estado -> empezarSiNoLaHaHechoOtra(tarea));
        if (ejecucion == null) {
            log.info("La tarea {} ya la ha empezado otra instancia en este turno: aquí no se repite.", tarea);
            return;
        }

        String detalle;
        try {
            detalle = trabajo.get();
        } catch (RuntimeException fallo) {
            try {
                terminar(ejecucion, ScheduledTaskResult.ERROR,
                        fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            } catch (RuntimeException alRegistrar) {
                // El fallo que importa es el de la tarea: el del registro
                // viaja dentro de él, en vez de taparlo.
                fallo.addSuppressed(alRegistrar);
            }
            throw fallo;
        }
        terminar(ejecucion, ScheduledTaskResult.OK, detalle);
    }

    /**
     * Apunta que la tarea empieza, salvo que otra instancia la haya empezado
     * ya en este turno; entonces devuelve null (ADR 042).
     *
     * Mirar y apuntar van en una transacción corta y con el turno pedido: sin
     * él, dos instancias que arrancan la tarea en la misma milésima mirarían
     * las dos antes de que ninguna apuntase, y correrían las dos. La tarea en
     * sí va fuera, después de confirmar: el cerrojo dura lo que estas dos
     * consultas, no lo que tarde la tarea.
     *
     * <b>La misma instancia sí puede repetir</b>: no hay camino en producción
     * que lo haga (cada tarea se dispara una vez por turno), y es lo que deja
     * a los tests lanzar una tarea varias veces seguidas.
     *
     * <b>Si la otra instancia se queda a medias, este turno se pierde.</b> No
     * se repite aquí porque no se sabe hasta dónde llegó, y varias tareas
     * mandan correos. Se ve: {@code /estado/tareas} la da por no hecha.
     */
    private ScheduledTaskRun empezarSiNoLaHaHechoOtra(ScheduledTask tarea) {
        repository.pedirTurno(ESPACIO_DE_LOS_TURNOS, tarea.name().hashCode());

        Instant ahora = clock.instant();
        Instant turno = ultimaHoraProgramada(tarea, ahora.plus(HOLGURA_DEL_TURNO));
        if (repository.existsByTareaAndInicioGreaterThanEqualAndInstanciaNot(
                tarea, turno.minus(HOLGURA_DEL_TURNO), instancia)) {
            return null;
        }
        return repository.save(ScheduledTaskRun.builder()
                .tarea(tarea)
                .inicio(ahora)
                .resultado(ScheduledTaskResult.EN_CURSO)
                .instancia(instancia)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScheduledTaskResult> ultimoResultado(ScheduledTask tarea) {
        return repository.findFirstByTareaAndResultadoNotOrderByInicioDesc(tarea, ScheduledTaskResult.EN_CURSO)
                .map(ScheduledTaskRun::getResultado);
    }

    private void terminar(ScheduledTaskRun ejecucion, ScheduledTaskResult resultado, String detalle) {
        ejecucion.setFin(clock.instant());
        ejecucion.setResultado(resultado);
        ejecucion.setDetalle(detalle != null && detalle.length() > MAXIMO_DETALLE
                ? detalle.substring(0, MAXIMO_DETALLE)
                : detalle);
        repository.save(ejecucion);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemStatusResponse estado() {
        Instant ahora = clock.instant();
        List<TaskStatus> tareas = Arrays.stream(ScheduledTask.values())
                .map(tarea -> estadoDe(tarea, ahora))
                .toList();
        return new SystemStatusResponse(tareas.stream().allMatch(TaskStatus::ok), tareas);
    }

    private TaskStatus estadoDe(ScheduledTask tarea, Instant ahora) {
        Instant debioCorrer = ultimaHoraProgramada(tarea, ahora.minus(GRACIA));
        boolean ok = repository.existsByTareaAndResultadoAndInicioGreaterThanEqual(
                tarea, ScheduledTaskResult.OK, debioCorrer);
        Optional<ScheduledTaskRun> ultima = repository.findFirstByTareaOrderByInicioDesc(tarea);
        Optional<ScheduledTaskRun> ultimaCorrecta =
                repository.findFirstByTareaAndResultadoOrderByInicioDesc(tarea, ScheduledTaskResult.OK);

        return new TaskStatus(
                tarea,
                debioCorrer,
                ok,
                ultima.map(ScheduledTaskRun::getInicio).orElse(null),
                ultima.map(ScheduledTaskRun::getResultado).orElse(null),
                ultimaCorrecta.map(ScheduledTaskRun::getInicio).orElse(null));
    }

    /**
     * La última vez que el cron de la tarea se disparó sin pasar de
     * {@code limite}, en hora española.
     *
     * {@link CronExpression} solo sabe calcular la SIGUIENTE, así que se
     * avanza desde dos días antes hasta pasarse. Dos días bastan para
     * cualquier tarea que corra al menos una vez cada 48 horas; las de
     * ahora son diarias.
     */
    static Instant ultimaHoraProgramada(ScheduledTask tarea, Instant limite) {
        CronExpression cron = tarea.cron();
        ZonedDateTime hasta = limite.atZone(ZONA);
        ZonedDateTime candidata = cron.next(hasta.minusDays(2));
        ZonedDateTime ultima = null;
        while (candidata != null && !candidata.isAfter(hasta)) {
            ultima = candidata;
            candidata = cron.next(candidata);
        }
        if (ultima == null) {
            throw new IllegalStateException("La tarea " + tarea + " no se dispara ni una vez cada dos días");
        }
        return ultima.toInstant();
    }
}
