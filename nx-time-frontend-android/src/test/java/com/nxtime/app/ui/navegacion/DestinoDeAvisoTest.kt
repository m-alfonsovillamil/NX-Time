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
        // "horas-extra" estaba aqui hasta la Fase F y "canal-denuncias"
        // hasta la G: los dos han dejado de servir de ejemplo por el
        // mejor motivo posible -- que ahora existen, que es exactamente
        // el caso que este test describia. Se sustituyen por otros que
        // siguen sin existir.
        assertNull(rutaDeAviso("correccion/42"))
        assertNull(rutaDeAviso("ofertas-internas"))
        assertNull(rutaDeAviso("candidaturas/pendientes"))
    }

    @Test
    fun `los dos avisos de horas extra llevan a la misma pantalla (Fase F)`() {
        // El de exceso detectado y el de bolsa al limite comparten
        // destino: los dos se resuelven mirando la misma lista.
        assertEquals(Pantalla.HORAS_EXTRA.ruta, rutaDeAviso(DESTINO_HORAS_EXTRA))
    }

    @Test
    fun `los dos avisos del canal llevan a pantallas DISTINTAS (Fase G)`() {
        // Es lo contrario que en horas extra, y a proposito: "denuncia
        // recibida" solo le llega a quien instruye y le lleva a la
        // bandeja; "denuncia actualizada" solo le llega a quien denuncio
        // identificandose y le lleva a la suya. Mandarlos a la misma
        // pantalla dejaria a un empleado en una lista que no puede ver.
        assertEquals(Pantalla.CANAL_DENUNCIAS.ruta, rutaDeAviso(DESTINO_CANAL_DENUNCIAS))
        assertEquals(Pantalla.DENUNCIAS.ruta, rutaDeAviso(DESTINO_DENUNCIAS))
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
