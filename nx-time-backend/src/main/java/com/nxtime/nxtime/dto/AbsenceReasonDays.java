package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Los días perdidos con un motivo concreto.
 *
 * @param motivo el tipo de ausencia (MEDICO, ASUNTOS_PROPIOS...) o
 *   INCIDENCIA_ACEPTADA: una ausencia de cuadrante cuya explicación se aceptó.
 * @param etiqueta lo mismo, escrito para leerse.
 */
public record AbsenceReasonDays(
        @Schema(example = "MEDICO") String motivo,
        @Schema(example = "Consulta médica") String etiqueta,
        int dias
) {
}
