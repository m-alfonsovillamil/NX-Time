package com.nxtime.app.data.sinconexion

import com.nxtime.app.data.dto.EnvioSinConexionRequest
import com.nxtime.app.data.dto.EnvioSinConexionResponse
import com.nxtime.app.data.dto.ErrorDelEnvio
import com.nxtime.app.data.dto.FichajePendiente
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.ResultadoSinConexion
import com.nxtime.app.data.dto.TipoFichaje
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.io.IOException

class EnvioDeFichajesTest {

    private val repositorio: AuthRepository = mock()
    private val cola = ColaEnMemoria()
    private val entorno = EntornoFalso(elapsed = 5_000, arranqueActual = 5)
    private val envio = EnvioDeFichajes(repositorio, cola, entorno) { "ana@x" }

    private fun fichaje(id: String, dueno: String = "ana@x", tipo: TipoFichaje = TipoFichaje.INICIO) = FichajePendiente(
        idCliente = id,
        dueno = dueno,
        tipo = tipo,
        horaDelDispositivo = "2026-10-26T07:00:00Z",
        elapsedAlFichar = 1_000,
        arranqueAlFichar = 5
    )

    private fun aplicado(id: String, resultado: String = "APLICADO", motivo: String? = null) =
        ResultadoSinConexion(idCliente = id, resultado = resultado, motivo = motivo)

    private suspend fun servidorDespierto() {
        whenever(repositorio.getRegistroActivo()).thenReturn(Response.success(null))
    }

    private fun errorHttp(codigo: Int) = Response.error<EnvioSinConexionResponse>(
        codigo, "{}".toResponseBody("application/json".toMediaType())
    )

    @Test
    fun `sin nada en la cola ni se llama al servidor`() = runTest {
        assertEquals(ResultadoDelEnvio.TERMINADO, envio.enviar())
        verify(repositorio, never()).getRegistroActivo()
    }

    @Test
    fun `manda en orden, con el tiempo medido justo antes, y vacia la cola`() = runTest {
        servidorDespierto()
        cola.anadir(fichaje("a"))
        cola.anadir(fichaje("b", tipo = TipoFichaje.FIN))
        whenever(repositorio.enviarFichajesSinConexion(any()))
            .thenReturn(Response.success(EnvioSinConexionResponse(listOf(aplicado("a"), aplicado("b")), null)))

        assertEquals(ResultadoDelEnvio.TERMINADO, envio.enviar())

        val captor = argumentCaptor<EnvioSinConexionRequest>()
        verify(repositorio).enviarFichajesSinConexion(captor.capture())
        assertEquals(listOf("a", "b"), captor.firstValue.fichajes.map { it.idCliente })
        assertEquals(4_000L, captor.firstValue.fichajes[0].msDesdeElFichaje)
        assertTrue(cola.fichajes.isEmpty())
    }

    /** Si la respuesta se pierde se reenvía: el servidor lo reconoce por su `idCliente`. */
    @Test
    fun `sin respuesta nada sale de la cola, y se reintenta`() = runTest {
        servidorDespierto()
        cola.anadir(fichaje("a"))
        whenever(repositorio.enviarFichajesSinConexion(any())).thenAnswer { throw IOException("sin red") }

        assertEquals(ResultadoDelEnvio.REINTENTAR, envio.enviar())
        assertEquals(1, cola.fichajes.size)
    }

    @Test
    fun `con el servidor caido nada sale de la cola, y se reintenta`() = runTest {
        servidorDespierto()
        cola.anadir(fichaje("a"))
        whenever(repositorio.enviarFichajesSinConexion(any())).thenReturn(errorHttp(503))

        assertEquals(ResultadoDelEnvio.REINTENTAR, envio.enviar())
        assertEquals(1, cola.fichajes.size)
        // Se arregla solo: no se avisa.
        assertNull(cola.errorDelEnvio("ana@x"))
    }

