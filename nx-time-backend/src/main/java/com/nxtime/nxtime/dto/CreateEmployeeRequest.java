package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para recibir la petición de crear un nuevo Empleado.
 *
 * Sin contraseña desde el 09/2026 (ver ADR 014): antes la tecleaba quien
 * daba el alta, así que el jefe conocía la contraseña de todos. Ahora la
 * elige el propio empleado con el código que le llega por correo.
 */
public record CreateEmployeeRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        String nombre,

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email
) {
}
