package com.nxtime.app.ui.ausencias

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.PeticionAusenciaDTO
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.data.dto.UsuarioSimpleDTO
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.time.LocalDate

/**
 * El formulario de solicitud de ausencia.
 *
 * Estas validaciones vivían dentro de la Activity, mezcladas con la
 * lectura de los campos de la vista, así que solo podían comprobarse a
 * mano en un emulador.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SolicitudViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val viewModel by lazy { SolicitudViewModel(repositorio) }

    private val respuesta = RespuestaAusencia(
        id = 1,
        fechaInicio = "2026-09-01",
        fechaFin = "2026-09-05",
        tipo = TipoAusencia.VACACIONES,
        estado = EstadoAusencia.PENDIENTE,
        motivo = null,
        usuario = UsuarioSimpleDTO("Ana")
    )

    @Test
    fun `sin fechas no se envia nada`() = runTest {
        viewModel.enviar()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.solicitud_fechas_incompletas),
            viewModel.uiState.value.error
        )
        verify(repositorio, never()).solicitarAusencia(any())
    }

    @Test
    fun `con solo una de las dos fechas tampoco`() = runTest {
        viewModel.onFechaInicioCambia(LocalDate.of(2026, 9, 1))
        viewModel.enviar()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.solicitud_fechas_incompletas),
            viewModel.uiState.value.error
        )
        verify(repositorio, never()).solicitarAusencia(any())
    }

    @Test
    fun `la fecha de fin no puede ser anterior a la de inicio`() = runTest {
        viewModel.onFechaInicioCambia(LocalDate.of(2026, 9, 5))
        viewModel.onFechaFinCambia(LocalDate.of(2026, 9, 1))
        viewModel.enviar()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.solicitud_fecha_invertida),
            viewModel.uiState.value.error
        )
        verify(repositorio, never()).solicitarAusencia(any())
    }

    @Test
    fun `una ausencia de un solo dia es valida`() = runTest {
        whenever(repositorio.solicitarAusencia(any())).thenReturn(Response.success(respuesta))
        val dia = LocalDate.of(2026, 9, 1)

        viewModel.onFechaInicioCambia(dia)
        viewModel.onFechaFinCambia(dia)
        viewModel.enviar()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.enviada)
    }

    @Test
    fun `las fechas viajan como fecha de calendario ISO`() = runTest {
        whenever(repositorio.solicitarAusencia(any())).thenReturn(Response.success(respuesta))

        viewModel.onTipoCambia(TipoAusencia.MEDICO)
        viewModel.onFechaInicioCambia(LocalDate.of(2026, 9, 1))
        viewModel.onFechaFinCambia(LocalDate.of(2026, 9, 5))
        viewModel.onMotivoCambia("  revisión anual  ")
        viewModel.enviar()
        advanceUntilIdle()

        verify(repositorio).solicitarAusencia(
            PeticionAusenciaDTO(
                fechaInicio = "2026-09-01",
                fechaFin = "2026-09-05",
                tipo = TipoAusencia.MEDICO,
                motivo = "revisión anual"
            )
        )
    }

    @Test
    fun `un motivo en blanco viaja como nulo y no como cadena vacia`() = runTest {
        whenever(repositorio.solicitarAusencia(any())).thenReturn(Response.success(respuesta))

        viewModel.onFechaInicioCambia(LocalDate.of(2026, 9, 1))
        viewModel.onFechaFinCambia(LocalDate.of(2026, 9, 5))
        viewModel.onMotivoCambia("   ")
        viewModel.enviar()
        advanceUntilIdle()

        verify(repositorio).solicitarAusencia(
            PeticionAusenciaDTO(
                fechaInicio = "2026-09-01",
                fechaFin = "2026-09-05",
                tipo = TipoAusencia.VACACIONES,
                motivo = null
            )
        )
    }

    @Test
    fun `un rechazo del backend deja el formulario utilizable`() = runTest {
        whenever(repositorio.solicitarAusencia(any())).thenThrow(RuntimeException("sin red"))

        viewModel.onFechaInicioCambia(LocalDate.of(2026, 9, 1))
        viewModel.onFechaFinCambia(LocalDate.of(2026, 9, 5))
        viewModel.enviar()
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertFalse(estado.enviada)
        assertFalse(estado.cargando)
        assertEquals(MensajeUi.Recurso(R.string.error_sin_conexion), estado.error)
    }
}
