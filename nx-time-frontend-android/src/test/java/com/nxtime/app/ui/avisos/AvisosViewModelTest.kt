package com.nxtime.app.ui.avisos

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.AvisoDTO
import com.nxtime.app.data.dto.ContadorAvisosDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AvisosViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun aviso(id: Long, leido: Boolean) = AvisoDTO(
        id = id,
        tipo = "AUSENCIA_RESUELTA",
        titulo = "Tu ausencia ha sido aprobada",
        cuerpo = "VACACIONES, del 2026-06-01 al 2026-06-05",
        rutaDestino = "ausencias",
        leido = leido,
        creadoEn = "2026-06-01T08:00:00Z"
    )

    @Test
    fun `al construirse no pide nada, porque tambien existe en el login`() = runTest {
        AvisosViewModel(repositorio)
        advanceUntilIdle()

        // Es la razón de que este ViewModel no tenga init { cargar() }:
        // NxTimeNavHost lo crea antes de que haya sesión.
        verify(repositorio, never()).getAvisos()
        verify(repositorio, never()).getContadorAvisos()
    }

    @Test
    fun `cargar trae la lista y cuenta los no leidos`() = runTest {
        whenever(repositorio.getAvisos()).thenReturn(
            Response.success(listOf(aviso(1L, leido = false), aviso(2L, leido = true)))
        )
        val viewModel = AvisosViewModel(repositorio).also { it.cargar() }
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.avisos.size)
        assertEquals(1, viewModel.uiState.value.noLeidos)
        assertFalse(viewModel.uiState.value.cargando)
    }

    @Test
    fun `refrescarContador no descarga la lista entera`() = runTest {
        whenever(repositorio.getContadorAvisos()).thenReturn(Response.success(ContadorAvisosDTO(3)))
        val viewModel = AvisosViewModel(repositorio).also { it.refrescarContador() }
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.noLeidos)
        verify(repositorio, never()).getAvisos()
    }

    @Test
    fun `si el contador falla no se le enseña un error a nadie`() = runTest {
        // La campana es un adorno hasta que se toca: un banner rojo en
        // "Mi jornada" por no poder contar avisos molestaría más de lo
        // que informa.
        whenever(repositorio.getContadorAvisos()).thenThrow(RuntimeException("sin red"))
        val viewModel = AvisosViewModel(repositorio).also { it.refrescarContador() }
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.noLeidos)
    }

    @Test
    fun `un fallo de red al cargar la lista si se cuenta`() = runTest {
        whenever(repositorio.getAvisos()).thenThrow(RuntimeException("sin red"))
        val viewModel = AvisosViewModel(repositorio).also { it.cargar() }
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.error_sin_conexion),
            viewModel.uiState.value.error
        )
    }

    @Test
    fun `marcar leido baja el contador y voltea el aviso sin recargar`() = runTest {
        whenever(repositorio.getAvisos()).thenReturn(
            Response.success(listOf(aviso(1L, leido = false), aviso(2L, leido = false)))
        )
        whenever(repositorio.marcarAvisoLeido(1L)).thenReturn(Response.success(Unit))
        val viewModel = AvisosViewModel(repositorio).also { it.cargar() }
        advanceUntilIdle()

        viewModel.marcarLeido(1L)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.noLeidos)
        assertTrue(viewModel.uiState.value.avisos.first { it.id == 1L }.leido)
        assertFalse(viewModel.uiState.value.avisos.first { it.id == 2L }.leido)
        // Nada de volver a pedir la lista: saltaría bajo el dedo que
        // acaba de tocarla.
        verify(repositorio, org.mockito.kotlin.times(1)).getAvisos()
    }

    @Test
    fun `marcar leido dos veces no vuelve a llamar al servidor`() = runTest {
        whenever(repositorio.getAvisos()).thenReturn(
            Response.success(listOf(aviso(1L, leido = false)))
        )
        whenever(repositorio.marcarAvisoLeido(1L)).thenReturn(Response.success(Unit))
        val viewModel = AvisosViewModel(repositorio).also { it.cargar() }
        advanceUntilIdle()

        viewModel.marcarLeido(1L)
        viewModel.marcarLeido(1L)
        advanceUntilIdle()

        verify(repositorio, org.mockito.kotlin.times(1)).marcarAvisoLeido(1L)
        assertEquals(0, viewModel.uiState.value.noLeidos)
    }

    @Test
    fun `marcar todos deja el contador a cero`() = runTest {
        whenever(repositorio.getAvisos()).thenReturn(
            Response.success(listOf(aviso(1L, leido = false), aviso(2L, leido = false)))
        )
        whenever(repositorio.marcarTodosLosAvisosLeidos()).thenReturn(Response.success(Unit))
        val viewModel = AvisosViewModel(repositorio).also { it.cargar() }
        advanceUntilIdle()

        viewModel.marcarTodosLeidos()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.noLeidos)
        assertTrue(viewModel.uiState.value.avisos.all { it.leido })
    }

    @Test
    fun `limpiar borra el estado al cerrar sesion`() = runTest {
        whenever(repositorio.getAvisos()).thenReturn(
            Response.success(listOf(aviso(1L, leido = false)))
        )
        val viewModel = AvisosViewModel(repositorio).also { it.cargar() }
        advanceUntilIdle()

        viewModel.limpiar()

        // El ViewModel vive en la Activity y sobrevive al cambio de
        // usuario: sin esto, quien entrase después vería el contador del
        // anterior.
        assertEquals(0, viewModel.uiState.value.noLeidos)
        assertTrue(viewModel.uiState.value.avisos.isEmpty())
    }
}
