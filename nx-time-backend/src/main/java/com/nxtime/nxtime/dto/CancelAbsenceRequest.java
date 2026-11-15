package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/ausencias/{id}/cancelar} (ADR 058).
 *
 * @param motivo opcional para quien retira la suya; obligatorio para quien
 *               anula la de otra persona, que tiene que saber por qué.
 */
public record CancelAbsenceRequest(
        @Schema(description = "Por qué se cancela. Obligatorio al anular la ausencia de otra persona.")
        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres.")
        String motivo
) {
}
