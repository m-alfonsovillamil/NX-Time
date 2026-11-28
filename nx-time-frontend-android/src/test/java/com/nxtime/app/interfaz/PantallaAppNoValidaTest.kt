package com.nxtime.app.interfaz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nxtime.app.BuildConfig
import com.nxtime.app.R
import com.nxtime.app.data.network.AppNoValida
import com.nxtime.app.ui.components.PantallaAppNoValida
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** La pantalla de cuando la app ya no se puede usar (ADR 064). */
@RunWith(RobolectricTestRunner::class)
class PantallaAppNoValidaTest {

    @get:Rule
    val pantalla = createComposeRule()

    private fun texto(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private var abierto: String? = null

    private fun montar(aviso: AppNoValida, pendientes: Int = 0) {
        pantalla.setContent {
            NxTimeTheme { PantallaAppNoValida(aviso, pendientes, onAbrir = { abierto = it }) }
        }
    }

    @Test
    fun `una version vieja lleva a actualizar con el enlace del servidor`() {
        montar(AppNoValida(retirada = false, urlDescarga = "https://play.ejemplo/app"))

        pantalla.onNodeWithText(texto(R.string.app_no_valida_titulo)).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.app_no_valida_actualizar)).performClick()
        assertEquals("https://play.ejemplo/app", abierto)
    }

    @Test
    fun `sin enlace del servidor, una version vieja va a su ficha de Play`() {
        montar(AppNoValida(retirada = false, urlDescarga = null))

        pantalla.onNodeWithText(texto(R.string.app_no_valida_actualizar)).performClick()
        assertEquals("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}", abierto)
    }

    @Test
    fun `una app retirada pide instalar la nueva, y sin enlace dice a quien pedirlo`() {
        montar(AppNoValida(retirada = true, urlDescarga = null))

        pantalla.onNodeWithText(texto(R.string.app_no_valida_titulo_retirada)).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.app_no_valida_sin_enlace)).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.app_no_valida_instalar)).assertDoesNotExist()
    }

    @Test
    fun `con fichajes sin enviar avisa de no desinstalarla`() {
        montar(AppNoValida(retirada = true, urlDescarga = "https://play.ejemplo/nueva"), pendientes = 2)

        val aviso = RuntimeEnvironment.getApplication().resources
            .getQuantityString(R.plurals.app_no_valida_pendientes, 2, 2)
        pantalla.onNodeWithText(aviso).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.app_no_valida_instalar)).performClick()
        assertEquals("https://play.ejemplo/nueva", abierto)
    }

}
