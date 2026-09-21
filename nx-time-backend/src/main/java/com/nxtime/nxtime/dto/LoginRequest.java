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
        String contrasena,

        /**
         * Desde dónde se entra: ANDROID, IOS o WEB (Fase A11).
         *
         * Decide cuánto vive el refresh token, y por eso lo dice el cliente y
         * no se adivina de la cabecera {@code User-Agent}: un navegador dura
         * doce horas y un móvil treinta días (ver
         * {@code RefreshToken.Origen}).
         *
         * <b>Opcional, y con ANDROID por defecto</b>: la app publicada no lo
         * manda y no tiene por qué cambiar de comportamiento por esto. Un
         * cliente que mienta solo se perjudica a sí mismo --nadie consigue más
         * permisos declarando otro origen, solo otra caducidad--, así que no
         * hace falta comprobarlo.
         */
        String origen
) {

    /**
     * Ver {@link Emails}. Sin esto, teclear el correo con una mayúscula da
     * "credenciales incorrectas", que manda a buscar el fallo en la
     * contraseña -- donde no está.
     */
    public LoginRequest {
        email = Emails.normalizar(email);
    }

    /** Lo que mandan los clientes que no declaran de dónde vienen. */
    public LoginRequest(String email, String contrasena) {
        this(email, contrasena, null);
    }
}
