package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.service.ScheduleIncidentService;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Compara cada noche lo fichado con el cuadrante (Fase B2).
 *
 * <b>A las 3:40</b>, después del cierre de jornadas olvidadas de las 3:00: una
 * jornada que cerró el sistema lleva una hora de salida que no es real, y la
 * regla de salida anticipada necesita saberlo para no acusar a nadie con ella.
 *
 * <b>Catorce días hacia atrás, no solo ayer</b>, por lo mismo que las horas
 * extra: las correcciones son rutina, y cuando el jueves se aprueba la del
 * lunes, el lunes cambia. Con una ventana, la incidencia que la corrección
 * deja sin sentido se retira sola, y la que aparece se crea. Pasar varias
 * veces por el mismo día es seguro: {@code uq_incidencias_usuario_fecha_tipo}
 * y el propio barrido lo hacen idempotente.
 */
@Component
public class ScheduleIncidentScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScheduleIncidentScheduler.class);

    private static final int DIAS_HACIA_ATRAS = 14;

    private static final ZoneId MADRID = ZoneId.of(ScheduledTask.ZONA);

    private final ScheduleIncidentService incidentService;
    private final TaskMonitorService taskMonitor;

    public ScheduleIncidentScheduler(ScheduleIncidentService incidentService, TaskMonitorService taskMonitor) {
        this.incidentService = incidentService;
        this.taskMonitor = taskMonitor;
    }

    /** El fallo sube, como en OvertimeScheduler: TaskMonitorService lo anota como ERROR. */
    @Scheduled(cron = ScheduledTask.CRON_CUMPLIMIENTO_CUADRANTE, zone = ScheduledTask.ZONA)
    public void detectarIncidencias() {
        taskMonitor.ejecutar(ScheduledTask.CUMPLIMIENTO_CUADRANTE, () -> {
            LocalDate hoy = LocalDate.now(MADRID);
            // Hasta AYER: hoy está a medias.
            LocalDate hasta = hoy.minusDays(1);
            LocalDate desde = hoy.minusDays(DIAS_HACIA_ATRAS);

            int nuevas = incidentService.detectar(desde, hasta);
            log.info("Incidencias de cuadrante {} .. {}: {} nuevas.", desde, hasta, nuevas);
            return "Revisados del " + desde + " al " + hasta + ": " + nuevas + " incidencias nuevas.";
        });
    }
}
