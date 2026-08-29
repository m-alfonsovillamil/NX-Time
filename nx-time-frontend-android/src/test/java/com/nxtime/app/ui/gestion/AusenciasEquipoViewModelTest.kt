package com.nxtime.app.ui.gestion

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.data.dto.UsuarioSimpleDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Las ausencias del equipo desde el lado del gestor.
 *
 * El caso que más importa es el rechazo sin motivo: el backend responde
 * 400 desde la Fase 9, así que la app tiene que pararlo antes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AusenciasEquipoViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private val pendiente = RespuestaAusencia(
        id = 7,
        fechaInicio = "2026-09-01",
        fechaFin = "2026-09-05",
        tipo = TipoAusencia.VACACIONES,
        estado = EstadoAusencia.PENDIENTE,
        motivo = "playa",
        usuario = UsuarioSimpleDTO("Ana")
    )

    private suspend fun viewModelPendientes(): AusenciasEquipoViewModel {
        whenever(repositorio.getPeticionesPendientes())
            .thenReturn(Response.success(listOf(pendiente)))
        return AusenciasEquipoViewModel(repositorio).also { it.mostrar(resueltas = false) }
    }

    @Test
    fun `las pendientes se piden al endpoint de pendientes`() = runTest {
        val viewModel = viewModelPendientes()
        advanceUntilIdle()

        verify(repositorio).getPeticionesPendientes()
        verify(repositorio, never()).getHistorialAusencias()
        assertEquals(listOf(pendiente), viewModel.uiState.value.peticiones)
    }

    @Test
    fun `las resueltas se piden al historial`() = runTest {
        whenever(repositorio.getHistorialAusencias()).thenReturn(Response.success(emptyList()))

        AusenciasEquipoViewModel(repositorio).mostrar(resueltas = true)
        advanceUntilIdle()

        verify(repositorio).getHistorialAusencias()
        verify(repositorio, never()).getPeticionesPendientes()
    }

    @Test
    fun `aprobar no manda comentario`() = runTest {
        val viewModel = viewModelPendientes()
        advanceUntilIdle()
        whenever(repositorio.cambiarEstadoPeticion(any(), any(), anyOrNull()))
            .thenReturn(Response.success(pendiente.copy(estado = EstadoAusencia.APROBADA)))

        viewModel.aprobar(7)
        advanceUntilIdle()

        verify(repositorio).cambiarEstadoPeticion(eq(7L), eq(EstadoAusencia.APROBADA), eq(null))
    }

    @Test
    fun `rechazar sin motivo no llega al servidor`() = runTest {
        val viewModel = viewModelPendientes()
        advanceUntilIdle()

        viewModel.rechazar(7, "   ")
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.pendientes_motivo_obligatorio),
            viewModel.uiState.value.error
        )
        verify(repositorio, never()).cambiarEstadoPeticion(any(), any(), anyOrNull())
    }

    @Test
    fun `rechazar con motivo lo manda sin espacios de sobra`() = runTest {
        val viewModel = viewModelPendientes()
        advanceUntilIdle()
        whenever(repositorio.cambiarEstadoPeticion(any(), any(), anyOrNull()))
            .thenReturn(Response.success(pendiente.copy(estado = EstadoAusencia.RECHAZADA)))

        viewModel.rechazar(7, "  Coincide con el cierre trimestral  ")
        advanceUntilIdle()

        verify(repositorio).cambiarEstadoPeticion(
            eq(7L),
            eq(EstadoAusencia.RECHAZADA),
            eq("Coincide con el cierre trimestral")
        )
    }

    /**
     * Tras resolver se vuelve a pedir la lista en vez de tocarla en
     * memoria: el backend devuelve además quién resolvió y cuándo, y
     * esos datos no están en la app.
     */
    @Test
    fun `resolver recarga la lista`() = runTest {
        val viewModel = viewModelPendientes()
        advanceUntilIdle()
        whenever(repositorio.cambiarEstadoPeticion(any(), any(), anyOrNull()))
            .thenReturn(Response.success(pendiente.copy(estado = EstadoAusencia.APROBADA)))

        viewModel.aprobar(7)
        advanceUntilIdle()

        // Una al construir y mostrar, otra tras aprobar.
        verify(repositorio, org.mockito.kotlin.times(2)).getPeticionesPendientes()
        assertEquals(null, viewModel.uiState.value.resolviendo)
    }
}
