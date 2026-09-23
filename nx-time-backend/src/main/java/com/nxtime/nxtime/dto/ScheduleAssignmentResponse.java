package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Una asignación de plantilla a una persona.
 *
 * @param aviso solo al crearla, y solo si la plantilla se separa de la jornada
 *   contratada más de la tolerancia. No es un error: un turno rotatorio puede
 *   cuadrar al mes y no a la semana. Se dice para que quien la asigna lo vea
 *   en el momento, y además se avisa a quien lleva los contratos.
 */
public record ScheduleAssignmentResponse(
        long id,
        long usuarioId,
        String usuarioNombre,
        long plantillaId,
        String plantillaNombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        boolean vigente,
        @Schema(nullable = true) String aviso
) {
}