    /** Antes reintentaba sin decir nada a nadie. */
    @Test
    fun `un rechazo que no se arregla esperando se anota con su motivo, y se sigue reintentando`() = runTest {
        servidorDespierto()
        cola.anadir(fichaje("a"))
        whenever(repositorio.enviarFichajesSinConexion(any())).thenReturn(
            Response.error(
                403,
                """{"status":403,"detail":"Tu cuenta está desactivada."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )

        assertEquals(ResultadoDelEnvio.REINTENTAR, envio.enviar())
        assertEquals(1, cola.fichajes.size)
        assertEquals(ErrorDelEnvio(403, "Tu cuenta está desactivada."), cola.errorDelEnvio("ana@x"))
    }

    /** La sesión ya lo dice (vuelve al login): anotarlo enseñaría un aviso viejo al volver a entrar. */
    @Test
    fun `una sesion caducada no se anota`() = runTest {
        whenever(repositorio.getRegistroActivo()).thenReturn(Response.error(401, "".toResponseBody(null)))
        cola.anadir(fichaje("a"))

        assertEquals(ResultadoDelEnvio.REINTENTAR, envio.enviar())
        assertEquals(1, cola.fichajes.size)
        assertNull(cola.errorDelEnvio("ana@x"))
    }

    @Test
    fun `un rechazo al despertar el servidor tambien se anota`() = runTest {
        whenever(repositorio.getRegistroActivo()).thenReturn(Response.error(403, "".toResponseBody(null)))
        cola.anadir(fichaje("a"))

        assertEquals(ResultadoDelEnvio.REINTENTAR, envio.enviar())
        assertEquals(ErrorDelEnvio(403, null), cola.errorDelEnvio("ana@x"))
    }

    @Test
    fun `el primer envio que llega borra el rechazo anotado`() = runTest {
        servidorDespierto()
        cola.guardarErrorDelEnvio("ana@x", ErrorDelEnvio(403, null))
        cola.anadir(fichaje("a"))
        whenever(repositorio.enviarFichajesSinConexion(any()))
            .thenReturn(Response.success(EnvioSinConexionResponse(listOf(aplicado("a")), null)))

        assertEquals(ResultadoDelEnvio.TERMINADO, envio.enviar())
        assertNull(cola.errorDelEnvio("ana@x"))
    }

    @Test
    fun `si no despierta el servidor no se manda`() = runTest {
        whenever(repositorio.getRegistroActivo()).thenAnswer { throw IOException("sin red") }
        cola.anadir(fichaje("a"))

        assertEquals(ResultadoDelEnvio.REINTENTAR, envio.enviar())
        verify(repositorio, never()).enviarFichajesSinConexion(any())
    }

    @Test
    fun `lo rechazado y lo que queda a revisar se queda a la vista, y lo aplicado no`() = runTest {
        servidorDespierto()
        listOf("a", "b", "c").forEach { cola.anadir(fichaje(it)) }
        whenever(repositorio.enviarFichajesSinConexion(any())).thenReturn(
            Response.success(
                EnvioSinConexionResponse(
                    listOf(aplicado("a"), aplicado("b", "RECHAZADO", "Ya hay una jornada."), aplicado("c", "A_REVISAR", "Tarde.")),
                    null
                )
            )
        )

        envio.enviar()

        assertTrue(cola.fichajes.isEmpty())
        assertEquals(listOf("b", "c"), cola.todosLosAvisos.map { it.idCliente })
        assertEquals(listOf(false, true), cola.todosLosAvisos.map { it.aRevisar })
        assertEquals("Ya hay una jornada.", cola.todosLosAvisos[0].motivo)
    }

    @Test
    fun `nunca se manda con la sesion de otra persona`() = runTest {
        servidorDespierto()
        cola.anadir(fichaje("de-otro", dueno = "luis@x"))

        assertEquals(ResultadoDelEnvio.TERMINADO, envio.enviar())

        verify(repositorio, never()).enviarFichajesSinConexion(any())
        assertEquals(1, cola.fichajes.size)
    }

    @Test
    fun `sin sesion no se hace nada, y lo guardado sigue ahi`() = runTest {
        cola.anadir(fichaje("a"))

        assertEquals(ResultadoDelEnvio.TERMINADO, EnvioDeFichajes(repositorio, cola, entorno) { null }.enviar())
        assertEquals(1, cola.fichajes.size)
    }

    @Test
    fun `lo que entra mientras se envia tambien sale`() = runTest {
        servidorDespierto()
        cola.anadir(fichaje("a"))
        whenever(repositorio.enviarFichajesSinConexion(any())).thenAnswer { llamada ->
            val ids = llamada.getArgument<EnvioSinConexionRequest>(0).fichajes.map { it.idCliente }
            if (ids.first() == "a") cola.anadir(fichaje("b"))
            Response.success(EnvioSinConexionResponse(ids.map { aplicado(it) }, null))
        }

        assertEquals(ResultadoDelEnvio.TERMINADO, envio.enviar())

        verify(repositorio, times(2)).enviarFichajesSinConexion(any())
        assertTrue(cola.fichajes.isEmpty())
    }

    @Test
    fun `guarda la jornada que dice el servidor`() = runTest {
        servidorDespierto()
        cola.anadir(fichaje("a"))
        val abierta = Registro(id = 3, horaEntrada = "2026-10-26T07:00:00Z", horaSalida = null, enPausa = false)
        whenever(repositorio.enviarFichajesSinConexion(any()))
            .thenReturn(Response.success(EnvioSinConexionResponse(listOf(aplicado("a")), abierta)))

        envio.enviar()

        assertEquals(abierta, cola.ultimaJornada("ana@x"))
    }
}
