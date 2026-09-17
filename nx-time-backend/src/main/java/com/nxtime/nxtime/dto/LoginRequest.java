package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * La app envía esto al backend al pulsar el botón de login.
 */
public record LoginRequest(

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        String contrasena
) {

    /**
     * Ver {@link Emails}. Sin esto, teclear el correo con una mayúscula da
     * "credenciales incorrectas", que manda a buscar el fallo en la
     * contraseña -- donde no está.
     */
    public LoginRequest {
        email = Emails.normalizar(email);
    }
}
