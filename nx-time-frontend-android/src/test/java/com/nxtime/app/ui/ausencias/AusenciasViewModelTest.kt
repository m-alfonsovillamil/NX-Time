package com.nxtime.app.ui.ausencias

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.data.dto.UsuarioSimpleDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.unaPagina
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Cancelar una ausencia propia (ADR 058): se manda, se recarga para ver el
 * estado nuevo, y si el servidor dice que no, la lista sigue a la vista.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AusenciasViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private val pendiente = RespuestaAusencia(
        id = 3,
        fechaInicio = "2026-12-01",
        fechaFin = "2026-12-02",
        tipo = TipoAusencia.ASUNTOS_PROPIOS,
        estado = EstadoAusencia.PENDIENTE,
        motivo = null,
        usuario = UsuarioSimpleDTO("Ana Fernández Gil"),
        puedoCancelar = true
    )

    private suspend fun conLista(): AusenciasViewModel {
        whenever(repositorio.getMisPeticiones(anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(unaPagina(listOf(pendiente)))
        return AusenciasViewModel(repositorio)
    }

    @Test
    fun `cancelar manda el motivo y recarga la lista`() = runTest {
        val viewModel = conLista()
        advanceUntilIdle()
        whenever(repositorio.cancelarAusencia(any(), anyOrNull()))
            .thenReturn(Response.success(pendiente.copy(estado = EstadoAusencia.CANCELADA, puedoCancelar = false)))

        viewModel.cancelar(3, "Cambio de planes")
        advanceUntilIdle()

        verify(repositorio).cancelarAusencia(eq(3L), eq("Cambio de planes"))
        verify(repositorio, times(2)).getMisPeticiones(anyOrNull(), anyOrNull(), any(), any())
        assertNull(viewModel.uiState.value.cancelando)
    }

    @Test
    fun `si el servidor no deja, se dice sin quitar la lista`() = runTest {
        val viewModel = conLista()
        advanceUntilIdle()
        whenever(repositorio.cancelarAusencia(any(), anyOrNull())).thenReturn(
            Response.error(
                409,
                """{"detail":"Esta ausencia ya ha empezado: pide a quien aprueba las ausencias que la anule."}"""
                    .toResponseBody(null)
            )
        )

        viewModel.cancelar(3, null)
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertNotNull(estado.errorAlCancelar)
        assertNull(estado.error)
        assertEquals(listOf(pendiente), estado.peticiones)

        viewModel.descartarErrorAlCancelar()
        assertNull(viewModel.uiState.value.errorAlCancelar)
    }
}
