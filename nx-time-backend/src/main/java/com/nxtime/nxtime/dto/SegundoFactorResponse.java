package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Cómo tiene una persona su segundo factor (ADR 051).
 *
 * @param motivo por qué se le exige, en una frase para enseñar; null si no se
 *   le exige
 */
@Schema(description = "El segundo factor de quien pregunta")
public record SegundoFactorResponse(
        @Schema(description = "Si se le pide un código al entrar con contraseña") boolean activo,
        @Schema(description = "Desde cuándo, si está activo") Instant desde,
        @Schema(description = "Si se le exige: entonces no puede desactivarlo") boolean obligatorio,
        @Schema(description = "Por qué se le exige") String motivo,
        @Schema(description = "Códigos de recuperación sin usar") int codigosDeRecuperacion) {
}
