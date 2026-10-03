package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Confirmar el correo con el código que llegó al registrar la empresa (V37,
 * ADR 034). Si vale, abre la sesión como el login.
 */
public record ConfirmRegistrationRequest(

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email,

        // Con espacios alrededor también vale: al pegarlo desde el correo
        // se arrastran con facilidad.
        @NotBlank(message = "El código es obligatorio.")
        @Pattern(regexp = "\\s*\\d{6}\\s*", message = "El código son 6 dígitos.")
        String codigo,

        /** ANDROID, IOS o WEB, como en el login: decide la sesión que se abre. */
        String origen
) {

    /** Ver {@link Emails}: el correo se busca en minúsculas, siempre. */
    public ConfirmRegistrationRequest {
        email = Emails.normalizar(email);
    }
}
