package com.nxtime.app.data.dto

/**
 * DTO que el backend devuelve tras un login o registro exitoso.
 *
 * refreshToken desde la Fase 4 del backend: el token de acceso ahora
 * dura poco (15 min); refreshToken es de larga duración y sirve para
 * pedir uno nuevo sin volver a pedir contraseña (ver RetrofitClient,
 * el Authenticator que lo usa automáticamente).
 *
 * Con el segundo factor (ADR 051 del backend), el login de quien lo tiene
 * activado devuelve SOLO [desafio]: sin token, sin refresh, sin nombre. Gson
 * no mira la nulabilidad de Kotlin, así que esos campos llegan a null aunque
 * el tipo diga otra cosa: hay que mirar [desafio] antes de tocar ninguno
 * (ver [faltaElCodigo]).
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
    val authorities: List<String> = emptyList(),
    /**
     * La zona de su empresa, en nombre IANA (ADR 032): en ella se cuentan
     * los días y se enseñan las horas. Null con un backend de antes, y
     * entonces se queda la de por defecto (ver `DateFormats.fijarZona`).
     */
    val zonaHoraria: String? = null,
    /**
     * Si tiene que entrar con segundo factor y esta sesión no ha pasado por
     * él (ADR 051): el servidor no le deja hacer nada más que activarlo, y
     * eso se hace desde la web.
     */
    val segundoFactorPorActivar: Boolean = false,
    /** El paso intermedio del login: falta el código. Ver la cabecera. */
    val desafio: String? = null
) {
    /** La contraseña era buena y falta el código del segundo factor. */
    val faltaElCodigo: Boolean get() = desafio != null
}
