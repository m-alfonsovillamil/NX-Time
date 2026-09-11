package com.nxtime.app.ui.gestion

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.CrearEmpleadoRequest
import com.nxtime.app.data.dto.CrearGestorRequest
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

/**
 * Alta de empleados y gestores. Desde el ADR 014 no lleva contraseña: lo
 * que se fija aquí es que no se manda ninguna y que el 503 de "no se ha
 * podido enviar el correo" llega a la pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AltaUsuarioViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val viewModel by lazy { AltaUsuarioViewModel(repositorio) }

    @Test
    fun `sin nombre o sin correo no se envia nada`() = runTest {
        viewModel.onNombreCambia("Ana")
        viewModel.crear(esGestor = false)
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.error_campos_obligatorios), viewModel.uiState.value.error)
        verify(repositorio, never()).crearEmpleado(any())
    }

    @Test
    fun `un empleado se crea solo con nombre y correo, sin contrasena`() = runTest {
        whenever(repositorio.crearEmpleado(any())).thenReturn(Response.success(Unit))

        viewModel.onNombreCambia(" Ana ")
        viewModel.onEmailCambia(" ana@nxtime.com ")
        viewModel.crear(esGestor = false)
        advanceUntilIdle()

        verify(repositorio).crearEmpleado(CrearEmpleadoRequest("Ana", "ana@nxtime.com"))
        assertTrue(viewModel.uiState.value.creado)
    }

    @Test
    fun `un gestor va a su propio endpoint`() = runTest {
        whenever(repositorio.crearGestor(any())).thenReturn(Response.success(Unit))

        viewModel.onNombreCambia("Marta")
        viewModel.onEmailCambia("marta@nxtime.com")
        viewModel.crear(esGestor = true)
        advanceUntilIdle()

        verify(repositorio).crearGestor(CrearGestorRequest("Marta", "marta@nxtime.com"))
        verify(repositorio, never()).crearEmpleado(any())
    }

    @Test
    fun `si el correo del codigo no sale, el alta no se da por hecha y se dice por que`() = runTest {
        whenever(repositorio.crearEmpleado(any())).thenReturn(
            Response.error(
                503,
                """{"status":503,"detail":"No se ha podido enviar el correo con el código de acceso, así que la cuenta no se ha creado."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )

        viewModel.onNombreCambia("Ana")
        viewModel.onEmailCambia("ana@nxtime.com")
        viewModel.crear(esGestor = false)
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertFalse(estado.creado)
        assertEquals(
            MensajeUi.Texto("No se ha podido enviar el correo con el código de acceso, así que la cuenta no se ha creado."),
            estado.error
        )
    }
}
