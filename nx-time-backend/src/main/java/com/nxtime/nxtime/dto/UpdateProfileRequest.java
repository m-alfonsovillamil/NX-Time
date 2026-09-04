package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Cuerpo de {@code PATCH /api/v1/perfil}: lo que una persona puede
 * cambiar de sí misma.
 *
 * <b>No lleva ni rol, ni empresa, ni jornada, ni días de vacaciones</b>,
 * y eso es la mitad del diseño: si estuvieran aquí, cualquiera podría
 * ascenderse o regalarse vacaciones con un PATCH sobre su propio perfil.
 * Esos campos se cambian desde {@code /api/v1/gestor/empleados/{id}/...},
 * detrás de sus authorities.
 *
 * El departamento tampoco: quién pertenece a qué departamento es una
 * decisión de organización, no un dato personal.
 *
 * Como todo PATCH del proyecto, null significa "no tocar". Cadena vacía
 * sí es un cambio: es cómo se borra un puesto que ya no aplica.
 *
 * @param fechaNacimiento {@code @Past} y no un CHECK en la base:
 *   PostgreSQL exige funciones IMMUTABLE en un CHECK y CURRENT_DATE no
 *   lo es, así que la restricción ni siquiera se dejaría crear.
 */
public record UpdateProfileRequest(

        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.")
        String nombre,

        @Size(max = 150, message = "Los apellidos no pueden pasar de 150 caracteres.")
        String apellidos,

        @Past(message = "La fecha de nacimiento tiene que ser anterior a hoy.")
        LocalDate fechaNacimiento,

        @Size(max = 120, message = "El puesto no puede pasar de 120 caracteres.")
        String puesto
) {
}
