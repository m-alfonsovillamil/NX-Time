package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Positive;

/** Cambiar de proyecto durante la jornada abierta (ADR 017). */
public record ChangeProjectRequest(
        @Positive(message = "Hay que indicar a qué proyecto cambias.")
        long proyectoId
) {
}
