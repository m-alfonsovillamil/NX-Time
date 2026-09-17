package com.nxtime.app.ui.reparto

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.ImputacionesDTO
import com.nxtime.app.data.dto.LineaImputacion
import com.nxtime.app.data.dto.LineaReparto
import com.nxtime.app.data.dto.ProyectoParaFichar
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
 * Repartir las horas de una jornada entre proyectos (ADR 017).
 *
 * Lo que importa aquí es lo que la pantalla dice ANTES de pulsar: si el
 * reparto se va a aplicar o lo va a tener que aprobar alguien, y que no deje
 * enviar lo que el servidor va a rechazar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepartoViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun imputaciones(
        repartoLibre: Boolean = true,
        neto: Long = 480,
        solicitud: Long? = null
    ) = ImputacionesDTO(
        fichajeId = 7,
        netoMinutos = neto,
        repartoLibre = repartoLibre,
        lineas = listOf(LineaImputacion(1, "CORE", "Plataforma", neto)),
        disponibles = listOf(ProyectoParaFichar(1, "CORE", "Plataforma"), ProyectoParaFichar(2, "APP", "Móvil")),
        solicitudPendienteId = solicitud
    )

    private val vacio = "".toResponseBody("application/json".toMediaType())

    private suspend fun viewModel(dto: ImputacionesDTO): RepartoViewModel {
        whenever(repositorio.getImputaciones(7)).thenReturn(Response.success(dto))
        return RepartoViewModel(7, repositorio)
    }

    @Test
    fun `arranca con lo que ya estaba imputado`() = runTest {
        val vm = viewModel(imputaciones())
        advanceUntilIdle()

        assertEquals(mapOf(1L to 480L), vm.uiState.value.minutos)
        assertEquals(480, vm.uiState.value.totalMinutos)
        assertFalse(vm.uiState.value.necesitaAprobacion)
        assertTrue(vm.uiState.value.puedeEnviar)
    }

    @Test
    fun `repartir de esta semana sumando el neto se aplica al momento`() = runTest {
        val vm = viewModel(imputaciones())
        advanceUntilIdle()
        whenever(repositorio.repartir(eq(7L), any(), anyOrNull())).thenReturn(Response.success(vacio))

        vm.cambiarMinutos(1, 240)
        vm.cambiarMinutos(2, 240)
        vm.enviar()
        advanceUntilIdle()

        verify(repositorio).repartir(7, listOf(LineaReparto(1, 240), LineaReparto(2, 240)), null)
        assertTrue(vm.uiState.value.aplicado)
        assertFalse(vm.uiState.value.pedido)
    }

    /* Un 202 significa que hay que esperar a un gestor: no es lo mismo que "hecho". */
    @Test
    fun `un 202 se entiende como pendiente de aprobar, no como aplicado`() = runTest {
        val vm = viewModel(imputaciones(repartoLibre = false))
        advanceUntilIdle()
        whenever(repositorio.repartir(eq(7L), any(), anyOrNull()))
            .thenReturn(Response.success(202, vacio))

        vm.cambiarMinutos(1, 240)
        vm.cambiarMinutos(2, 240)
        vm.cambiarMotivo("Estuve en los dos")
        vm.enviar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pedido)
        assertFalse(vm.uiState.value.aplicado)
    }

    @Test
    fun `no deja enviar lo que el servidor va a rechazar`() = runTest {
        val vm = viewModel(imputaciones())
        advanceUntilIdle()

        // Menos horas de las trabajadas.
        vm.cambiarMinutos(1, 100)
        assertFalse(vm.uiState.value.puedeEnviar)
        // Todo a cero.
        vm.cambiarMinutos(1, 0)
        assertFalse(vm.uiState.value.puedeEnviar)

        // Más horas de las fichadas: se puede, pero exige motivo.
        vm.cambiarMinutos(1, 480)
        vm.cambiarMinutos(2, 60)
        assertTrue(vm.uiState.value.necesitaAprobacion)
        assertFalse(vm.uiState.value.puedeEnviar)
        vm.cambiarMotivo("Seguí trabajando sin fichar")
        assertTrue(vm.uiState.value.puedeEnviar)

        vm.enviar()
        advanceUntilIdle()
        verify(repositorio, never()).repartir(eq(7L), any(), eq(null))
    }

    @Test
    fun `con una correccion viva no se puede repartir`() = runTest {
        val vm = viewModel(imputaciones(solicitud = 42))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.puedeEnviar)
        assertEquals(42L, vm.uiState.value.imputaciones?.solicitudPendienteId)
    }

    @Test
    fun `todo a un proyecto pone el neto entero ahi`() = runTest {
        val vm = viewModel(imputaciones())
        advanceUntilIdle()

        vm.todoA(2)

        assertEquals(mapOf(2L to 480L), vm.uiState.value.minutos)
        assertNotNull(vm.uiState.value.imputaciones)
    }
}
