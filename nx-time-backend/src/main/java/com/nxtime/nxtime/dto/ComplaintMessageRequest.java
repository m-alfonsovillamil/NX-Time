package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Escribir en el expediente de una denuncia (Fase G). */
public record ComplaintMessageRequest(

        @NotBlank(message = "El mensaje no puede estar vacío.")
        @Size(max = 2000, message = "El mensaje no puede pasar de 2000 caracteres.")
        String texto
) {
}
