package com.nxtime.nxtime.dto

/**
 * DTO para recibir la petición de cambio de contraseña de un usuario ya autenticado.
 */
data class CambiarContrasenaRequest(
    val contrasenaAntigua: String,
    val contrasenaNueva: String
)