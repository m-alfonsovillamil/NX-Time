package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para recibir la petición de cambio de contraseña de un usuario ya autenticado.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "La contraseña antigua es obligatoria.")
        String contrasenaAntigua,

        @NotBlank(message = "La contraseña nueva es obligatoria.")
        @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres.")
        String contrasenaNueva
) {
}
