package com.nxtime.app.ui.historial

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.repository.AuthRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/** El historial por periodos: qué fechas se piden y qué total se enseña. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistorialViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    /* Un miércoles de mediados de mes, y un 1 de marzo tras febrero corto. */
    private val miercoles = LocalDate.of(2026, 9, 16)

    private fun registro(id: Long, entrada: String, salida: String?, pausa: Long = 0) =
        Registro(id = id, horaEntrada = entrada, horaSalida = salida, enPausa = false, segundosPausaAcumulados = pausa)

    @Test
    fun `cada periodo da los dos dias que tocan, con la semana empezando en lunes`() {
        assertNull(PeriodoHistorial.Recientes.rango(miercoles))
        assertEquals(LocalDate.of(2026, 9, 14) to LocalDate.of(2026, 9, 20), PeriodoHistorial.EstaSemana.rango(miercoles))
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), PeriodoHistorial.EsteMes.rango(miercoles))
        assertEquals(LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31), PeriodoHistorial.MesAnterior.rango(miercoles))
        // Un domingo sigue siendo de la semana que empezó el lunes anterior.
        assertEquals(
            LocalDate.of(2026, 9, 14) to LocalDate.of(2026, 9, 20),
            PeriodoHistorial.EstaSemana.rango(LocalDate.of(2026, 9, 20))
        )
        assertEquals(
            LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28),
            PeriodoHistorial.MesAnterior.rango(LocalDate.of(2026, 3, 1))
        )
    }

    @Test
    fun `por defecto pide los recientes, sin fechas`() = runTest {
        whenever(repositorio.getHistorial(anyOrNull(), anyOrNull())).thenReturn(Response.success(emptyList()))

        HistorialViewModel(repositorio) { miercoles }
        advanceUntilIdle()

        verify(repositorio).getHistorial(null, null)
    }

    @Test
    fun `al elegir un periodo pide esas fechas y suma solo las jornadas cerradas`() = runTest {
        whenever(repositorio.getHistorial(anyOrNull(), anyOrNull())).thenReturn(Response.success(emptyList()))
        whenever(repositorio.getHistorial(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(
            Response.success(
                listOf(
                    // 8 h con 30 min de pausa = 7 h 30 min.
                    registro(1, "2026-09-15T07:00:00Z", "2026-09-15T15:00:00Z", pausa = 1800),
                    registro(2, "2026-09-14T07:00:00Z", "2026-09-14T08:00:00Z"),
                    // La abierta no suma: cambia cada segundo.
                    registro(3, "2026-09-16T07:00:00Z", null)
                )
            )
        )
        val vm = HistorialViewModel(repositorio) { miercoles }
        advanceUntilIdle()

        vm.cambiarPeriodo(PeriodoHistorial.EsteMes)
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.registros.size)
        assertEquals(8L * 3600 + 30 * 60, vm.uiState.value.segundosNetos)
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), vm.uiState.value.rango)
    }

    @Test
    fun `al recargar se conserva el periodo elegido`() = runTest {
        whenever(repositorio.getHistorial(anyOrNull(), anyOrNull())).thenReturn(Response.success(emptyList()))
        val elegido = PeriodoHistorial.Elegido(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31))
        val vm = HistorialViewModel(repositorio) { miercoles }
        advanceUntilIdle()

        vm.cambiarPeriodo(elegido)
        vm.cargar()
        advanceUntilIdle()

        assertEquals(elegido, vm.uiState.value.periodo)
        org.mockito.kotlin.verify(repositorio, org.mockito.kotlin.times(2))
            .getHistorial(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31))
    }
}
