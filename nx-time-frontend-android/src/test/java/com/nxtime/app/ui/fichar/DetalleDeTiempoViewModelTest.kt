package com.nxtime.app.ui.fichar

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.HorasDelDiaDTO
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.data.dto.UsuarioSimpleDTO
import com.nxtime.app.data.repository.AuthRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/** La hoja de detalle de las tarjetas de inicio: qué días pide y qué enseña. */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleDeTiempoViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val miercoles = LocalDate.of(2026, 9, 16)

    private fun dia(fecha: String, trabajados: Long) = HorasDelDiaDTO(fecha, trabajados, 480)

    @Test
    fun `cada tarjeta pide su rango, con la semana de lunes a domingo`() {
        assertEquals(miercoles to miercoles, DetalleDeTiempoViewModel.rango(TipoDetalle.HOY, miercoles))
        assertEquals(
            LocalDate.of(2026, 9, 14) to LocalDate.of(2026, 9, 20),
            DetalleDeTiempoViewModel.rango(TipoDetalle.SEMANA, miercoles)
        )
        assertEquals(
            LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30),
            DetalleDeTiempoViewModel.rango(TipoDetalle.MES, miercoles)
        )
    }

    /* El servidor solo cuenta cerradas: la jornada abierta se suma a HOY y a ningún otro día. */
    @Test
    fun `la jornada en curso se suma solo al dia de hoy`() {
        val dias = listOf(dia("2026-09-15", 480), dia("2026-09-16", 120))

        val conCurso = DetalleDeTiempoViewModel.conJornadaEnCurso(dias, miercoles, 90)

        assertEquals(listOf(480L, 210L), conCurso.map { it.minutosTrabajados })
        assertEquals(dias, DetalleDeTiempoViewModel.conJornadaEnCurso(dias, miercoles, 0))
    }

    @Test
    fun `abrir la semana carga sus dias y cerrar limpia la hoja`() = runTest {
        whenever(repositorio.getHorasPorDia(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)))
            .thenReturn(Response.success(listOf(dia("2026-09-14", 480))))
        val vm = DetalleDeTiempoViewModel(repositorio) { miercoles }

        vm.abrir(TipoDetalle.SEMANA)
        advanceUntilIdle()

        assertEquals(TipoDetalle.SEMANA, vm.uiState.value.tipo)
        assertFalse(vm.uiState.value.cargando)
        assertEquals(1, vm.uiState.value.dias.size)

        vm.cerrar()
        assertNull(vm.uiState.value.tipo)
    }

    @Test
    fun `vacaciones enseña solo las aprobadas que no han terminado, la mas cercana primero`() = runTest {
        fun ausencia(id: Long, inicio: String, fin: String, estado: EstadoAusencia) =
            RespuestaAusencia(id, inicio, fin, TipoAusencia.VACACIONES, estado, null, UsuarioSimpleDTO("Ana"))
        whenever(repositorio.getMisPeticiones()).thenReturn(
            Response.success(
                listOf(
                    ausencia(1, "2026-12-21", "2026-12-31", EstadoAusencia.APROBADA),
                    ausencia(2, "2026-08-03", "2026-08-14", EstadoAusencia.APROBADA),
                    // Termina hoy: todavía cuenta.
                    ausencia(3, "2026-09-14", "2026-09-16", EstadoAusencia.APROBADA),
                    ausencia(4, "2026-10-12", "2026-10-13", EstadoAusencia.PENDIENTE)
                )
            )
        )
        val vm = DetalleDeTiempoViewModel(repositorio) { miercoles }

        vm.abrir(TipoDetalle.VACACIONES)
        advanceUntilIdle()

        assertEquals(listOf(3L, 1L), vm.uiState.value.proximasAusencias.map { it.id })
        verify(repositorio, never()).getHorasPorDia(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
