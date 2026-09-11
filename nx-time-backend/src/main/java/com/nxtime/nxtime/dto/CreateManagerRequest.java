package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para recibir la petición de que un administrador cree un gestor.
 *
 * Sin contraseña, por lo mismo que {@link CreateEmployeeRequest} (ver ADR 014).
 */
public record CreateManagerRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        String nombre,

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email
) {
}
