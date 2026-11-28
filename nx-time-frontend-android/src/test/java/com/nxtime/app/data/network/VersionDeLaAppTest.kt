package com.nxtime.app.data.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** La app dice su versión, y se entera de cuándo ya no la atienden (ADR 064). */
class VersionDeLaAppTest {

    @After
    fun limpiar() = VersionDeLaApp.olvidar()

    private fun respuesta(peticion: Request, codigo: Int, cuerpo: String) = Response.Builder()
        .request(peticion)
        .protocol(Protocol.HTTP_1_1)
        .code(codigo)
        .message("x")
        .body(cuerpo.toResponseBody("application/problem+json".toMediaType()))
        .build()

    /** Pasa una petición por el interceptor con la API respondiendo [codigo] y [cuerpo]. */
    private fun pasar(codigo: Int, cuerpo: String): Pair<Request, Response> {
        val original = Request.Builder().url("https://api.ejemplo.test/api/v1/fichaje/activo").build()
        val enviada = argumentCaptor<Request>()
        val cadena = mock<Interceptor.Chain> {
            on { request() } doReturn original
            on { proceed(any()) } doAnswer { respuesta(it.getArgument(0), codigo, cuerpo) }
        }
        val salida = VersionDeLaApp.interceptor(version = 28, paquete = "com.nxtime.app").intercept(cadena)
        org.mockito.kotlin.verify(cadena).proceed(enviada.capture())
        return enviada.firstValue to salida
    }

    @Test
    fun `cada peticion lleva la version y el paquete`() {
        val (enviada, _) = pasar(200, "{}")
        assertEquals("28", enviada.header(VersionDeLaApp.CABECERA_VERSION))
        assertEquals("com.nxtime.app", enviada.header(VersionDeLaApp.CABECERA_PAQUETE))
        assertNull(VersionDeLaApp.aviso.value)
    }

    @Test
    fun `un 426 de la API deja el aviso, y el cuerpo sigue entero para quien venga detras`() {
        val cuerpo = """{"status":426,"codigo":"APP_DESACTUALIZADA","urlDescarga":"https://play.ejemplo/app"}"""
        val (_, salida) = pasar(426, cuerpo)
        assertEquals(AppNoValida(retirada = false, urlDescarga = "https://play.ejemplo/app"), VersionDeLaApp.aviso.value)
        assertEquals(cuerpo, salida.body.string())
    }

    @Test
    fun `una app retirada lo sabe, y sin enlace queda en null`() {
        pasar(426, """{"codigo":"APP_RETIRADA","urlDescarga":""}""")
        assertEquals(AppNoValida(retirada = true, urlDescarga = null), VersionDeLaApp.aviso.value)
    }

    @Test
    fun `lo que no es un 426 nuestro no cuenta`() {
        assertNull(VersionDeLaApp.avisoDe(403, """{"codigo":"APP_RETIRADA"}"""))
        assertNull(VersionDeLaApp.avisoDe(426, "<html>proxy</html>"))
        assertNull(VersionDeLaApp.avisoDe(426, """{"codigo":"OTRA_COSA"}"""))
        assertNull(VersionDeLaApp.avisoDe(426, null))
        pasar(426, "no es json")
        assertNull(VersionDeLaApp.aviso.value)
    }
}
