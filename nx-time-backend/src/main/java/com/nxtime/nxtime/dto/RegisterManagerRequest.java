package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para recibir la petición de registro de un nuevo Gestor y Empresa.
 *
 * Dos renombrados del 09/2026, y los dos por coherencia con el resto de la
 * API: {@code nombreGestor} se parte en {@code nombre} + {@code apellidos}
 * —como en las altas y en el perfil— y {@code password} pasa a
 * {@code contrasena}, que era el único sitio del backend donde el campo
 * seguía en inglés.
 */
public record RegisterManagerRequest(

        @NotBlank(message = "El nombre de la empresa es obligatorio.")
        String nombreEmpresa,

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.")
        String nombre,

        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 150, message = "Los apellidos no pueden pasar de 150 caracteres.")
        String apellidos,

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email no tiene un formato válido.")
        String email,

        // El mismo mínimo y el mismo máximo que PasswordResetRequest: 8 por
        // política, y 72 porque BCrypt trunca ahí -- sin el máximo, dos
        // contraseñas largas que coincidan en los primeros 72 bytes son la
        // misma para el sistema.
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String contrasena
) {
}
