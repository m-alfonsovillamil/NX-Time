package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ScheduleExceptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Una fila de excepción: un LIBRE, o uno de los tramos de un día distinto. */
public record ScheduleExceptionResponse(
        long id,
        long usuarioId,
        LocalDate fecha,
        ScheduleExceptionType tipo,
        @Schema(nullable = true) Integer inicio,
        @Schema(nullable = true) Integer fin,
        @Schema(nullable = true, example = "08:00") String horaInicio,
        @Schema(nullable = true, example = "14:00") String horaFin,
        @Schema(nullable = true) String motivo
) {
}
