package com.nxtime.app.ui.ausencias

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.data.dto.UsuarioSimpleDTO
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

/** El filtro de "Mis ausencias": qué deja pasar, y que sobrevive a recargar. */
@OptIn(ExperimentalCoroutinesApi::class)
class FiltroAusenciasTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private fun ausencia(
        id: Long,
        inicio: String,
        fin: String = inicio,
        tipo: TipoAusencia = TipoAusencia.VACACIONES,
        estado: EstadoAusencia = EstadoAusencia.APROBADA
    ) = RespuestaAusencia(id, inicio, fin, tipo, estado, null, UsuarioSimpleDTO("Ana"))

    private val lista = listOf(
        ausencia(1, "2026-08-03", "2026-08-14"),
        ausencia(2, "2026-09-20", tipo = TipoAusencia.MEDICO, estado = EstadoAusencia.PENDIENTE),
        ausencia(3, "2025-12-29", "2026-01-02", estado = EstadoAusencia.RECHAZADA),
        ausencia(4, "2025-03-10", tipo = TipoAusencia.ASUNTOS_PROPIOS)
    )

    private fun ids(filtro: FiltroAusencias) = FiltroAusencias.filtrar(lista, filtro).map { it.id }

    @Test
    fun `sin filtro pasan todas`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(FiltroAusencias()))
        assertFalse(FiltroAusencias().activo)
    }

    @Test
    fun `por estado y por tipo`() {
        assertEquals(listOf(2L), ids(FiltroAusencias(estado = EstadoAusencia.PENDIENTE)))
        assertEquals(listOf(4L), ids(FiltroAusencias(tipo = TipoAusencia.ASUNTOS_PROPIOS)))
    }

    /* Unas vacaciones de fin de año tienen que salir en los dos años que tocan. */
    @Test
    fun `una ausencia que cruza de ano sale en los dos`() {
        assertEquals(listOf(1L, 2L, 3L), ids(FiltroAusencias(anio = 2026)))
        assertEquals(listOf(3L, 4L), ids(FiltroAusencias(anio = 2025)))
    }

    @Test
    fun `los filtros se combinan`() {
        assertEquals(listOf(1L), ids(FiltroAusencias(anio = 2026, estado = EstadoAusencia.APROBADA)))
        assertEquals(emptyList<Long>(), ids(FiltroAusencias(anio = 2025, tipo = TipoAusencia.MEDICO)))
    }

    @Test
    fun `solo se ofrecen los anos y tipos que aparecen`() {
        assertEquals(listOf(2026, 2025), FiltroAusencias.aniosDe(lista))
        assertEquals(
            listOf(TipoAusencia.VACACIONES, TipoAusencia.ASUNTOS_PROPIOS, TipoAusencia.MEDICO),
            FiltroAusencias.tiposDe(lista)
        )
    }

    @Test
    fun `el filtro se conserva al recargar y se puede quitar`() = runTest {
        val repositorio: AuthRepository = mock()
        whenever(repositorio.getMisPeticiones()).thenReturn(Response.success(lista))
        val vm = AusenciasViewModel(repositorio)
        advanceUntilIdle()

        vm.cambiarFiltro(FiltroAusencias(anio = 2025))
        vm.cargar()
        advanceUntilIdle()

        assertEquals(listOf(3L, 4L), vm.uiState.value.visibles.map { it.id })
        assertTrue(vm.uiState.value.filtro.activo)

        vm.quitarFiltros()
        assertEquals(4, vm.uiState.value.visibles.size)
    }
}
