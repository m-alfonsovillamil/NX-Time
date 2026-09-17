package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** "He olvidado mi contraseña": solo el correo (ver ADR 014). */
public record PasswordRecoveryRequest(

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email
) {

    /**
     * Aquí es donde más duele no normalizar: este endpoint devuelve 202
     * siempre, así que una mayúscula deja a la persona sin código y sin
     * ninguna pista de por qué. Ver {@link Emails}.
     */
    public PasswordRecoveryRequest {
        email = Emails.normalizar(email);
    }
}
