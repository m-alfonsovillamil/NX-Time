package com.nxtime.app.data.dto

/**
 * Códigos de acceso (ADR 014): elegir contraseña con un código que llega
 * por correo, sea para recuperarla o para entrar por primera vez.
 */

/** "Enviarme un código". */
data class SolicitarCodigoRequest(
    val email: String
)

/** El código de 6 dígitos y la contraseña elegida. */
data class RestablecerContrasenaRequest(
    val email: String,
    val codigo: String,
    val contrasenaNueva: String
)
