package com.nxtime.app.interfaz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.acceso.RecuperarAccesoScreen
import com.nxtime.app.ui.acceso.RecuperarAccesoViewModel
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response

/**
 * Elegir contraseña con un código, en sus dos pasos (ADR 014).
 *
 * Es por donde entra todo el mundo la primera vez: la cuenta nace sin
 * contraseña y el correo de bienvenida trae el código.
 */
@RunWith(RobolectricTestRunner::class)
class RecuperarAccesoScreenTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val repositorio: AuthRepository = mock {
        onBlocking { solicitarCodigoAcceso(any()) } doReturn Response.success(Unit)
        onBlocking { restablecerContrasena(any(), any(), any()) } doReturn Response.success(Unit)
    }
    private var vueltas = 0

    private fun texto(id: Int, vararg argumentos: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *argumentos)

    private fun montar() {
        // Fuera de la composición: dentro, cada recomposición crearía otro.
        val viewModel = RecuperarAccesoViewModel(repositorio)
        pantalla.setContent {
            NxTimeTheme {
                RecuperarAccesoScreen(onVolver = { vueltas++ }, viewModel = viewModel)
            }
        }
    }

    private fun escribir(etiqueta: Int, valor: String) =
        pantalla.onNodeWithText(texto(etiqueta)).performTextInput(valor)

    private fun tocar(id: Int) {
        pantalla.onNodeWithText(texto(id)).performClick()
        pantalla.waitForIdle()
    }

    @Test
    fun `pedir el codigo, escribirlo con la contrasena nueva y volver al acceso`() {
        montar()

        escribir(R.string.login_email, "ana@nxtime.com")
        tocar(R.string.recuperar_enviar_codigo)
        // No dice si la cuenta existe: «si tiene una cuenta, le hemos enviado…».
        pantalla.onNodeWithText(texto(R.string.recuperar_codigo_enviado, "ana@nxtime.com")).assertIsDisplayed()

        escribir(R.string.recuperar_codigo, "123456")
        escribir(R.string.contrasena_nueva, "una-contrasena-nueva")
        escribir(R.string.contrasena_repetir, "una-contrasena-nueva")
        tocar(R.string.recuperar_guardar)

        pantalla.onNodeWithText(texto(R.string.recuperar_hecho_titulo)).assertIsDisplayed()
        verifyBlocking(repositorio) { restablecerContrasena("ana@nxtime.com", "123456", "una-contrasena-nueva") }

        tocar(R.string.recuperar_ir_a_login)
        assertEquals(1, vueltas)
    }

    /* Pedir otro código anularía el de bienvenida: solo vale el último. */
    @Test
    fun `ya tengo un codigo pasa al segundo paso sin pedir otro`() {
        montar()

        escribir(R.string.login_email, "ana@nxtime.com")
        tocar(R.string.recuperar_ya_tengo_codigo)

        pantalla.onNodeWithText(texto(R.string.recuperar_codigo)).assertIsDisplayed()
        verifyBlocking(repositorio, never()) { solicitarCodigoAcceso(any()) }
    }

    @Test
    fun `si las dos contrasenas no coinciden lo dice y no sale a la red`() {
        montar()
        escribir(R.string.login_email, "ana@nxtime.com")
        tocar(R.string.recuperar_ya_tengo_codigo)

        escribir(R.string.recuperar_codigo, "123456")
        escribir(R.string.contrasena_nueva, "una-contrasena-nueva")
        escribir(R.string.contrasena_repetir, "otra-distinta-xx")
        tocar(R.string.recuperar_guardar)

        pantalla.onNodeWithText(texto(R.string.contrasena_no_coinciden)).assertIsDisplayed()
        verifyBlocking(repositorio, never()) { restablecerContrasena(any(), any(), any()) }
    }
}
