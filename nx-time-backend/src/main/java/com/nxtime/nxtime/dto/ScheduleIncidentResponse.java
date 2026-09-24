package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ScheduleIncidentStatus;
import com.nxtime.nxtime.domain.ScheduleIncidentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una incidencia de cuadrante (Fase B2).
 *
 * @param horaPrevista la hora teórica contra la que se comparó, ya formateada
 *   ("09:00"). Sin ella, "25 minutos tarde" no dice tarde respecto a qué.
 * @param horaReal la entrada o la salida real; null en una ausencia.
 */
public record ScheduleIncidentResponse(
        long id,
        long usuarioId,
        String usuario,
        LocalDate fecha,
        ScheduleIncidentType tipo,
        int minutos,
        @Schema(example = "09:00") String horaPrevista,
        @Schema(nullable = true) Instant horaReal,
        ScheduleIncidentStatus estado,
        @Schema(nullable = true) String justificacion,
        @Schema(nullable = true) Instant justificadaEn,
        @Schema(nullable = true) String comentarioResolucion,
        @Schema(nullable = true) String resueltaPor,
        @Schema(nullable = true) Instant resueltaEn
) {
}
