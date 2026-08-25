package com.nxtime.app.data.dto

/**
 * DTO que el backend devuelve tras un login o registro exitoso.
 *
 * refreshToken desde la Fase 4 del backend: el token de acceso ahora
 * dura poco (15 min); refreshToken es de larga duración y sirve para
 * pedir uno nuevo sin volver a pedir contraseña (ver RetrofitClient,
 * el Authenticator que lo usa automáticamente).
 */
data class RespuestaAutenticacion(
    val token: String,
    val refreshToken: String,
    val nombre: String,
    val rol: String
)
