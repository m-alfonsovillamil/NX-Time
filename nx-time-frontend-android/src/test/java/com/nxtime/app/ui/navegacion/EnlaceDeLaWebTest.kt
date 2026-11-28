package com.nxtime.app.ui.navegacion

import com.nxtime.app.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los enlaces de los botones de los correos que abren la app (App Links, 1.19). */
class EnlaceDeLaWebTest {

    /** El dominio de la web, de build.gradle.kts (`marcaHostWeb`). */
    private val web = BuildConfig.HOST_WEB

    @Test
    fun `un enlace de un correo da su destino`() {
        assertEquals(DESTINO_FIRMAS, destinoDeEnlaceWeb(web, "/firmas"))
        assertEquals(
            DESTINO_AUSENCIAS_EQUIPO_PENDIENTES,
            destinoDeEnlaceWeb(web, "/ausencias-equipo/pendientes")
        )
        // Con barra final también: hay clientes de correo que la añaden.
        assertEquals(DESTINO_CORRECCIONES_PENDIENTES, destinoDeEnlaceWeb(web.uppercase(), "/correcciones/pendientes/"))
    }

    @Test
    fun `lo que no es de la web, o no se sabe abrir, no navega`() {
        assertNull(destinoDeEnlaceWeb("otra-web.com", "/firmas"))
        assertNull(destinoDeEnlaceWeb(null, "/firmas"))
        assertNull(destinoDeEnlaceWeb(web, null))
        assertNull(destinoDeEnlaceWeb(web, "/"))
        // Los ajustes de la empresa solo están en la web.
        assertNull(destinoDeEnlaceWeb(web, "/ajustes-empresa"))
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
        // El dominio no se escribe en el manifiesto: lo pone Gradle.
        assertTrue(manifiesto.contains("""android:host="${'$'}{hostWeb}""""))
        assertEquals(setOf(web), HOSTS_DE_LA_WEB)
    }

    /**
     * La web publica en `assetlinks.json` el paquete que puede abrir sus enlaces:
     * si no es el de la app (por ejemplo, tras cambiar de nombre), los botones de
     * los correos abren el navegador.
     */
    @Test
    fun `la web declara el paquete de esta app`() {
        val assetlinks = File("../nx-time-frontend-web/public/.well-known/assetlinks.json").readText()
        val paquete = BuildConfig.APPLICATION_ID.removeSuffix(".dev")
        assertTrue(assetlinks, assetlinks.contains(""""package_name": "$paquete""""))
    }
}
