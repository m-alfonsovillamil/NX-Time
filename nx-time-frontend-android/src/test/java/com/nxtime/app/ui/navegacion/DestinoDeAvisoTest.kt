package com.nxtime.app.ui.navegacion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La traducción de destinos lógicos a rutas de este grafo.
 *
 * Lo que de verdad se prueba aquí es el caso de abajo: que un símbolo
 * desconocido devuelva null en vez de reventar. Va a pasar en cuanto el
 * backend desplegado vaya por delante de la app instalada, y entonces
 * la diferencia es entre un aviso que se lee pero no navega y una app
 * que se cierra al tocarlo.
 */
class DestinoDeAvisoTest {

    @Test
    fun `cada destino conocido resuelve a su ruta`() {
        assertEquals(Pantalla.FICHAR.ruta, rutaDeAviso(DESTINO_FICHAR))
        assertEquals(Pantalla.AUSENCIAS.ruta, rutaDeAviso(DESTINO_AUSENCIAS))
        assertEquals(
            Pantalla.ausenciasEquipo(resueltas = false),
            rutaDeAviso(DESTINO_AUSENCIAS_EQUIPO_PENDIENTES)
        )
        assertEquals(
            Pantalla.ausenciasEquipo(resueltas = true),
            rutaDeAviso(DESTINO_AUSENCIAS_EQUIPO_RESUELTAS)
        )
    }

    @Test
    fun `un destino de una version mas nueva del backend no navega, pero tampoco revienta`() {
        // "horas-extra" estaba aqui hasta la Fase F, como ejemplo de
        // destino que el backend podria mandar y la app no conocer. Ha
        // dejado de servir de ejemplo porque ahora existe -- que es
        // exactamente el caso que este test describia -- y se sustituye
        // por otros dos que siguen sin existir.
        assertNull(rutaDeAviso("correccion/42"))
        assertNull(rutaDeAviso("canal-denuncias"))
        assertNull(rutaDeAviso("ofertas-internas"))
    }

    @Test
    fun `los dos avisos de horas extra llevan a la misma pantalla (Fase F)`() {
        // El de exceso detectado y el de bolsa al limite comparten
        // destino: los dos se resuelven mirando la misma lista.
        assertEquals(Pantalla.HORAS_EXTRA.ruta, rutaDeAviso(DESTINO_HORAS_EXTRA))
    }

    @Test
    fun `un aviso sin destino no navega`() {
        assertNull(rutaDeAviso(null))
        assertNull(rutaDeAviso(""))
    }

    @Test
    fun `las rutas de equipo llevan el argumento resuelto, no la plantilla`() {
        // La ruta del enum es "ausencias-equipo/{resueltas}"; navegar a
        // eso literalmente no encontraría destino.
        assertEquals("ausencias-equipo/false", rutaDeAviso(DESTINO_AUSENCIAS_EQUIPO_PENDIENTES))
        assertEquals("ausencias-equipo/true", rutaDeAviso(DESTINO_AUSENCIAS_EQUIPO_RESUELTAS))
    }
}
