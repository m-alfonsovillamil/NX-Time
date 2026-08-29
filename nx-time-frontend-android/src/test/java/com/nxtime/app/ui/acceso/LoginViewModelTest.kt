package com.nxtime.app.ui.acceso

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PeticionLogin
import com.nxtime.app.data.dto.RespuestaAutenticacion
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val viewModel by lazy { LoginViewModel(repositorio) }

    private val respuestaValida = RespuestaAutenticacion(
        token = "jwt",
        refreshToken = "refresh",
        nombre = "Ana",
        rol = "EMPLEADO"
    )

    @Test
    fun `no se sale a la red con los campos vacios`() = runTest {
        viewModel.entrar()
        advanceUntilIdle()

        // Se señala el campo que falta, no "los campos" en bloque.
        assertEquals(
            MensajeUi.Recurso(R.string.login_email_vacio),
            viewModel.uiState.value.error
        )
        // Importa además porque /auth/login tiene límite de intentos por
        // IP: gastar uno en una petición que ya se sabe inválida acerca
        // al usuario a quedarse bloqueado.
        verify(repositorio, never()).login(any())
    }

    @Test
    fun `un login correcto guarda la sesion y concede el acceso`() = runTest {
        whenever(repositorio.login(any())).thenReturn(Response.success(respuestaValida))

        viewModel.onEmailCambia("  ana@nxtime.com  ")
        viewModel.onContrasenaCambia("secreta123")
        viewModel.entrar()
        advanceUntilIdle()

        // El correo va sin espacios: pegarlo desde el gestor de
        // contraseñas los arrastra con facilidad.
        verify(repositorio).login(PeticionLogin("ana@nxtime.com", "secreta123"))
        verify(repositorio).procesarLoginExitoso(respuestaValida)

        val estado = viewModel.uiState.value
        assertTrue(estado.accesoConcedido)
        assertFalse(estado.cargando)
    }

    @Test
    fun `unas credenciales malas muestran el mensaje del backend`() = runTest {
        whenever(repositorio.login(any())).thenReturn(
            Response.error(
                401,
                """{"status":401,"detail":"Correo o contraseña incorrectos."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )

        viewModel.onEmailCambia("ana@nxtime.com")
        viewModel.onContrasenaCambia("mal")
        viewModel.entrar()
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertEquals(MensajeUi.Texto("Correo o contraseña incorrectos."), estado.error)
        assertFalse(estado.accesoConcedido)
        assertFalse(estado.cargando)
    }

    @Test
    fun `con el correo puesto pero sin contrasena se avisa de la contrasena`() = runTest {
        viewModel.onEmailCambia("ana@nxtime.com")
        viewModel.entrar()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.login_contrasena_vacia),
            viewModel.uiState.value.error
        )
        verify(repositorio, never()).login(any())
    }

    @Test
    fun `escribir de nuevo borra el error anterior`() = runTest {
        viewModel.entrar()
        advanceUntilIdle()
        assertEquals(
            MensajeUi.Recurso(R.string.login_email_vacio),
            viewModel.uiState.value.error
        )

        viewModel.onEmailCambia("a")

        assertEquals(null, viewModel.uiState.value.error)
    }
}
