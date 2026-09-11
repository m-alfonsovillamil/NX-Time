package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Elegir contraseña con un código de acceso, sea el de alta o el de
 * recuperación (ver ADR 014).
 */
public record PasswordResetRequest(

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email,

        // Con espacios alrededor también vale: al pegarlo desde el correo
        // se arrastran con facilidad.
        @NotBlank(message = "El código es obligatorio.")
        @Pattern(regexp = "\\s*\\d{6}\\s*", message = "El código son 6 dígitos.")
        String codigo,

        // 72 es lo máximo que BCrypt tiene en cuenta: lo que pasara de ahí
        // se ignoraría en silencio, y dos contraseñas distintas valdrían igual.
        @NotBlank(message = "La contraseña nueva es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String contrasenaNueva
) {
}
