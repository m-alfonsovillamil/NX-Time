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
    val rol: String,
    /**
     * Lo que esta persona puede hacer, resuelto por el SERVIDOR.
     *
     * Hasta la Fase C3 la app deducia esto del `rol` con su propia copia
     * del reparto de permisos (`ui/util/Permisos.kt`), que funcionaba
     * mientras alguien se acordara de tocar los dos sitios. Ahora llega
     * hecho, y `Permisos` solo pregunta si una cadena esta en la lista.
     *
     * Con valor por defecto para que una respuesta de un backend antiguo
     * --o un test que no la ponga-- signifique "sin permisos", nunca
     * "todos".
     */
    val authorities: List<String> = emptyList()
)
