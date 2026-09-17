package com.nxtime.app.ui.fichar

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PausaAnadidaDTO
import com.nxtime.app.data.dto.PausaAnadidaRequest
import com.nxtime.app.data.dto.PausaAnadidaResultado
import com.nxtime.app.data.dto.Registro
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Añadir una pausa a posteriori (ADR 015).
 *
 * Qué pasa al guardar lo decide el servidor; lo que tiene lógica aquí es lo
 * de siempre con las horas —se recogen en hora española y viajan en UTC— y
 * que la pantalla diga la verdad sobre lo que ha pasado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnadirPausaViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun viewModel() = AnadirPausaViewModel(7L, repositorio)

    private fun registro() = Registro(
        id = 7, horaEntrada = "2026-09-03T06:00:00Z", horaSalida = null,
        enPausa = false, minutosPausaAcumulados = 60
    )

    /*
     * El 3 de septiembre España está en horario de verano (UTC+2): las 14:00
     * de la pantalla son las 12:00Z. Es el desfase que se colaría al componer
     * la cadena a mano, y que acabaría firmado en la traza.
     */
    @Test
    fun `las horas de la pausa se mandan en UTC, no en hora local`() = runTest {
        whenever(repositorio.anadirPausa(any(), any()))
            .thenReturn(Response.success(PausaAnadidaResultado(aplicada = true, fichaje = registro())))

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T06:00:00Z", null)
        viewModel.cambiarInicio(14, 0)
        viewModel.cambiarFin(15, 0)
        viewModel.cambiarMotivo("Olvidé fichar la comida")
        viewModel.guardar()
        advanceUntilIdle()

        val enviada = argumentCaptor<PausaAnadidaRequest>()
        verify(repositorio).anadirPausa(eq(7L), enviada.capture())
        assertEquals("2026-09-03T12:00:00Z", enviada.firstValue.inicio)
        assertEquals("2026-09-03T13:00:00Z", enviada.firstValue.fin)
    }

    @Test
    fun `sin motivo no se envia nada`() = runTest {
        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T06:00:00Z", null)
        viewModel.guardar()
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.pausa_motivo_vacio), viewModel.uiState.value.error)
        verify(repositorio, never()).anadirPausa(any(), any())
    }

    @Test
    fun `una pausa que acaba antes de empezar no sale a la red`() = runTest {
        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T06:00:00Z", null)
        viewModel.cambiarInicio(15, 0)
        viewModel.cambiarFin(14, 0)
        viewModel.cambiarMotivo("Comida")
        viewModel.guardar()
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.pausa_fin_anterior), viewModel.uiState.value.error)
        verify(repositorio, never()).anadirPausa(any(), any())
    }

    /*
     * La pantalla no se inventa lo que ha pasado: lo dice `aplicada`, que
     * pone el servidor. Una pausa de un día pasado queda PEDIDA, y decir
     * "añadida" haría pensar que el historial está roto cuando no cambie.
     */
    @Test
    fun `si el servidor la deja pedida, la pantalla no dice que se aplico`() = runTest {
        whenever(repositorio.anadirPausa(any(), any()))
            .thenReturn(Response.success(PausaAnadidaResultado(aplicada = false)))

        val viewModel = viewModel()
        viewModel.precargar("2026-06-01T07:00:00Z", "2026-06-01T15:00:00Z")
        viewModel.cambiarMotivo("Comida")
        viewModel.guardar()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hecho)
        assertFalse(viewModel.uiState.value.aplicada)
    }

    @Test
    fun `una pausa de un turno de noche puede acabar al dia siguiente`() = runTest {
        whenever(repositorio.anadirPausa(any(), any()))
            .thenReturn(Response.success(PausaAnadidaResultado(aplicada = true, fichaje = registro())))

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T20:00:00Z", null)
        viewModel.cambiarInicio(23, 30)
        viewModel.cambiarFin(0, 15)
        viewModel.cambiarFinEsOtroDia(true)
        viewModel.cambiarMotivo("Descanso")
        viewModel.guardar()
        advanceUntilIdle()

        val enviada = argumentCaptor<PausaAnadidaRequest>()
        verify(repositorio).anadirPausa(eq(7L), enviada.capture())
        assertEquals("2026-09-03T21:30:00Z", enviada.firstValue.inicio)
        assertEquals("2026-09-03T22:15:00Z", enviada.firstValue.fin)
    }

    @Test
    fun `una jornada abierta se anuncia como directa, una pasada como pendiente de aprobar`() {
        val abierta = viewModel().apply { precargar("2026-06-01T07:00:00Z", null) }
        assertTrue(abierta.uiState.value.vaDirecta)

        val pasada = viewModel().apply { precargar("2026-06-01T07:00:00Z", "2026-06-01T15:00:00Z") }
        assertFalse(pasada.uiState.value.vaDirecta)
    }

    @Test
    fun `al deshacer una pausa se vuelve a pedir la lista`() = runTest {
        val pausa = PausaAnadidaDTO(
            id = 3, inicio = "2026-09-03T12:00:00Z", fin = "2026-09-03T13:00:00Z",
            minutos = 60, motivo = "Comida"
        )
        whenever(repositorio.getPausasAnadidas(7L))
            .thenReturn(Response.success(listOf(pausa)))
            .thenReturn(Response.success(emptyList()))
        whenever(repositorio.deshacerPausa(7L, 3L)).thenReturn(Response.success(registro()))

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T06:00:00Z", null)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.pausas.size)

        viewModel.deshacer(3L)
        advanceUntilIdle()

        verify(repositorio, times(2)).getPausasAnadidas(7L)
        assertTrue(viewModel.uiState.value.pausas.isEmpty())
    }
}
