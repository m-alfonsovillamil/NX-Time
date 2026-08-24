package com.nxtime.nxtime.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO para la respuesta del historial de fichajes del equipo.
 *
 * Antes, horaEntrada/horaSalida/fecha viajaban como String
 * preformateado ("HH:mm:ss" / "yyyy-MM-dd"), en vez de fechas
 * ISO-8601 tipadas (ver auditoría, defectos de diseño). Cambio de
 * contrato deliberado de esta fase: requiere actualizar el parseo en
 * la app Android (GestorHistorialAdapter).
 */
public record TeamTimeEntryDTO(
        long id,
        LocalDateTime horaEntrada,
        LocalDateTime horaSalida,
        LocalDate fecha,
        SimpleUserDTO usuario,
        long minutosPausaAcumulados
) {
}
