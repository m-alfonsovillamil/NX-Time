package com.nxtime.app.data.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Que la sesión caduque **antes de que nadie esté mirando** y aun así se
 * acabe en el login.
 *
 * Es el fallo de octubre de 2026: al arrancar el proceso, el registro de push
 * habla con el servidor antes de que exista ninguna pantalla. Con una sesión
 * guardada que ya no valía, el refresco fallaba, la sesión se borraba y el
 * aviso se emitía sin nadie al otro lado. La app se quedaba en «Mi jornada»,
 * sin token y sin salida.
 */
class SenalDeSesionCaducadaTest {

    @Test
    fun `quien empieza a mirar despues de que caduque se entera igual`() = runTest {
        val senal = SenalDeSesionCaducada()

        // La sesión muere con la app recién arrancada: todavía no hay pantalla.
        senal.avisar()

        // Y ahora llega la pantalla. Tiene que verlo.
        assertTrue(senal.pendiente.first())
    }

    @Test
    fun `el aviso de una sola vez que habia antes se perdia`() = runTest {
        // Exactamente lo que tenía SessionManager: por esto no vale. El
        // `extraBufferCapacity` no guarda nada cuando no hay nadie suscrito.
        val deAntes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        assertTrue("emitir dice que sí…", deAntes.tryEmit(Unit))

        val recibido = withTimeoutOrNull(200) { deAntes.first() }
        assertNull("…pero quien llega después no recibe nada", recibido)
    }

    @Test
    fun `una vez atendida no vuelve a saltar al recrear la pantalla`() = runTest {
        val senal = SenalDeSesionCaducada()
        senal.avisar()

        // El grafo de navegación lleva al login y lo deja dicho.
        senal.atendida()

        // Girar el móvil en el login recrea la pantalla, que vuelve a mirar:
        // no puede navegar otra vez y borrar lo que se estaba escribiendo.
        assertFalse(senal.pendiente.first())
    }

    @Test
    fun `quien ya estaba mirando lo ve en cuanto caduca, y una sola vez`() = runTest {
        val senal = SenalDeSesionCaducada()
        val vistas = mutableListOf<Boolean>()
        val pantalla = launch { senal.pendiente.collect { vistas.add(it) } }
        testScheduler.advanceUntilIdle()

        senal.avisar()
        testScheduler.advanceUntilIdle()
        // Dos peticiones que fallan casi a la vez no son dos caducidades.
        senal.avisar()
        testScheduler.advanceUntilIdle()

        pantalla.cancel()
        assertEquals(listOf(false, true), vistas)
    }

    @Test
    fun `volver a entrar deja la señal limpia para la sesion nueva`() = runTest {
        val senal = SenalDeSesionCaducada()
        senal.avisar()

        // `saveAuthData` llama a esto al guardar una sesión nueva.
        senal.atendida()
        assertFalse(senal.pendiente.value)

        // Y si la sesión nueva caduca más adelante, vuelve a avisar.
        senal.avisar()
        assertTrue(senal.pendiente.value)
    }
}
