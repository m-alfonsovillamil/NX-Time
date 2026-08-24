package com.nxtime.app.data.dto

/**
 * DTO para enviar la petición de cambio de contraseña de un usuario ya autenticado.
 */
data class CambiarContrasenaRequest(
    val contrasenaAntigua: String,
    val contrasenaNueva: String
)