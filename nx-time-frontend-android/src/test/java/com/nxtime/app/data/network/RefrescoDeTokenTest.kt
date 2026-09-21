package com.nxtime.app.data.network

import com.nxtime.app.data.session.SessionManager
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * El refresco del token cuando llegan varios 401 a la vez.
 *
 * Es el caso real de abrir la app: salen varias peticiones en paralelo y,
 * pasados los 15 minutos de vida del access token, todas reciben 401 casi
 * simultáneamente. Antes cada una pedía su propio refresco.
 */
class RefrescoDeTokenTest {

    /** Un SessionManager de mentira que guarda el token en memoria, como el de verdad. */
    private class SesionFalsa {
        val mock: SessionManager = mock()
        private var accessToken: String? = "token-viejo"

        init {
            whenever(mock.fetchAuthToken()).thenAnswer { synchronized(this) { accessToken } }
            whenever(mock.fetchRefreshToken()).thenReturn("refresh")
            whenever(mock.updateAccessToken(any())).thenAnswer { invocacion ->
                synchronized(this) { accessToken = invocacion.getArgument(0) }
                null
            }
        }
    }

    @Test
    fun `varios 401 a la vez piden UN solo refresco y todos reintentan con el token nuevo`() {
        val sesion = SesionFalsa()
        val refrescos = AtomicInteger()
        val refresco = RefrescoDeToken(sesion.mock) {
            refrescos.incrementAndGet()
            // Un refresco real tarda: sin esta pausa los hilos podrían no
            // llegar a solaparse y el test pasaría sin probar nada.
            Thread.sleep(50)
            "token-nuevo"
        }

        val hilos = 8
        val enPosicion = CountDownLatch(hilos)
        val salida = CountDownLatch(1)
        val terminados = CountDownLatch(hilos)
        val obtenidos = ConcurrentLinkedQueue<String?>()
        val ejecutor = Executors.newFixedThreadPool(hilos)
        try {
            repeat(hilos) {
                ejecutor.submit {
                    enPosicion.countDown()
                    salida.await()
                    obtenidos.add(refresco.tokenParaReintentar("token-viejo"))
                    terminados.countDown()
                }
            }
            assertTrue(enPosicion.await(5, TimeUnit.SECONDS))
            salida.countDown()
            assertTrue(terminados.await(10, TimeUnit.SECONDS))
        } finally {
            ejecutor.shutdownNow()
        }

        // Lo que arregla esta fase: ocho 401 y una sola llamada a /auth/refresh.
        assertEquals(1, refrescos.get())
        // Y ninguno se queda sin token: los siete que esperaron reciben el que
        // consiguió el primero.
        assertEquals(hilos, obtenidos.size)
        assertTrue(obtenidos.all { it == "token-nuevo" })
    }

    @Test
    fun `si otro hilo ya refresco, no se vuelve a pedir`() {
        val sesion = SesionFalsa()
        val refrescos = AtomicInteger()
        val refresco = RefrescoDeToken(sesion.mock) {
            refrescos.incrementAndGet()
            "token-nuevo"
        }

        // Primero uno refresca de verdad.
        refresco.tokenParaReintentar("token-viejo")
        // Y ahora llega el rezagado, con el token viejo en la mano.
        val segundo = refresco.tokenParaReintentar("token-viejo")

        assertEquals("token-nuevo", segundo)
        assertEquals("no hace falta pedir otro: el guardado ya sirve", 1, refrescos.get())
    }

    @Test
    fun `sin refresh token no se intenta nada`() {
        val sesion: SessionManager = mock()
        whenever(sesion.fetchAuthToken()).thenReturn("token-viejo")
        whenever(sesion.fetchRefreshToken()).thenReturn(null)
        val refrescos = AtomicInteger()

        val resultado = RefrescoDeToken(sesion) { refrescos.incrementAndGet(); "x" }
            .tokenParaReintentar("token-viejo")

        assertNull(resultado)
        assertEquals(0, refrescos.get())
        // Sin refresh token no hay sesión que expirar: nunca la hubo, o ya se
        // cerró. Expirarla aquí dispararía una navegación al login de más.
        verify(sesion, never()).expirarSesion()
    }

    @Test
    fun `si el refresco falla se expira la sesion`() {
        val sesion = SesionFalsa()

        val resultado = RefrescoDeToken(sesion.mock) { null }.tokenParaReintentar("token-viejo")

        assertNull(resultado)
        verify(sesion.mock).expirarSesion()
    }

    @Test
    fun `una excepcion al refrescar se trata como un fallo, no se propaga`() {
        val sesion = SesionFalsa()

        // Un Authenticator que lanza deja la petición original con una
        // excepción rara en vez de con su 401.
        val resultado = RefrescoDeToken(sesion.mock) { throw RuntimeException("sin red") }
            .tokenParaReintentar("token-viejo")

        assertNull(resultado)
        verify(sesion.mock).expirarSesion()
    }
}
