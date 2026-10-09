package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.service.impl.IncompleteTimeEntryCloser;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * tiene que envolver la transacción ENTERA. Con {@code @Transactional}
     * en este método, el commit ocurriría al salir de él -- después de
     * haber escrito "OK" -- y un fallo al confirmar dejaría registrado que
     * la tarea salió bien.
     */
    @Scheduled(cron = ScheduledTask.CRON_CIERRE_JORNADAS, zone = ScheduledTask.ZONA)
    public void cerrarJornadasOlvidadas() {
        taskMonitor.ejecutar(ScheduledTask.CIERRE_JORNADAS, () -> {
            Integer cerradas = transaccion.execute(estado -> cerrarEnUnaTransaccion());
            return cerradas == null || cerradas == 0
                    ? "Ninguna jornada pendiente."
                    : "Cerradas " + cerradas + " jornadas sin fichaje de salida.";
        });
    }

    private int cerrarEnUnaTransaccion() {
        List<TimeEntry> olvidadas = timeEntryRepository.findJornadasAbiertasAnterioresA(
                IncompleteTimeEntryCloser.limite(Instant.now()));

        if (olvidadas.isEmpty()) {
            log.debug("Revisión de jornadas incompletas: ninguna pendiente.");
            return 0;
        }

        olvidadas.forEach(cierre::cerrar);

        log.warn("Cerradas automáticamente {} jornadas sin fichaje de salida. "
                + "Requieren corrección manual por RRHH.", olvidadas.size());
        return olvidadas.size();
    }
}
