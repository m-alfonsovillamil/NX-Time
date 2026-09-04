package com.nxtime.app.ui.gestion

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.PanelEmpresaDTO
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * La ficha de empleado del panel de empresa.
 *
 * Lo que se prueba aquí, sobre todo, es que los valores imposibles se
 * paran en el móvil: el backend también los rechaza (espeja los CHECK de
 * la base), pero un 400 de ida y vuelta para decir que 61 horas no caben
 * en una semana es un viaje que sobra.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelEmpresaViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private val empleado = EmpleadoSimpleDTO(
        id = 10L,
        nombre = "Ana Fernández",
        email = "ana@nxtime.test",
        activo = true,
        horasSemanales = "40.0",
        diasVacaciones = 22
    )

    private suspend fun conPanelCargado() {
        whenever(repositorio.getPanelEmpresa()).thenReturn(
            Response.success(
                PanelEmpresaDTO(
                    empleadosActivos = 1,
                    minutosMesEmpresa = 9_600,
                    ausenciasPendientes = 0,
                    incidenciasAbiertas = 0
                )
            )
        )
        whenever(repositorio.getMisEmpleados()).thenReturn(Response.success(listOf(empleado)))
    }

    @Test
    fun `una jornada fuera de rango no sale a la red`() = runTest {
        conPanelCargado()
        val viewModel = PanelEmpresaViewModel(repositorio)
        advanceUntilIdle()

        var guardado = false
        viewModel.guardarFicha(10L, horas = "61", dias = "22") { guardado = true }
        advanceUntilIdle()

        assertFalse(guardado)
        assertEquals(
            MensajeUi.Recurso(R.string.empresa_ficha_horas_invalidas),
            viewModel.uiState.value.errorFicha
        )
        verify(repositorio, never()).configurarFichaEmpleado(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `una jornada que no es un numero tampoco`() = runTest {
        conPanelCargado()
        val viewModel = PanelEmpresaViewModel(repositorio)
        advanceUntilIdle()

        viewModel.guardarFicha(10L, horas = "ocho", dias = "22") {}
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.empresa_ficha_horas_invalidas),
            viewModel.uiState.value.errorFicha
        )
        verify(repositorio, never()).configurarFichaEmpleado(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `unos dias de vacaciones negativos se paran aqui`() = runTest {
        conPanelCargado()
        val viewModel = PanelEmpresaViewModel(repositorio)
        advanceUntilIdle()

        viewModel.guardarFicha(10L, horas = "40", dias = "-1") {}
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.empresa_ficha_dias_invalidos),
            viewModel.uiState.value.errorFicha
        )
        verify(repositorio, never()).configurarFichaEmpleado(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `la coma decimal se traduce a punto antes de enviarla`() = runTest {
        // En un teclado español lo natural es escribir "37,5", y el
        // backend espera un decimal con punto.
        conPanelCargado()
        whenever(repositorio.configurarFichaEmpleado(eq(10L), eq("37.5"), eq(25)))
            .thenReturn(Response.success(empleado))
        val viewModel = PanelEmpresaViewModel(repositorio)
        advanceUntilIdle()

        var guardado = false
        viewModel.guardarFicha(10L, horas = "37,5", dias = "25") { guardado = true }
        advanceUntilIdle()

        assertTrue(guardado)
        verify(repositorio).configurarFichaEmpleado(eq(10L), eq("37.5"), eq(25))
    }

    @Test
    fun `guardar bien recarga el panel`() = runTest {
        conPanelCargado()
        whenever(repositorio.configurarFichaEmpleado(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Response.success(empleado))
        val viewModel = PanelEmpresaViewModel(repositorio)
        advanceUntilIdle()

        viewModel.guardarFicha(10L, horas = "37.5", dias = "25") {}
        advanceUntilIdle()

        // Una en el init y otra al guardar: se recarga en vez de retocar
        // la lista en local, igual que hace el alta/baja.
        verify(repositorio, times(2)).getMisEmpleados()
        assertFalse(viewModel.uiState.value.guardandoFicha)
        assertEquals(null, viewModel.uiState.value.errorFicha)
    }

    @Test
    fun `si el servidor rechaza la ficha, el dialogo no se cierra`() = runTest {
        conPanelCargado()
        whenever(repositorio.configurarFichaEmpleado(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Response.error(403, okhttp3.ResponseBody.create(null, "")))
        val viewModel = PanelEmpresaViewModel(repositorio)
        advanceUntilIdle()

        var guardado = false
        viewModel.guardarFicha(10L, horas = "37.5", dias = "25") { guardado = true }
        advanceUntilIdle()

        assertFalse(guardado)
        assertEquals(
            MensajeUi.Recurso(R.string.error_sin_permisos),
            viewModel.uiState.value.errorFicha
        )
    }
}
