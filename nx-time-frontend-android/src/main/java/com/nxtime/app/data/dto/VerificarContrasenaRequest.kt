package com.nxtime.app.data.dto

/**
 * Comprobar la contraseña propia sin abrir sesión:
 * `POST /api/v1/perfil/verificar-contrasena`.
 *
 * Es lo que pide la huella antes de activarse. Antes se hacía con un
 * `/auth/login` completo, que emitía tokens nuevos, dejaba vivo el refresh
 * anterior en el servidor y consumía el límite de intentos del login.
 */
data class VerificarContrasenaRequest(val contrasena: String)
