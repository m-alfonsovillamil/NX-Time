package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para recibir la petición de cambio de contraseña de un usuario ya autenticado.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "La contraseña antigua es obligatoria.")
        String contrasenaAntigua,

        // Igual que PasswordResetRequest y RegisterManagerRequest: 8 de
        // mínimo en todo el backend, y 72 de máximo porque BCrypt trunca ahí.
        @NotBlank(message = "La contraseña nueva es obligatoria.")
        @Size(min = 8, max = 72, message = "La nueva contraseña debe tener entre 8 y 72 caracteres.")
        String contrasenaNueva
) {
}
