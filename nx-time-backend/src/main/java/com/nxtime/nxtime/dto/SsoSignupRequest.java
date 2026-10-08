package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que falta para registrar una empresa cuando quién eres ya lo ha dicho
 * Google o Microsoft (ADR 038): ni correo ni contraseña, que es justo lo que
 * ahorra. El correo es el de esa cuenta; el origen es siempre la web.
 */
public record SsoSignupRequest(

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
