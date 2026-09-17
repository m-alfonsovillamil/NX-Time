package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.service.DataDeletionService;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Anonimiza cada noche a quien pidió el borrado de sus datos y ya ha cumplido
 * los cuatro años de conservación del registro horario (ADR 016).
 *
 * Busca "vencidas en o antes de hoy", no "vencidas hoy": si una noche la tarea
 * falla o el servidor está parado, la siguiente recoge lo pendiente. Y es
 * seguro repetir: una solicitud anonimizada lleva {@code anonimizada_en} y ya
 * no vuelve a salir.
 */
@Component
public class DataDeletionScheduler {

    private static final ZoneId MADRID = ZoneId.of(ScheduledTask.ZONA);

    private final DataDeletionService dataDeletionService;
    private final TaskMonitorService taskMonitor;

    public DataDeletionScheduler(DataDeletionService dataDeletionService, TaskMonitorService taskMonitor) {
        this.dataDeletionService = dataDeletionService;
        this.taskMonitor = taskMonitor;
    }

    @Scheduled(cron = ScheduledTask.CRON_ANONIMIZACION, zone = ScheduledTask.ZONA)
    public void anonimizar() {
        taskMonitor.ejecutar(ScheduledTask.ANONIMIZACION, () -> {
            int anonimizadas = dataDeletionService.anonimizarVencidas(LocalDate.now(MADRID));
            return anonimizadas == 1
                    ? "1 persona anonimizada."
                    : anonimizadas + " personas anonimizadas.";
        });
    }
}
