package com.nxtime.app.ui.acceso

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PeticionRegistroSso
import com.nxtime.app.data.dto.ProveedorSsoDTO
import com.nxtime.app.data.dto.RegistroPendienteSsoDTO
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
import org.junit.Assert.assertNotNull
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
 * Registrar una empresa con una cuenta de Google o de Microsoft desde la app
 * (ADR 044): la ida, la vuelta, el pase y el formulario.
 *
 * Lo que hay que sostener es lo mismo que al entrar, la atadura entre el
 * código y el secreto de la app, y además que con la cuenta no se pida ni se
 * mande correo ni contraseña, y que un pase que no vale no deje a nadie
 * atascado en un formulario que ya no puede enviar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistroConSsoTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val guarda = object : GuardaDelVerificador {
        override var verificador: String? = null
        override var paraVincular: Boolean = false
        override var paraRegistrar: Boolean = false
    }
    private val sso = AccesoSso(guarda)
    private val viewModel by lazy { RegistroEmpresaViewModel(repositorio, sso) }

    private val google = ProveedorSsoDTO("google", "Google", "https://api.nxtime-web.com/auth/sso/google/iniciar")
    private val sesion = RespuestaAutenticacion(token = "jwt", refreshToken = "refresh", nombre = "Eva", rol = "ADMIN")
    private val pendiente = RegistroPendienteSsoDTO("google", "Google", "eva@gmail.test", "el-pase")

    private fun <T> rechazo(codigo: Int, detalle: String = "No.") = Response.error<T>(
        codigo,
        """{"status":$codigo,"detail":"$detalle"}""".toResponseBody("application/problem+json".toMediaType())
    )

    /** Deja el formulario como tras volver del navegador con una cuenta que no tiene cuenta de NX Time. */
    private suspend fun volverConLaCuenta() {
        whenever(repositorio.canjearRegistroSso(any(), any())).thenReturn(Response.success(pendiente))
        viewModel.registrarCon(google)
        sso.recibir(codigo = null, error = null, alta = "el-codigo")
    }

    private fun rellenar() {
        viewModel.onEmpresaCambia(" Talleres Eva SL ")
        viewModel.onNombreCambia(" Eva ")
        viewModel.onApellidosCambia("Martín ")
        viewModel.onAceptaCambia(true)
    }

    // ------------------------------------------------------------------
    // La ida
    // ------------------------------------------------------------------

    @Test
    fun `los proveedores que ofrece el servidor llegan a la pantalla`() = runTest {
        whenever(repositorio.getProveedoresSso()).thenReturn(Response.success(listOf(google)))

        viewModel
        advanceUntilIdle()

        assertEquals(listOf(google), viewModel.uiState.value.proveedoresSso)
    }

    @Test
    fun `registrar con un proveedor abre su URL diciendo que es la app que es para registrar y con el reto del verificador`() =
        runTest {
            viewModel.registrarCon(google)

            val verificador = guarda.verificador!!
            assertTrue(guarda.paraRegistrar)
            assertFalse(guarda.paraVincular)
            assertEquals(
                "${google.inicio}?cliente=app&registro=1&reto=${AccesoSso.retoDe(verificador)}",
                viewModel.uiState.value.abrirEnNavegador
            )

            viewModel.navegadorAbierto()
            assertNull(viewModel.uiState.value.abrirEnNavegador)
        }

    @Test
    fun `sin navegador se dice y la ida preparada se descarta`() = runTest {
        viewModel.registrarCon(google)

        viewModel.sinNavegador()

        assertNull(guarda.verificador)
        assertNull(viewModel.uiState.value.abrirEnNavegador)
        assertEquals(MensajeUi.Recurso(R.string.registro_sso_sin_navegador), viewModel.uiState.value.error)
    }

    // ------------------------------------------------------------------
    // La vuelta
    // ------------------------------------------------------------------

    @Test
    fun `al volver con un codigo de registro se cambia por el pase con el verificador guardado`() = runTest {
        whenever(repositorio.canjearRegistroSso(any(), any())).thenReturn(Response.success(pendiente))
        viewModel.registrarCon(google)
        val verificador = guarda.verificador!!

        sso.recibir(codigo = null, error = null, alta = "el-codigo")
        advanceUntilIdle()

        verify(repositorio).canjearRegistroSso("el-codigo", verificador)
        assertEquals(CuentaDelRegistro("Google", "eva@gmail.test", "el-pase"), viewModel.uiState.value.conCuenta)
        // Todavía no hay nada registrado ni sesión: falta el nombre de la empresa.
        assertFalse(viewModel.uiState.value.registrado)
        verify(repositorio, never()).procesarLoginExitoso(any())
        // Gastado: ese código y ese verificador no vuelven a servir.
        assertNull(guarda.verificador)
        assertFalse(guarda.paraRegistrar)
        assertNull(sso.vueltaDeRegistro.value)
    }

    /** Quien pulsa «Registrar con Google» y ya tiene cuenta ha demostrado lo mismo que entrando. */
    @Test
    fun `quien ya tenia cuenta vuelve con un codigo de entrar y entra`() = runTest {
        whenever(repositorio.canjearSso(any(), any())).thenReturn(Response.success(sesion))
        viewModel.registrarCon(google)
        val verificador = guarda.verificador!!

        sso.recibir(codigo = "de-entrar", error = null)
        advanceUntilIdle()

        verify(repositorio).canjearSso("de-entrar", verificador)
        verify(repositorio).procesarLoginExitoso(sesion)
        assertTrue(viewModel.uiState.value.registrado)
        verify(repositorio, never()).canjearRegistroSso(any(), any())
    }

    /** La vuelta de un registro no es del acceso: el login no la ve ni la canjea. */
    @Test
    fun `la vuelta de un registro no le llega a quien atiende las de entrar`() = runTest {
        viewModel.registrarCon(google)

        sso.recibir(codigo = "de-entrar", error = null, alta = "el-codigo")

        assertNull(sso.vuelta.value)
        assertNull(sso.vueltaDeVinculo.value)
        assertEquals("el-codigo", sso.vueltaDeRegistro.value?.alta)
    }

    @Test
    fun `si la vuelta trae un motivo se dice con palabras de registro y no se canjea nada`() = runTest {
        viewModel.registrarCon(google)

        sso.recibir(codigo = null, error = "correo-sin-verificar")
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.registro_sso_error_correo_sin_verificar),
            viewModel.uiState.value.error
        )
        assertNull(viewModel.uiState.value.conCuenta)
        assertNull(guarda.verificador)
        verify(repositorio, never()).canjearRegistroSso(any(), any())
        verify(repositorio, never()).canjearSso(any(), any())
    }

    @Test
    fun `un motivo que esta version no conoce cae en el generico`() {
        assertEquals(R.string.registro_sso_error_fallo, mensajeDeRegistroConSso("uno-nuevo"))
        assertEquals(R.string.registro_sso_error_cancelado, mensajeDeRegistroConSso("cancelado"))
    }

    @Test
    fun `si el servidor no acepta el codigo se dice y no hay cuenta con la que seguir`() = runTest {
        whenever(repositorio.canjearRegistroSso(any(), any())).thenReturn(rechazo(400))
        viewModel.registrarCon(google)

        sso.recibir(codigo = null, error = null, alta = "caducado")
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.registro_sso_error_fallo), viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.conCuenta)
        assertFalse(viewModel.uiState.value.cargando)
    }

    /** Un enlace nxtime://sso?alta=… que alguien mande no hace nada: no hay ida pendiente. */
    @Test
    fun `una vuelta que nadie ha pedido se ignora`() = runTest {
        viewModel
        sso.recibir(codigo = null, error = null, alta = "de-otro")
        advanceUntilIdle()

        assertNull(sso.vueltaDeRegistro.value)
        assertNull(viewModel.uiState.value.conCuenta)
        verify(repositorio, never()).canjearRegistroSso(any(), any())
    }

    /**
     * El sistema puede cerrar la app mientras la persona elige cuenta. Al
     * volver arranca de cero: lo guardado está en las preferencias y la vuelta
     * llega antes de que exista esta pantalla.
     */
    @Test
    fun `una vuelta que llega antes que la pantalla se atiende al abrirla`() = runTest {
        whenever(repositorio.canjearRegistroSso(any(), any())).thenReturn(Response.success(pendiente))
        guarda.verificador = "el-de-antes-de-morir"
        guarda.paraRegistrar = true
        sso.recibir(codigo = null, error = null, alta = "el-codigo")

        viewModel
        advanceUntilIdle()

        verify(repositorio).canjearRegistroSso("el-codigo", "el-de-antes-de-morir")
        assertNotNull(viewModel.uiState.value.conCuenta)
    }

    // ------------------------------------------------------------------
    // El formulario, ya con la cuenta
    // ------------------------------------------------------------------

    @Test
    fun `con la cuenta se registra sin correo ni contrasena mandando el pase y se entra`() = runTest {
        whenever(repositorio.registrarEmpresaConSso(any())).thenReturn(Response.success(sesion))
        volverConLaCuenta()
        advanceUntilIdle()
        rellenar()

        viewModel.registrar()
        advanceUntilIdle()

        verify(repositorio).registrarEmpresaConSso(PeticionRegistroSso("el-pase", "Talleres Eva SL", "Eva", "Martín", true))
        verify(repositorio).procesarLoginExitoso(sesion)
        assertTrue(viewModel.uiState.value.registrado)
        // Ni el registro con contraseña ni su código: esto es otra cosa.
        verify(repositorio, never()).registrarEmpresaGestor(any())
        assertNull(viewModel.uiState.value.pendienteDe)
    }

    @Test
    fun `con la cuenta siguen haciendo falta la empresa y el nombre`() = runTest {
        volverConLaCuenta()
        advanceUntilIdle()
        viewModel.onEmpresaCambia("Talleres Eva SL")

        viewModel.registrar()
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.error_campos_obligatorios), viewModel.uiState.value.error)
        verify(repositorio, never()).registrarEmpresaConSso(any())
    }

    /** El pase no se gasta con un 409: se puede probar con otro nombre sin volver al navegador. */
    @Test
    fun `si el nombre de la empresa esta cogido se lee el mensaje y la cuenta sigue ahi`() = runTest {
        whenever(repositorio.registrarEmpresaConSso(any()))
            .thenReturn(rechazo(409, "La empresa ya existe. Solicita acceso al administrador."))
        volverConLaCuenta()
        advanceUntilIdle()
        rellenar()

        viewModel.registrar()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Texto("La empresa ya existe. Solicita acceso al administrador."),
            viewModel.uiState.value.error
        )
        assertNotNull(viewModel.uiState.value.conCuenta)
        assertFalse(viewModel.uiState.value.registrado)
    }

    @Test
    fun `si el pase ha caducado se vuelve al formulario de siempre diciendolo`() = runTest {
        whenever(repositorio.registrarEmpresaConSso(any())).thenReturn(rechazo(404, "El registro ha caducado."))
        volverConLaCuenta()
        advanceUntilIdle()
        rellenar()

        viewModel.registrar()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.conCuenta)
        assertEquals(MensajeUi.Recurso(R.string.registro_sso_error_caducado), viewModel.uiState.value.error)
        // Lo escrito no se pierde.
        assertEquals(" Talleres Eva SL ", viewModel.uiState.value.empresa)
    }

    @Test
    fun `se puede dejar la cuenta y registrar con correo y contrasena`() = runTest {
        volverConLaCuenta()
        advanceUntilIdle()

        viewModel.dejarLaCuenta()

        assertNull(viewModel.uiState.value.conCuenta)
        rellenar()
        viewModel.registrar()
        advanceUntilIdle()
        // Vuelve a pedir lo de siempre: falta el correo y la contraseña.
        assertEquals(MensajeUi.Recurso(R.string.error_campos_obligatorios), viewModel.uiState.value.error)
        verify(repositorio, never()).registrarEmpresaConSso(any())
    }
}
