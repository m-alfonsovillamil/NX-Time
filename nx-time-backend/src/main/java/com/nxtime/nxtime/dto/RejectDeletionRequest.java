package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rechazar una solicitud de borrado. El comentario es obligatorio: quien
 * pidió el borrado tiene derecho a saber por qué no se atiende, y sin eso
 * no puede ni reclamar ni resolver lo que lo impide.
 */
public record RejectDeletionRequest(
        @NotBlank(message = "Hay que explicar por qué no se ejecuta el borrado.")
        @Size(max = 500, message = "El comentario no puede pasar de 500 caracteres.")
        String comentario
) {
}
