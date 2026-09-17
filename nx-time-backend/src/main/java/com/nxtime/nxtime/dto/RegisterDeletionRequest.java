package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * RRHH o ADMIN registra una solicitud de borrado recibida fuera de la
 * aplicación: por correo, por carta, en persona (ADR 016).
 *
 * @param motivo cómo llegó y cuándo ("Correo del 12/09 a rrhh@..."). Aquí sí
 *     es obligatorio: es la constancia de que la persona lo pidió, porque no
 *     lo pidió desde su cuenta.
 */
public record RegisterDeletionRequest(
        @Positive(message = "Hay que indicar de quién es la solicitud.")
        long usuarioId,
        @NotBlank(message = "Hay que indicar cómo llegó la solicitud.")
        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres.")
        String motivo
) {
}
