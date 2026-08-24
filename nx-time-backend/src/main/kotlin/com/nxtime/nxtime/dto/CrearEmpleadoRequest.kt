package com.nxtime.nxtime.dto

/**
 * DTO para recibir la petición de crear un nuevo Empleado.
 */
data class CrearEmpleadoRequest(
    val nombre: String,
    val email: String,
    val contrasena: String
)