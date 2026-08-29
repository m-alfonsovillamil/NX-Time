package com.nxtime.app.data.network

import com.nxtime.app.R
import com.nxtime.app.ui.util.MensajeUi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El traductor de errores del servidor a mensajes para el usuario.
 *
 * Es la pieza que arregla el defecto más visible de la app anterior --
 * enseñar "409 Conflict" en vez de "Ya hay una jornada activa." -- así
 * que conviene que quede fijada por tests: si alguien vuelve a tocarla,
 * que falle aquí y no en la pantalla de fichar.
 */
class ApiErrorParserTest {

    private fun cuerpo(json: String) =
        json.toResponseBody("application/problem+json".toMediaType())

    @Test
    fun `saca el detail de un ProblemDetail`() {
        val json = """
            {"type":"about:blank","title":"Conflict","status":409,
             "detail":"Ya hay una jornada activa.","instance":"/api/v1/fichaje"}
        """.trimIndent()

        assertEquals(
            MensajeUi.Texto("Ya hay una jornada activa."),
            ApiErrorParser.mensajeDe(cuerpo(json), 409)
        )
    }

    @Test
    fun `un cuerpo que no es JSON no revienta y cae al mensaje generico`() {
        // Lo que responde un proxy o un balanceador cuando el backend
        // ni siquiera ha llegado a contestar.
        val html = "<html><body><h1>502 Bad Gateway</h1></body></html>"

        assertEquals(
            MensajeUi.Recurso(R.string.error_servidor),
            ApiErrorParser.mensajeDe(cuerpo(html), 502)
        )
    }

    @Test
    fun `un cuerpo vacio cae al mensaje generico del codigo`() {
        assertEquals(
            MensajeUi.Recurso(R.string.error_sesion_caducada),
            ApiErrorParser.mensajeDe(cuerpo(""), 401)
        )
    }

    @Test
    fun `un detail vacio no se muestra tal cual`() {
        // Un "detail" en blanco dejaría al usuario con un aviso mudo.
        val json = """{"status":400,"detail":"   "}"""

        assertEquals(
            MensajeUi.Recurso(R.string.error_datos_invalidos),
            ApiErrorParser.mensajeDe(cuerpo(json), 400)
        )
    }

    @Test
    fun `sin cuerpo de error tambien hay mensaje`() {
        assertEquals(
            MensajeUi.Recurso(R.string.error_demasiados_intentos),
            ApiErrorParser.mensajeDe(null, 429)
        )
    }

    @Test
    fun `un fallo de red se explica como fallo de red`() {
        assertEquals(
            MensajeUi.Recurso(R.string.error_sin_conexion),
            ApiErrorParser.mensajeDeRed(java.net.UnknownHostException("api.nxtime"))
        )
    }
}
