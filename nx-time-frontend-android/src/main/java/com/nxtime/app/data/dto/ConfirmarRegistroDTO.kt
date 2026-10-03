package com.nxtime.app.data.dto

/**
 * Lo que responde el registro de una empresa desde la V37 del backend (ADR
 * 034): no hay sesión todavía. Ha salido un código al correo, y la sesión se
 * abre al canjearlo con [ConfirmarRegistroRequest].
 */
data class RegistroPendienteDTO(
    val email: String? = null,
    val mensaje: String? = null
)

/** El código que llegó al registrar la empresa. Si vale, el servidor abre la sesión como el login. */
data class ConfirmarRegistroRequest(
    val email: String,
    val codigo: String,
    val origen: String = "ANDROID"
)
