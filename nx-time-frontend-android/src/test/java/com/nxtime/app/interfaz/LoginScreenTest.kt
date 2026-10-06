package com.nxtime.app.interfaz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nxtime.app.R
import com.nxtime.app.data.dto.RespuestaAutenticacion
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.acceso.LoginScreen
import com.nxtime.app.ui.acceso.LoginViewModel
import com.nxtime.app.ui.theme.NxTimeTheme
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response

/**
 * La pantalla de acceso, pintada de verdad y tocada como la tocaría una persona.
 *
 * `LoginViewModelTest` ya prueba qué decide el ViewModel. Esto prueba lo que
 * faltaba: que la pantalla se lo enseña a quien la mira, y que sus botones
 * hacen lo que dicen.
 */
@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val repositorio: AuthRepository = mock()
    private var accesos = 0

    private fun texto(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun montar() {
        // Fuera de la composición: dentro, cada recomposición crearía otro.
        val viewModel = LoginViewModel(repositorio)
        pantalla.setContent {
            NxTimeTheme {
                LoginScreen(
                    onAccesoConcedido = { accesos++ },
                    onIrRegistroEmpresa = {},
                    onIrRecuperarAcceso = {},
                    viewModel = viewModel
                )
            }
        }
    }

    private fun escribirCredenciales() {
        pantalla.onNodeWithText(texto(R.string.login_email)).performTextInput("ana@nxtime.com")
        pantalla.onNodeWithText(texto(R.string.login_contrasena)).performTextInput("una-contrasena")
    }

    @Test
    fun `con el correo vacio lo dice y no sale a la red`() {
        montar()

        pantalla.onNodeWithText(texto(R.string.login_entrar)).performClick()

        pantalla.onNodeWithText(texto(R.string.login_email_vacio)).assertIsDisplayed()
        verifyBlocking(repositorio, never()) { login(any()) }
    }

    @Test
    fun `con credenciales malas se lee el mensaje del servidor y no se entra`() {
        repositorio.stub {
            onBlocking { login(any()) } doReturn Response.error(
                401,
                """{"status":401,"detail":"Correo o contraseña incorrectos."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        }
        montar()

        escribirCredenciales()
        pantalla.onNodeWithText(texto(R.string.login_entrar)).performClick()

        pantalla.onNodeWithText("Correo o contraseña incorrectos.").assertIsDisplayed()
        assertEquals(0, accesos)
    }

    @Test
    fun `con credenciales buenas se guarda la sesion y se entra una sola vez`() {
        val respuesta = RespuestaAutenticacion(token = "jwt", refreshToken = "refresh", nombre = "Ana", rol = "EMPLEADO")
        repositorio.stub {
            onBlocking { login(any()) } doReturn Response.success(respuesta)
        }
        montar()

        escribirCredenciales()
        pantalla.onNodeWithText(texto(R.string.login_entrar)).performClick()
        pantalla.waitForIdle()

        verifyBlocking(repositorio) { procesarLoginExitoso(respuesta) }
        assertEquals(1, accesos)
    }
}
