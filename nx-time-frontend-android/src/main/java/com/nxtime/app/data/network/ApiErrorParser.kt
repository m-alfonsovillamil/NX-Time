package com.nxtime.app.data.network

import com.google.gson.JsonParser
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Saca el mensaje que se le enseña al usuario de una respuesta de error.
 *
 * El backend devuelve `ProblemDetail` (RFC 7807) desde la Fase 2, con un
 * campo `detail` escrito para que lo lea una persona:
 *
 * ```json
 * {"type":"about:blank","title":"Conflict","status":409,
 *  "detail":"Ya hay una jornada activa.","instance":"/api/v1/fichaje"}
 * ```
 *
 * Ese trabajo no llegaba a la pantalla. De los doce ViewModel: tres
 * volcaban el JSON **entero** en un Toast, dos lo descartaban y ponían
 * "Error al cargar los datos", y el resto ni lo miraban. El peor caso
 * estaba en el flujo principal, donde el mensaje se componía con
 * `response.message()` -- que en Retrofit es la frase del estado HTTP, no
 * el cuerpo -- así que al fichar dos veces el usuario leía
 * "Error al registrar fichaje: 409 Conflict" en lugar de
 * "Ya hay una jornada activa.".
 *
 * Todo eso pasa ahora por aquí.
 */
object ApiErrorParser {

    /** Mensaje legible del error de una respuesta sin éxito. */
    fun mensajeDe(response: Response<*>): String =
        mensajeDe(response.errorBody(), response.code())

    fun mensajeDe(errorBody: ResponseBody?, codigo: Int): String {
        val detalle = detalleDelProblemDetail(errorBody)
        return detalle ?: mensajeGenerico(codigo)
    }

    /**
     * Devuelve el campo "detail" del cuerpo, o null si el cuerpo no es
     * un ProblemDetail utilizable.
     *
     * Se traga cualquier excepción a propósito: el cuerpo de un error no
     * siempre lo escribe la aplicación (un proxy o un balanceador pueden
     * responder HTML), y quedarse sin mensaje no puede convertirse en un
     * cierre inesperado justo cuando algo ya ha ido mal.
     */
    private fun detalleDelProblemDetail(errorBody: ResponseBody?): String? = try {
        val cuerpo = errorBody?.string()
        if (cuerpo.isNullOrBlank()) {
            null
        } else {
            JsonParser.parseString(cuerpo)
                .takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("detail")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Solo para cuando el backend no dice nada aprovechable. Explica qué
     * puede hacer el usuario, que es más útil que enseñarle el número.
     */
    private fun mensajeGenerico(codigo: Int): String = when (codigo) {
        400 -> "Los datos enviados no son válidos."
        401 -> "Tu sesión ha caducado. Vuelve a iniciar sesión."
        403 -> "No tienes permisos para realizar esta acción."
        404 -> "No se ha encontrado lo que buscabas."
        409 -> "La operación entra en conflicto con el estado actual."
        429 -> "Demasiados intentos. Espera un momento y vuelve a probar."
        in 500..599 -> "El servidor no está disponible. Inténtalo más tarde."
        else -> "Se ha producido un error inesperado."
    }

    /**
     * Mensaje para un fallo de red (sin respuesta del servidor). Se
     * separa del anterior porque la causa y la solución son distintas:
     * aquí la petición no llegó a salir.
     */
    fun mensajeDeRed(e: Throwable): String =
        "No se ha podido conectar con el servidor. Comprueba tu conexión."
}
