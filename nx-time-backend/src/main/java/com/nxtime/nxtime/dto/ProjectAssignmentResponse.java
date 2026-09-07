package com.nxtime.nxtime.dto;

import java.time.LocalDate;

/**
 * Una asignación: quién, a qué proyecto y con qué vigencia.
 *
 * Lleva los datos de las dos puntas (persona y proyecto) porque la misma
 * respuesta sirve para dos pantallas que miran desde lados opuestos: la
 * ficha del proyecto ("quién ha pasado por aquí") y el perfil de la
 * persona ("en qué he estado"). Partirlo en dos DTO casi idénticos solo
 * añadiría un mapeador más.
 *
 * {@code fechaFin} null significa que sigue asignado, y {@code vigente}
 * lo resuelve para no obligar al cliente a comparar la fecha de fin con
 * el día de hoy — que además tendría que hacer en la zona correcta.
 */
public record ProjectAssignmentResponse(
        long id,
        long usuarioId,
        String usuario,
        long proyectoId,
        String proyectoCodigo,
        String proyectoNombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        boolean vigente
) {
}
