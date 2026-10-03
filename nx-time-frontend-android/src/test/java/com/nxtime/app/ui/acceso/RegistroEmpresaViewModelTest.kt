package com.nxtime.app.ui.acceso

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.RegistroPendienteDTO
import com.nxtime.app.data.dto.RespuestaAutenticacion
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

/** Registrar una empresa: desde la V37 del backend (ADR 034), sin sesión hasta confirmar el correo. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistroEmpresaViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val viewModel by lazy { RegistroEmpresaViewModel(repositorio) }

    private val sesion = RespuestaAutenticacion(token = "jwt", refreshToken = "refresh", nombre = "Eva", rol = "ADMIN")

    private fun rellenar() {
        viewModel.onEmpresaCambia("Talleres Eva SL")
        viewModel.onNombreCambia("Eva")
        viewModel.onApellidosCambia("Martín")
        viewModel.onEmailCambia(" eva@talleres.test ")
        viewModel.onContrasenaCambia("unaBuena123")
    }

    @Test
    fun `registrar no abre sesion pide el codigo y al canjearlo entra`() = runTest {
        whenever(repositorio.registrarEmpresaGestor(any()))
            .thenReturn(Response.success(202, RegistroPendienteDTO("eva@talleres.test", "Te hemos mandado un código")))
        whenever(repositorio.confirmarRegistro("eva@talleres.test", "123456")).thenReturn(Response.success(sesion))

        rellenar()
        viewModel.registrar()
        advanceUntilIdle()

        assertEquals("eva@talleres.test", viewModel.uiState.value.pendienteDe)
        assertFalse(viewModel.uiState.value.registrado)
        verify(repositorio, never()).procesarLoginExitoso(any())

        viewModel.onCodigoCambia("123456")
        viewModel.confirmar()
        advanceUntilIdle()

        verify(repositorio).procesarLoginExitoso(sesion)
        assertTrue(viewModel.uiState.value.registrado)
    }
}
