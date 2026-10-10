package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.service.impl.IncompleteTimeEntryCloser;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cierra automáticamente las jornadas que nadie cerró (Fase 9).
 *
 * El problema que resuelve no es cosmético: el índice parcial único
 * {@code uq_registros_jornada_abierta} (Fase 3) impide que un empleado
 * tenga dos jornadas abiertas a la vez, así que una jornada que se
 * quedó sin fichar la salida **bloqueaba todos sus fichajes futuros,
 * para siempre**. Quien se fuera un viernes sin fichar la salida no
 * podía volver a fichar el lunes.
 *
 * Cómo se cierra cada una (la salida en el tope, la marca de incompleta y
 * la traza en la auditoría) está en {@link IncompleteTimeEntryCloser}, que es
 * también lo que aplica el fichaje cuando alguien se encuentra con una.
 */
@Component
public class IncompleteTimeEntryScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncompleteTimeEntryScheduler.class);

    /** Cuántos ids de jornadas sin cerrar caben en el resumen de la tarea. */
    private static final int FALLIDAS_EN_EL_RESUMEN = 20;

    private final TimeEntryRepository timeEntryRepository;
    private final IncompleteTimeEntryCloser cierre;
    private final TaskMonitorService taskMonitor;
    private final TransactionTemplate transaccion;

    public IncompleteTimeEntryScheduler(
            TimeEntryRepository timeEntryRepository,
            IncompleteTimeEntryCloser cierre,
            TaskMonitorService taskMonitor,
            TransactionTemplate transaccion) {
        this.timeEntryRepository = timeEntryRepository;
        this.cierre = cierre;
        this.taskMonitor = taskMonitor;
        this.transaccion = transaccion;
    }

    /**
     * Todos los días a las 3:00 (hora española), con el sistema en calma.
     *
     * La transacción va con {@link TransactionTemplate} y no con
     * {@code @Transactional} desde el paso 5 del piloto, y no es cuestión
     * de estilo: {@link TaskMonitorService} registra cómo acabó la tarea y
     * tiene que envolver las transacciones ENTERAS. Con {@code @Transactional}
     * en este método, el commit ocurriría al salir de él -- después de
     * haber escrito "OK" -- y un fallo al confirmar dejaría registrado que
     * la tarea salió bien.
     *
     * <b>Cada jornada, en su transacción</b> (ADR 042). Hasta octubre de 2026
     * iban todas en una, y eso tenía dos problemas:
     * <ul>
     *   <li>una sola jornada que no se pudiera cerrar deshacía el cierre de
     *       todas las demás, esa noche y todas las siguientes, hasta que
     *       alguien la arreglase a mano;</li>
     *   <li>cada cierre escribe en la traza de auditoría, que se encadena con
     *       un cerrojo que dura hasta que la transacción confirma: con todas
     *       en una, las escrituras de todas iban seguidas con el cerrojo
     *       cogido, y quien fichase a esa hora esperaba a la última.</li>
     * </ul>
     *
     * Ahora se cierran las que se puedan y, si alguna no, la tarea acaba en
     * ERROR diciendo cuáles: así sale en {@code /estado/tareas} sin que las
     * demás se queden sin cerrar.
     */
    @Scheduled(cron = ScheduledTask.CRON_CIERRE_JORNADAS, zone = ScheduledTask.ZONA)
    public void cerrarJornadasOlvidadas() {
        taskMonitor.ejecutar(ScheduledTask.CIERRE_JORNADAS, this::cerrarUnaAUna);
    }

    private String cerrarUnaAUna() {
        Instant ahora = Instant.now();
        List<Long> olvidadas = timeEntryRepository.findIdsDeJornadasAbiertasAnterioresA(
                IncompleteTimeEntryCloser.limite(ahora));

        if (olvidadas.isEmpty()) {
            log.debug("Revisión de jornadas incompletas: ninguna pendiente.");
            return "Ninguna jornada pendiente.";
        }

        int cerradas = 0;
        List<Long> fallidas = new ArrayList<>();
        RuntimeException primerFallo = null;
        for (Long id : olvidadas) {
            try {
                Boolean cerrada = transaccion.execute(estado -> cerrarSiSigueOlvidada(id, ahora));
                if (Boolean.TRUE.equals(cerrada)) {
                    cerradas++;
                }
            } catch (OptimisticLockingFailureException alguienSeAdelanto) {
                // Su dueña ha fichado justo ahora, y el fichaje ya la cierra
                // igual que esto (ver IncompleteTimeEntryCloser): no es un fallo.
                log.info("La jornada {} la ha cerrado otro mientras se cerraba aquí.", id);
            } catch (RuntimeException fallo) {
                log.error("No se pudo cerrar la jornada {}; se sigue con las demás.", id, fallo);
                fallidas.add(id);
                if (primerFallo == null) {
                    primerFallo = fallo;
                }
            }
        }

        if (cerradas > 0) {
            log.warn("Cerradas automáticamente {} jornadas sin fichaje de salida. "
                    + "Requieren corrección manual por RRHH.", cerradas);
        }
        if (primerFallo != null) {
            // En ERROR a propósito, aunque las demás estén cerradas: una
            // jornada que no se deja cerrar hay que mirarla, y una tarea en
            // verde no la mira nadie.
            throw new IllegalStateException("Cerradas " + cerradas + " jornadas, pero " + fallidas.size()
                    + " no se pudieron cerrar (" + resumenDe(fallidas) + "). La primera: "
                    + primerFallo.getClass().getSimpleName() + ": " + primerFallo.getMessage(), primerFallo);
        }
        return cerradas == 0
                ? "Ninguna jornada pendiente."
                : "Cerradas " + cerradas + " jornadas sin fichaje de salida.";
    }

    /**
     * Se vuelve a cargar y a mirar dentro de su transacción: entre que se
     * hizo la lista y le llega el turno, su dueña ha podido fichar, y el
     * fichaje ya la habría cerrado.
     */
    private boolean cerrarSiSigueOlvidada(long id, Instant ahora) {
        TimeEntry entrada = timeEntryRepository.findById(id).orElse(null);
        if (entrada == null || entrada.isAnulado() || !IncompleteTimeEntryCloser.estaOlvidada(entrada, ahora)) {
            return false;
        }
        cierre.cerrar(entrada);
        return true;
    }

    private static String resumenDe(List<Long> fallidas) {
        String ids = fallidas.stream().limit(FALLIDAS_EN_EL_RESUMEN).map(String::valueOf)
                .reduce((a, b) -> a + ", " + b).orElse("");
        return fallidas.size() > FALLIDAS_EN_EL_RESUMEN ? "ids " + ids + "…" : "ids " + ids;
    }
}
