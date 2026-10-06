package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.service.DataDeletionService;
import com.nxtime.nxtime.service.TaskMonitorService;
import com.nxtime.nxtime.service.impl.UnconfirmedRegistrationCleaner;
import java.time.Instant;
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
 *
 * <p>En la misma pasada se borran los registros de empresa que nadie confirmó
 * en dos días ({@link UnconfirmedRegistrationCleaner}). Va aquí y no en una
 * tarea propia a propósito: cada tarea nueva es otra hora a la que se despierta
 * la base, y esta ya es la limpieza de la noche.
 */
@Component
public class DataDeletionScheduler {

    private static final ZoneId MADRID = ZoneId.of(ScheduledTask.ZONA);

    private final DataDeletionService dataDeletionService;
    private final TaskMonitorService taskMonitor;
    private final UnconfirmedRegistrationCleaner registrosSinConfirmar;

    public DataDeletionScheduler(DataDeletionService dataDeletionService, TaskMonitorService taskMonitor,
            UnconfirmedRegistrationCleaner registrosSinConfirmar) {
        this.dataDeletionService = dataDeletionService;
        this.taskMonitor = taskMonitor;
        this.registrosSinConfirmar = registrosSinConfirmar;
    }

    @Scheduled(cron = ScheduledTask.CRON_ANONIMIZACION, zone = ScheduledTask.ZONA)
    public void anonimizar() {
        taskMonitor.ejecutar(ScheduledTask.ANONIMIZACION, () -> {
            int anonimizadas = dataDeletionService.anonimizarVencidas(LocalDate.now(MADRID));
            int sinConfirmar = registrosSinConfirmar.borrarCaducados(Instant.now());
            String resumen = anonimizadas == 1
                    ? "1 persona anonimizada."
                    : anonimizadas + " personas anonimizadas.";
            return sinConfirmar == 0
                    ? resumen
                    : resumen + " Registros de empresa sin confirmar borrados: " + sinConfirmar + ".";
        });
    }
}
