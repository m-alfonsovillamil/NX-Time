package com.nxtime.nxtime.dto;

/**
 * DTO para la respuesta del historial de fichajes del equipo.
 */
public record TeamTimeEntryDTO(
        long id,
        String horaEntrada,
        String horaSalida,
        String fecha,
        SimpleUserDTO usuario,
        long minutosPausaAcumulados
) {
}
