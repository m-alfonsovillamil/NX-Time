package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.MotivoDeSupresion;
import jakarta.validation.constraints.NotNull;

/**
 * Suprimir un texto del expediente (ADR 048): la descripción, si
 * {@code mensajeId} es null, o un mensaje.
 */
public record ComplaintRedactionRequest(
        Long mensajeId,

        @NotNull(message = "Hay que decir por qué se suprime.")
        MotivoDeSupresion motivo) {
}
