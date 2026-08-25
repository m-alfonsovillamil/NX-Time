package com.nxtime.nxtime.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO para la respuesta del historial de fichajes del equipo.
 *
 * horaEntrada/horaSalida son Instant desde la Fase 3 (antes
 * LocalDateTime; y antes de eso, String preformateado -- ver
 * auditoría, defectos de diseño). "fecha" se deriva del Instant en la
 * zona Europe/Madrid (ver TimeEntryMapper), no en UTC: el día de
 * calendario en que empezó la jornada es el que importa para agrupar
 * el historial, no el día UTC.
 */
public record TeamTimeEntryDTO(
        long id,
        Instant horaEntrada,
        Instant horaSalida,
        LocalDate fecha,
        SimpleUserDTO usuario,
        long minutosPausaAcumulados
) {
}
