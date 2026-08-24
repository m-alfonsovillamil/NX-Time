package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para recibir la petición de registro de un nuevo Gestor y Empresa.
 */
public record RegisterManagerRequest(

        @NotBlank(message = "El nombre de la empresa es obligatorio.")
        String nombreEmpresa,

        @NotBlank(message = "El nombre del gestor es obligatorio.")
        String nombreGestor,

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres.")
        String password
) {
}
