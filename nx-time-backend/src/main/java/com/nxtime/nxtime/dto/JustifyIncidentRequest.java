package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Explicar qué pasó un día que no cuadró con el cuadrante. */
public record JustifyIncidentRequest(
        @NotBlank(message = "Hay que escribir la explicación.")
        @Size(max = 1000, message = "La explicación no puede pasar de 1000 caracteres.")
        String texto
) {
}
