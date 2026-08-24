package com.nxtime.app.data.dto

/**
 * DTO para recibir la lista de empleados del gestor.
 */
data class EmpleadoSimpleDTO(
    val id: Long,
    val nombre: String,
    val email: String
)