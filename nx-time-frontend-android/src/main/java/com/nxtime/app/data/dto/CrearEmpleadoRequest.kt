package com.nxtime.app.data.dto

/**
 * DTO para enviar la petición de crear un nuevo Empleado.
 *
 * Sin contraseña (ADR 014): la elige el propio empleado con el código que
 * le llega por correo.
 */
data class CrearEmpleadoRequest(
    val nombre: String,
    val apellidos: String,
    val email: String
)
