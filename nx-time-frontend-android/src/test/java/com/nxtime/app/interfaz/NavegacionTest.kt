package com.nxtime.app.interfaz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.work.testing.WorkManagerTestInitHelper
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.R
import com.nxtime.app.ui.navegacion.NxTimeNavHost
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * La navegación entera, con la aplicación arrancada de verdad.
 *
 * A diferencia de los tests de una pantalla, aquí corre `NxTimeApplication` y
 * la sesión es la `SessionManager` real, sobre sus preferencias: lo que se
 * prueba es justo lo que pasa entre la sesión y la pantalla. Solo el servidor
 * es un doble.
 *
 * **Por qué existe:** la 1.11 se quedaba en «Mi jornada», sin sesión y sin
 * salida, cuando esta caducaba al arrancar. Los 390 tests de entonces pasaban:
 * ninguno montaba la navegación.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = NxTimeApplication::class)
class NavegacionTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val app: NxTimeApplication
        get() = RuntimeEnvironment.getApplication() as NxTimeApplication

    private fun texto(id: Int): String = app.getString(id)

    @Before
    fun sinServidor() {
        app.authRepository = servidorCaido()
        // Cerrar la sesión cancela el recordatorio de fichar, que es de WorkManager.
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun entrarComo(vararg authorities: String) {
        app.sessionManager.saveAuthData(
            token = "jwt",
            refreshToken = "refresh",
            nombre = "Ana",
            authorities = authorities.toList()
        )
    }

    private fun montar(sesionIniciada: Boolean) {
        pantalla.setContent {
            NxTimeTheme {
                NxTimeNavHost(sesionIniciada = sesionIniciada, sessionManager = app.sessionManager)
            }
        }
        pantalla.waitForIdle()
    }

    private fun estaEnElAcceso() {
        pantalla.onNodeWithText(texto(R.string.login_entrar)).assertIsDisplayed()
        // Y sin la barra de quien tiene sesión.
        pantalla.onNodeWithText(texto(R.string.barra_historial)).assertDoesNotExist()
    }

    /**
     * La regresión de la 1.11. La sesión caduca ANTES de que exista pantalla:
     * al arrancar, el registro de push habla con el servidor, este rechaza el
     * refresh y la sesión se borra. La actividad ya había decidido abrir en
     * «Mi jornada», porque al mirarlo sí había sesión.
     */
    @Test
    fun `una sesion que caduca antes de pintar nada lleva al acceso`() {
        entrarComo("fichaje:escribir")
        app.sessionManager.expirarSesion()

        montar(sesionIniciada = true)

        estaEnElAcceso()
    }

    @Test
    fun `una sesion que caduca con la app abierta lleva al acceso`() {
        entrarComo("fichaje:escribir")
        montar(sesionIniciada = true)
        pantalla.onNodeWithText(texto(R.string.barra_historial)).assertIsDisplayed()

        app.sessionManager.expirarSesion()
        pantalla.waitForIdle()

        estaEnElAcceso()
    }

    @Test
    fun `sin sesion se abre en el acceso`() {
        montar(sesionIniciada = false)

        estaEnElAcceso()
    }

    /* La pestaña de gestión la decide lo que el servidor dijo que esta persona puede hacer. */

    @Test
    fun `quien no gestiona un equipo no tiene la pestana de gestion`() {
        entrarComo("fichaje:escribir", "fichaje:leer")

        montar(sesionIniciada = true)

        pantalla.onNodeWithText(texto(R.string.barra_jornada)).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.barra_gestion)).assertDoesNotExist()
    }

    @Test
    fun `quien gestiona un equipo si la tiene`() {
        entrarComo("fichaje:escribir", "fichaje:leer", "fichaje:leer:equipo")

        montar(sesionIniciada = true)

        pantalla.onNodeWithText(texto(R.string.barra_gestion)).assertIsDisplayed()
    }
}
