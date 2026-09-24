package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decidir sobre una incidencia. Aceptar no pide comentario; rechazar sí, y lo
 * comprueba el servicio: es la decisión que alguien querría ver motivada.
 */
public record ResolveIncidentRequest(
        @NotNull(message = "Hay que decir si se acepta o no.")
        Boolean aceptar,

        @Size(max = 1000, message = "El comentario no puede pasar de 1000 caracteres.")
        String comentario
) {
}
