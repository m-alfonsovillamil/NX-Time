package com.nxtime.app.data.dto

/**
 * DTO que el backend devuelve tras un login exitoso.
 */
data class RespuestaAutenticacion(
    val token: String,
    val nombre: String,
    val rol: String
)