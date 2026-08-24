package com.nxtime.app.data.dto

/**
 * DTO para enviar la petición de crear un nuevo Empleado.
 */
data class CrearEmpleadoRequest(
    val nombre: String,
    val email: String,
    val contrasena: String
)