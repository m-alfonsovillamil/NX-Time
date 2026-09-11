package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** "He olvidado mi contraseña": solo el correo (ver ADR 014). */
public record PasswordRecoveryRequest(

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email
) {
}
