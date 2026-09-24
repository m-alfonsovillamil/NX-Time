package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.service.MonthlySignatureService;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recuerda firmar el mes anterior (Fase B3).
 *
 * <b>A diario a las 9:00</b>, no solo el día 1: si ese día no corre, el
 * siguiente se pone al día, y el servicio ya evita mandar dos recordatorios el
 * mismo mes a la misma persona. Ver {@link ScheduledTask#CRON_RECORDATORIO_FIRMA}.
 */
@Component
public class SignatureReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SignatureReminderScheduler.class);

    private static final ZoneId MADRID = ZoneId.of(ScheduledTask.ZONA);

    private final MonthlySignatureService signatureService;
    private final TaskMonitorService taskMonitor;

    public SignatureReminderScheduler(MonthlySignatureService signatureService, TaskMonitorService taskMonitor) {
        this.signatureService = signatureService;
        this.taskMonitor = taskMonitor;
    }

    @Scheduled(cron = ScheduledTask.CRON_RECORDATORIO_FIRMA, zone = ScheduledTask.ZONA)
    public void recordarFirmas() {
        taskMonitor.ejecutar(ScheduledTask.RECORDATORIO_FIRMA, () -> {
            LocalDate hoy = LocalDate.now(MADRID);
            int avisadas = signatureService.recordar(hoy);
            YearMonth mes = YearMonth.from(hoy).minusMonths(1);
            log.info("Recordatorio de firma de {}: {} personas.", mes, avisadas);
            return "Recordado firmar " + mes + " a " + avisadas + " personas.";
        });
    }
}
