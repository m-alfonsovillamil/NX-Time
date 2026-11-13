package com.nxtime.app.ui.navegacion

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los enlaces de los botones de los correos que abren la app (App Links, 1.19). */
class EnlaceDeLaWebTest {

    @Test
    fun `un enlace de un correo da su destino`() {
        assertEquals(DESTINO_FIRMAS, destinoDeEnlaceWeb("nxtime-web.com", "/firmas"))
        assertEquals(
            DESTINO_AUSENCIAS_EQUIPO_PENDIENTES,
            destinoDeEnlaceWeb("nxtime-web.com", "/ausencias-equipo/pendientes")
        )
        // Con barra final también: hay clientes de correo que la añaden.
        assertEquals(DESTINO_CORRECCIONES_PENDIENTES, destinoDeEnlaceWeb("NXTIME-WEB.com", "/correcciones/pendientes/"))
    }

    @Test
    fun `lo que no es de la web, o no se sabe abrir, no navega`() {
        assertNull(destinoDeEnlaceWeb("otra-web.com", "/firmas"))
        assertNull(destinoDeEnlaceWeb(null, "/firmas"))
        assertNull(destinoDeEnlaceWeb("nxtime-web.com", null))
        assertNull(destinoDeEnlaceWeb("nxtime-web.com", "/"))
        // Los ajustes de la empresa solo están en la web.
        assertNull(destinoDeEnlaceWeb("nxtime-web.com", "/ajustes-empresa"))
    }

    @Test
    fun `cada destino con enlace se sabe abrir`() {
        DESTINOS_CON_ENLACE.forEach { destino ->
            assertTrue("$destino no tiene pantalla", rutaDeAviso(destino) != null)
        }
    }

    /**
     * El manifiesto y la lista tienen que coincidir: un destino que falte en
     * el manifiesto abriría el navegador, y uno de más abriría la app sin
     * llevar a ninguna parte.
     */
    @Test
    fun `el manifiesto declara exactamente esos destinos, verificados`() {
        val manifiesto = File("src/main/AndroidManifest.xml").readText()
        val rutas = Regex("""android:path="/([^"]+)"""").findAll(manifiesto).map { it.groupValues[1] }.toSet()
        assertEquals(DESTINOS_CON_ENLACE.toSet(), rutas)
        assertTrue(manifiesto.contains("""android:autoVerify="true""""))
        HOSTS_DE_LA_WEB.forEach { assertTrue(manifiesto.contains("""android:host="$it"""")) }
    }
}
