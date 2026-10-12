package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registrar una empresa desde la app con una cuenta de Google o de Microsoft
 * (ADR 044). Lo mismo que {@link SsoSignupRequest}, más el pase: la app no
 * tiene la cookie que en la web dice quién es.
 *
 * @param pase el que devolvió {@code POST /auth/sso/registro/canjear}
 */
public record SsoAppSignupRequest(
        @NotBlank(message = "Falta el pase del registro.")
        @Size(max = 2000, message = "El pase no es válido.")
        String pase,

        @NotBlank(message = "El nombre de la empresa es obligatorio.")
        @Size(max = 200, message = "El nombre de la empresa no puede pasar de 200 caracteres.")
        String nombreEmpresa,

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.")
        String nombre,

        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 150, message = "Los apellidos no pueden pasar de 150 caracteres.")
        String apellidos
) {
}
