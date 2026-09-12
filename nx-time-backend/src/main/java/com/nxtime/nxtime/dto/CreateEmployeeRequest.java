package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para recibir la petición de crear un nuevo Empleado.
 *
 * Sin contraseña desde el 09/2026 (ver ADR 014): antes la tecleaba quien
 * daba el alta, así que el jefe conocía la contraseña de todos. Ahora la
 * elige el propio empleado con el código que le llega por correo.
 *
 * Nombre y apellidos van <b>separados desde el alta</b>: la ficha los
 * guarda en dos columnas, y pedir "nombre completo" obligaba a la persona
 * a repartirlos después desde su perfil (o a quedarse con el nombre y los
 * apellidos juntos en el campo de nombre, que es lo que pasó de verdad).
 */
public record CreateEmployeeRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.")
        String nombre,

        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 150, message = "Los apellidos no pueden pasar de 150 caracteres.")
        String apellidos,

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email
) {
}
