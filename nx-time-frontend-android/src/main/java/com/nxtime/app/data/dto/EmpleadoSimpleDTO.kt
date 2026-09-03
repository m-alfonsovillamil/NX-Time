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
    val activo: Boolean = true
)