package com.nxtime.app.ui.acceso

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RecuperarAccesoViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val viewModel by lazy { RecuperarAccesoViewModel(repositorio) }

    private fun codigoNoValido(): Response<Unit> = Response.error(
        400,
        """{"status":400,"detail":"El código no es válido o ha caducado. Pide uno nuevo."}"""
            .toResponseBody("application/problem+json".toMediaType())
    )

    private fun enElPasoDelCodigo() {
        viewModel.onEmailCambia("ana@nxtime.com")
        viewModel.yaTengoCodigo()
    }

    @Test
    fun `sin correo no se pide ningun codigo`() = runTest {
        viewModel.pedirCodigo()
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.login_email_vacio), viewModel.uiState.value.error)
        verify(repositorio, never()).solicitarCodigoAcceso(any())
    }

    @Test
    fun `pedir un codigo pasa al paso del codigo avisando de que solo llega si hay cuenta`() = runTest {
        whenever(repositorio.solicitarCodigoAcceso(any())).thenReturn(Response.success(202, Unit))

        viewModel.onEmailCambia("  ana@nxtime.com ")
        viewModel.pedirCodigo()
        advanceUntilIdle()

        verify(repositorio).solicitarCodigoAcceso("ana@nxtime.com")
        val estado = viewModel.uiState.value
        assertEquals(PasoRecuperacion.CODIGO, estado.paso)
        assertTrue(estado.codigoPedido)
        assertFalse(estado.cargando)
    }

    @Test
    fun `ya tengo un codigo pasa al paso del codigo SIN pedir otro`() = runTest {
        enElPasoDelCodigo()
        advanceUntilIdle()

        // Pedir otro anularía el de bienvenida: solo vale el último.
        verify(repositorio, never()).solicitarCodigoAcceso(any())
        assertEquals(PasoRecuperacion.CODIGO, viewModel.uiState.value.paso)
        assertFalse(viewModel.uiState.value.codigoPedido)
    }

    @Test
    fun `el codigo solo admite seis digitos, y pegado con espacios tambien vale`() {
        viewModel.onCodigoCambia("12 34-56789")

        assertEquals("123456", viewModel.uiState.value.codigo)
    }

    @Test
    fun `con el codigo incompleto no se envia`() = runTest {
        enElPasoDelCodigo()
        viewModel.onCodigoCambia("123")
        viewModel.onNuevaCambia("nuevaSegura123")
        viewModel.onRepetidaCambia("nuevaSegura123")

        viewModel.guardar()
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.recuperar_codigo_incompleto), viewModel.uiState.value.error)
        verify(repositorio, never()).restablecerContrasena(any(), any(), any())
    }

    @Test
    fun `con las contrasenas distintas no se envia`() = runTest {
        enElPasoDelCodigo()
        viewModel.onCodigoCambia("123456")
        viewModel.onNuevaCambia("nuevaSegura123")
        viewModel.onRepetidaCambia("otraDistinta123")

        viewModel.guardar()
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.contrasena_no_coinciden), viewModel.uiState.value.error)
        verify(repositorio, never()).restablecerContrasena(any(), any(), any())
    }

    @Test
    fun `un codigo valido guarda la contrasena y termina`() = runTest {
        whenever(repositorio.restablecerContrasena(any(), any(), any())).thenReturn(Response.success(204, Unit))
        enElPasoDelCodigo()
        viewModel.onCodigoCambia("012345")
        viewModel.onNuevaCambia("nuevaSegura123")
        viewModel.onRepetidaCambia("nuevaSegura123")

        viewModel.guardar()
        advanceUntilIdle()

        verify(repositorio).restablecerContrasena("ana@nxtime.com", "012345", "nuevaSegura123")
        val estado = viewModel.uiState.value
        assertEquals(PasoRecuperacion.HECHO, estado.paso)
        // Las contraseñas no se quedan en memoria más de lo necesario.
        assertEquals("", estado.nueva)
        assertEquals("", estado.repetida)
    }

    @Test
    fun `un codigo que no vale muestra el mensaje del backend y vacia el codigo`() = runTest {
        whenever(repositorio.restablecerContrasena(any(), any(), any())).thenReturn(codigoNoValido())
        enElPasoDelCodigo()
        viewModel.onCodigoCambia("999999")
        viewModel.onNuevaCambia("nuevaSegura123")
        viewModel.onRepetidaCambia("nuevaSegura123")

        viewModel.guardar()
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertEquals(MensajeUi.Texto("El código no es válido o ha caducado. Pide uno nuevo."), estado.error)
        assertEquals(PasoRecuperacion.CODIGO, estado.paso)
        assertEquals("", estado.codigo)
    }

    @Test
    fun `sin red al pedir el codigo se dice que no hay conexion`() = runTest {
        whenever(repositorio.solicitarCodigoAcceso(any())).thenAnswer { throw IOException("sin red") }

        viewModel.onEmailCambia("ana@nxtime.com")
        viewModel.pedirCodigo()
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.error_sin_conexion), viewModel.uiState.value.error)
        assertEquals(PasoRecuperacion.CORREO, viewModel.uiState.value.paso)
    }
}
