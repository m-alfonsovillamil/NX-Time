package com.nxtime.app.data.network

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class ArranqueEnFrioTest {

    private var ahora = 1_000_000L

    // Retardo de aviso corto para no alargar la suite: lo que se prueba
    // es que el aviso llega y se retira, no cuánto tarda.
    private val arranque = ArranqueEnFrio(reloj = { ahora }, retardoAvisoMs = 50)

    private fun cadena(codigo: Int = 200, alResponder: () -> Unit = {}): Interceptor.Chain {
        val peticion = Request.Builder().url("https://nxtime.test/api/v1/fichaje").build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(peticion)
        whenever(chain.withConnectTimeout(any(), any())).thenReturn(chain)
        whenever(chain.withReadTimeout(any(), any())).thenReturn(chain)
        whenever(chain.proceed(any())).thenAnswer {
            alResponder()
            Response.Builder()
                .request(peticion)
                .protocol(Protocol.HTTP_1_1)
                .code(codigo)
                .message("")
                .build()
        }
        return chain
    }

    private fun verificarEsperaLarga(chain: Interceptor.Chain) {
        verify(chain).withConnectTimeout(ArranqueEnFrio.CONEXION_LARGA_S.toInt(), TimeUnit.SECONDS)
        verify(chain).withReadTimeout(ArranqueEnFrio.LECTURA_LARGA_S.toInt(), TimeUnit.SECONDS)
    }

    private fun esperarHasta(limiteMs: Long = 2_000, condicion: () -> Boolean): Boolean {
        val fin = System.currentTimeMillis() + limiteMs
        while (System.currentTimeMillis() < fin) {
            if (condicion()) return true
            Thread.sleep(10)
        }
        return condicion()
    }

    @Test
    fun `la primera peticion del proceso espera lo que tarda en despertar`() {
        // Es la de cada mañana: la app acaba de abrirse y no sabe nada
        // del servidor, que lleva horas dormido.
        val chain = cadena()
        arranque.interceptor.intercept(chain)

        verificarEsperaLarga(chain)
    }

    @Test
    fun `con una respuesta reciente rigen los tiempos cortos`() {
        arranque.interceptor.intercept(cadena())
        ahora += 60_000

        val chain = cadena()
        arranque.interceptor.intercept(chain)

        // Con el servidor despierto, una espera de minutos sería peor
        // que el error: se deja el tiempo corto del cliente.
        verify(chain, never()).withReadTimeout(any(), any())
    }

    @Test
    fun `pasado el umbral sin respuestas vuelve la espera larga`() {
        arranque.interceptor.intercept(cadena())
        ahora += ArranqueEnFrio.UMBRAL_DORMIDO_MS

        val chain = cadena()
        arranque.interceptor.intercept(chain)

        verificarEsperaLarga(chain)
    }

    @Test
    fun `un error 5xx no cuenta como servidor despierto`() {
        // Mientras Render levanta la instancia, el 502 lo da su proxy.
        arranque.interceptor.intercept(cadena(codigo = 502))

        val chain = cadena()
        arranque.interceptor.intercept(chain)

        verificarEsperaLarga(chain)
    }

    @Test
    fun `un error 4xx si cuenta como servidor despierto`() {
        // Un 401 lo escribe Spring: la aplicación está en pie.
        arranque.interceptor.intercept(cadena(codigo = 401))

        assertFalse(arranque.puedeEstarDormido())
    }

    @Test
    fun `una espera larga avisa de que el servidor despierta y lo retira al responder`() {
        var avisoMientrasEsperaba = false
        val chain = cadena(alResponder = {
            avisoMientrasEsperaba = esperarHasta { arranque.despertando.value }
        })

        arranque.interceptor.intercept(chain)

        assertTrue(avisoMientrasEsperaba)
        assertFalse(arranque.despertando.value)
    }

    @Test
    fun `una respuesta rapida no llega a avisar`() {
        // Con el keep-alive, el caso normal es que el servidor ya estuviera
        // despierto: el aviso no puede parpadear en cada arranque de la app.
        arranque.interceptor.intercept(cadena())

        Thread.sleep(200)

        assertFalse(arranque.despertando.value)
    }

    @Test
    fun `si la peticion falla el aviso tambien se retira`() {
        val chain = cadena(alResponder = {
            esperarHasta { arranque.despertando.value }
            throw java.net.SocketTimeoutException("timeout")
        })

        runCatching { arranque.interceptor.intercept(chain) }

        assertFalse(arranque.despertando.value)
    }
}
