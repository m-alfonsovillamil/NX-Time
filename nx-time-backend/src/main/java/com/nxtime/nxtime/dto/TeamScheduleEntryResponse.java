package com.nxtime.nxtime.dto;

/** El horario teórico de una persona del equipo un día concreto. */
public record TeamScheduleEntryResponse(
        long usuarioId,
        String nombre,
        TheoreticalDayResponse dia
) {
}
