package com.nxtime.app.ui.acceso

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.ProveedorSsoDTO
import com.nxtime.app.data.dto.RespuestaAutenticacion
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.sso.AccesoSso
import com.nxtime.app.data.sso.GuardaDelVerificador
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Entrar con Google o con Microsoft desde la app (ADR 036): la ida, la vuelta
 * y el canje.
 *
 * Lo que hay que sostener aquí es la atadura entre el código y el secreto de
 * la app: que la URL que se abre lleve el reto del verificador que se guarda,
 * que el canje presente ese verificador y no otro, y que una vuelta que nadie
 * ha pedido no haga nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SsoEnElAccesoTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val guarda = object : GuardaDelVerificador {
        override var verificador: String? = null
        override var paraVincular: Boolean = false
    }
    private val sso = AccesoSso(guarda)
    private val viewModel by lazy { LoginViewModel(repositorio, sso) }

    private val google = ProveedorSsoDTO("google", "Google", "https://api.nxtime-web.com/auth/sso/google/iniciar")
    private val sesion = RespuestaAutenticacion(token = "jwt", refreshToken = "refresh", nombre = "Ana", rol = "EMPLEADO")

    private fun <T> rechazo(codigo: Int) = Response.error<T>(
        codigo,
        """{"status":$codigo,"detail":"No."}""".toResponseBody("application/problem+json".toMediaType())
    )

    // ------------------------------------------------------------------
    // Qué proveedores hay
    // ------------------------------------------------------------------

    @Test
    fun `los proveedores que ofrece el servidor llegan a la pantalla`() = runTest {
        whenever(repositorio.getProveedoresSso()).thenReturn(Response.success(listOf(google)))

        viewModel
        advanceUntilIdle()

        assertEquals(listOf(google), viewModel.uiState.value.proveedoresSso)
    }

    /** Entrar con contraseña no puede depender de esto. */
    @Test
    fun `si la lista falla no hay botones ni error`() = runTest {
        whenever(repositorio.getProveedoresSso()).thenReturn(rechazo(500))

        viewModel
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.proveedoresSso.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    // ------------------------------------------------------------------
    // La ida
    // ------------------------------------------------------------------

    @Test
    fun `empezar guarda un verificador y abre el navegador con su reto`() = runTest {
        viewModel.entrarCon(google)

        val verificador = guarda.verificador!!
        // 32 bytes en base64url sin relleno.
        assertEquals(43, verificador.length)
        assertEquals(
            "${google.inicio}?cliente=app&reto=${AccesoSso.retoDe(verificador)}",
            viewModel.uiState.value.abrirEnNavegador
        )

        viewModel.navegadorAbierto()
        assertNull(viewModel.uiState.value.abrirEnNavegador)
    }

    @Test
    fun `cada ida lleva un verificador distinto y solo vale el ultimo`() = runTest {
        viewModel.entrarCon(google)
        val primero = guarda.verificador
        viewModel.entrarCon(google)

        assertFalse(primero == guarda.verificador)
    }

    /** El mismo ejemplo de la RFC 7636 que comprueba el servidor: si no coinciden, ningún canje vale. */
    @Test
    fun `el reto es el SHA-256 en base64url del verificador`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            AccesoSso.retoDe("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        )
    }

    // ------------------------------------------------------------------
    // La vuelta
    // ------------------------------------------------------------------

    @Test
    fun `al volver con un codigo se canjea con el verificador guardado y se entra`() = runTest {
        whenever(repositorio.canjearSso(any(), any())).thenReturn(Response.success(sesion))
        viewModel.entrarCon(google)
        val verificador = guarda.verificador!!

        sso.recibir(codigo = "el-codigo", error = null)
        advanceUntilIdle()

        verify(repositorio).canjearSso("el-codigo", verificador)
        verify(repositorio).procesarLoginExitoso(sesion)
        assertTrue(viewModel.uiState.value.accesoConcedido)
        // Gastado: ese código y ese verificador no vuelven a servir.
        assertNull(guarda.verificador)
        assertNull(sso.vuelta.value)
    }

    /**
     * El sistema puede cerrar la app mientras la persona elige cuenta. Al
     * volver arranca de cero: el verificador está en las preferencias y la
     * vuelta llega antes de que exista esta pantalla.
     */
    @Test
    fun `una vuelta que llega antes de que exista la pantalla tambien se atiende`() = runTest {
        whenever(repositorio.canjearSso(any(), any())).thenReturn(Response.success(sesion))
        guarda.verificador = "el-verificador-que-quedo-guardado"
        sso.recibir(codigo = "el-codigo", error = null)

        viewModel
        advanceUntilIdle()

        verify(repositorio).canjearSso("el-codigo", "el-verificador-que-quedo-guardado")
        assertTrue(viewModel.uiState.value.accesoConcedido)
    }

    @Test
    fun `al volver con un error se dice el motivo y no se canjea nada`() = runTest {
        viewModel.entrarCon(google)

        sso.recibir(codigo = null, error = "sin-cuenta")
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.sso_error_sin_cuenta), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.accesoConcedido)
        verify(repositorio, never()).canjearSso(any(), any())
        assertNull(guarda.verificador)
    }

    @Test
    fun `un motivo que esta version no conoce se explica como un fallo`() = runTest {
        viewModel.entrarCon(google)

        sso.recibir(codigo = null, error = "algo-nuevo")
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.sso_error_fallo), viewModel.uiState.value.error)
    }

    /**
     * Un enlace nxtime://sso?codigo=… que alguien le mande a la persona, para
     * que entre en la cuenta de otro: sin una ida pendiente, ni se mira.
     */
    @Test
    fun `una vuelta que nadie ha pedido no hace nada`() = runTest {
        viewModel
        sso.recibir(codigo = "el-codigo-de-otro", error = null)
        advanceUntilIdle()

        assertNull(sso.vuelta.value)
        verify(repositorio, never()).canjearSso(any(), any())
        assertFalse(viewModel.uiState.value.accesoConcedido)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `si el servidor rechaza el canje no se entra y se dice`() = runTest {
        whenever(repositorio.canjearSso(any(), any())).thenReturn(rechazo(400))
        viewModel.entrarCon(google)

        sso.recibir(codigo = "caducado", error = null)
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.sso_error_fallo), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.accesoConcedido)
        verify(repositorio, never()).procesarLoginExitoso(any())
        // Y el verificador se ha gastado: reintentar es empezar otra vez.
        assertNull(guarda.verificador)
    }

    @Test
    fun `una vuelta sin codigo ni error es un fallo`() = runTest {
        viewModel.entrarCon(google)

        sso.recibir(codigo = "  ", error = null)
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.sso_error_fallo), viewModel.uiState.value.error)
        verify(repositorio, never()).canjearSso(any(), any())
    }
}
