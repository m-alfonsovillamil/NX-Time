package com.nxtime.app.data.dto

/**
 * DTO para recibir la lista de empleados del gestor.
 */
data class EmpleadoSimpleDTO(
    val id: Long,
    val nombre: String,
    val email: String,

    /**
     * Si la cuenta sigue de alta. La lista trae activos e inactivos --
     * dar de baja no borra al empleado -- y este campo es el que permite
     * pintar el interruptor en la posición correcta.
     *
     * Por defecto `true` para no romper si el backend fuera anterior a
     * este campo: un empleado que aparece en la lista del gestor lo
     * normal es que esté de alta.
     */
    val activo: Boolean = true,

    /**
     * Jornada contractual semanal, en horas. Cadena y no `Double`
     * porque el backend la manda desde un `NUMERIC(4,1)` y aquí solo se
     * enseña y se reenvía tal cual (37.5 h es una jornada normal).
     */
    val horasSemanales: String = "40.0",

    /**
     * Días de vacaciones EFECTIVOS del año en curso: los suyos si
     * alguien se los ha fijado, y si no el mínimo legal de 22 que
     * aplica el backend por defecto.
     */
    val diasVacaciones: Int = 22
)