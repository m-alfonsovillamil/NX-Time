package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Añadir una pausa que no se fichó en su momento (ADR 015).
 *
 * El motivo es obligatorio siempre, también cuando se aplica en el acto:
 * no hay tope de duración, y lo que hace defendible eso es que cada pausa
 * añadida lleva escrito por qué.
 */
public record AddPauseRequest(

        @NotNull(message = "Indica cuándo empezó la pausa.")
        Instant inicio,

        @NotNull(message = "Indica cuándo acabó la pausa.")
        Instant fin,

        @NotBlank(message = "Explica por qué añades la pausa.")
        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres.")
        String motivo
) {
}
