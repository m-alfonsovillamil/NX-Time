package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cómo está la firma de un mes para una persona de la empresa (Fase B3).
 *
 * @param estado SIN_FIRMAR, VIGENTE o INVALIDADA (esta, si la última se cayó
 *   y no se ha vuelto a firmar).
 */
public record TeamSignatureResponse(
        long usuarioId,
        String usuario,
        String estado,
        @Schema(nullable = true) MonthlySignatureResponse firma
) {
}
