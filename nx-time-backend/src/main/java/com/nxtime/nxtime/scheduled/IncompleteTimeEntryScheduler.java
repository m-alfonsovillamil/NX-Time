package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 * Qué hace al cerrarlas:
 *  - Pone {@code horaSalida} al límite de antigüedad (no a "ahora"): si
 *    la jornada se abrió el lunes y esto corre el miércoles, dar por
 *    buenas 48 horas trabajadas sería peor que no hacer nada.
 *  - Marca {@code jornadaIncompleta = true}: la hora de salida es una
 *    convención del sistema, NO un fichaje real. Queda señalada para
 *    que RRHH la corrija con {@code PATCH /api/v1/fichaje/{id}} (Fase 8).
 *  - Deja traza en la auditoría, con {@code modificadoPor = null}, que
 *    en esa tabla significa exactamente "acción automática del
 *    sistema" (ver V4__business_rules.sql).
 */
@Component
public class IncompleteTimeEntryScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncompleteTimeEntryScheduler.class);

    /**
     * A partir de cuántas horas abierta se considera olvidada. 16 h
     * cubre de sobra cualquier jornada legal (incluidos turnos largos y
     * guardias) sin llegar a las 24, para que una jornada olvidada se
     * detecte esa misma noche y no a los dos días.
     */
    private static final int HORAS_PARA_CONSIDERARLA_OLVIDADA = 16;

    private final TimeEntryRepository timeEntryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TimeEntrySnapshotSerializer snapshotSerializer;

    public IncompleteTimeEntryScheduler(
            TimeEntryRepository timeEntryRepository,
            ApplicationEventPublisher eventPublisher,
            TimeEntrySnapshotSerializer snapshotSerializer) {
        this.timeEntryRepository = timeEntryRepository;
        this.eventPublisher = eventPublisher;
        this.snapshotSerializer = snapshotSerializer;
    }

    /** Todos los días a las 3:00 (hora española), con el sistema en calma. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Madrid")
    @Transactional
    public void cerrarJornadasOlvidadas() {
        Instant limite = Instant.now().minus(HORAS_PARA_CONSIDERARLA_OLVIDADA, ChronoUnit.HOURS);
        List<TimeEntry> olvidadas = timeEntryRepository.findJornadasAbiertasAnterioresA(limite);

        if (olvidadas.isEmpty()) {
            log.debug("Revisión de jornadas incompletas: ninguna pendiente.");
            return;
        }

        for (TimeEntry entrada : olvidadas) {
            String antes = snapshotSerializer.toJson(entrada);

            // La salida se fija al límite, no a Instant.now(): ver el
            // Javadoc de la clase.
            Instant horaSalida = entrada.getHoraEntrada().plus(HORAS_PARA_CONSIDERARLA_OLVIDADA, ChronoUnit.HOURS);

            entrada.setHoraSalida(horaSalida);
            entrada.setJornadaIncompleta(true);
            // Si se quedó "en pausa", también hay que sacarla de ese
            // estado: si no, quedaría cerrada pero marcada en pausa, un
            // estado que no significa nada.
            entrada.setEnPausa(false);
            entrada.setInicioPausaActual(null);
            timeEntryRepository.save(entrada);

            eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                    .registro(entrada)
                    .usuario(entrada.getUsuario())
                    .modificadoPor(null) // acción automática, sin autor humano
                    .accion(AuditAction.MODIFICACION)
                    .valorAnterior(antes)
                    .valorNuevo(snapshotSerializer.toJson(entrada))
                    .motivo("Cierre automático: jornada sin fichaje de salida tras "
                            + HORAS_PARA_CONSIDERARLA_OLVIDADA + " horas.")
                    .build()));
        }

        log.warn("Cerradas automáticamente {} jornadas sin fichaje de salida. "
                + "Requieren corrección manual por RRHH.", olvidadas.size());
    }
}
