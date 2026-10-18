package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ampliar a seis meses el plazo de respuesta (Ley 2/2023, art. 9.2.d; ADR 048).
 * El motivo le llega a quien denunció, como un mensaje del expediente.
 */
public record ComplaintExtensionRequest(
        @NotBlank(message = "Hay que explicar por qué se amplía el plazo.")
        @Size(max = 1000, message = "El motivo no puede pasar de 1000 caracteres.")
        String motivo) {
}
