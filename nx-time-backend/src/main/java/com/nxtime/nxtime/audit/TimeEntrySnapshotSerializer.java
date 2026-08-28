package com.nxtime.nxtime.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.TimeEntry;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Serializa un {@link TimeEntry} a la instantánea JSON que guardan
 * {@code valor_anterior}/{@code valor_nuevo} en la auditoría.
 *
 * Vive aquí, y no dentro de un servicio, porque desde la Fase 9 lo
 * necesitan dos sitios distintos -- {@code TimeEntryServiceImpl} (al
 * fichar y al corregir) y {@code IncompleteTimeEntryScheduler} (al
 * cerrar jornadas olvidadas) -- y las dos instantáneas tienen que tener
 * exactamente la misma forma: una línea temporal de auditoría en la que
 * cada entrada describe el fichaje "a su manera" no sirve para comparar
 * nada.
 *
 * No se serializa la entidad directamente: arrastraría el usuario, la
 * empresa y las relaciones perezosas de JPA. El record de abajo fija
 * exactamente qué campos entran.
 */
@Component
public class TimeEntrySnapshotSerializer {

    private final ObjectMapper objectMapper;

    public TimeEntrySnapshotSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(TimeEntry entry) {
        try {
            return objectMapper.writeValueAsString(new TimeEntrySnapshot(
                    entry.getId(),
                    entry.getHoraEntrada(),
                    entry.getHoraSalida(),
                    entry.isEnPausa(),
                    entry.getInicioPausaActual(),
                    entry.getSegundosPausaAcumulados(),
                    entry.isAnulado(),
                    entry.isJornadaIncompleta()));
        } catch (JsonProcessingException e) {
            // TimeEntrySnapshot es un record de tipos simples (long,
            // Instant, boolean): no hay forma realista de que esto falle.
            throw new IllegalStateException("No se pudo serializar el fichaje para la auditoría", e);
        }
    }

    private record TimeEntrySnapshot(
            long id,
            Instant horaEntrada,
            Instant horaSalida,
            boolean enPausa,
            Instant inicioPausaActual,
            long segundosPausaAcumulados,
            boolean anulado,
            boolean jornadaIncompleta
    ) {
    }
}
