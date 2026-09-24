package com.nxtime.app.ui.firma

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.FirmaMensualDTO
import com.nxtime.app.data.dto.MesParaFirmarDTO
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Firmar el registro desde la app (Fase B3).
 *
 * Qué se puede firmar lo decide el servidor; aquí se comprueba que la app
 * manda el mes que toca, recarga después (el mes pasa a tener firma y huella,
 * y eso lo pone el servidor) y enseña el porqué cuando el servidor dice que no.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirmaMensualViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private val agosto = MesParaFirmarDTO(
        anio = 2026, mes = 8, jornadas = 21, segundosNetos = 21 * 8 * 3600L, puedeFirmar = true)

    private fun firma() = FirmaMensualDTO(
        id = 3, usuarioId = 10, usuario = "Ana", anio = 2026, mes = 8, estado = "VIGENTE",
        hash = "a".repeat(64), jornadas = 21, segundosNetos = 21 * 8 * 3600L, firmadaEn = "2026-09-02T08:00:00Z")

    private fun <T> error(codigo: Int, detalle: String = ""): Response<T> = Response.error(
        codigo, "{\"detail\":\"$detalle\"}".toResponseBody("application/problem+json".toMediaType()))

    @Test
    fun `carga los meses y los enseña`() = runTest {
        whenever(repositorio.getMisMesesParaFirmar()).thenReturn(Response.success(listOf(agosto)))

        val vm = FirmaMensualViewModel(repositorio)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.cargado)
        assertEquals(listOf(agosto), vm.uiState.value.meses)
    }

    @Test
    fun `firmar manda el mes y recarga`() = runTest {
        whenever(repositorio.getMisMesesParaFirmar()).thenReturn(Response.success(listOf(agosto)))
        whenever(repositorio.firmarMes(2026, 8)).thenReturn(Response.success(firma()))

        val vm = FirmaMensualViewModel(repositorio)
        advanceUntilIdle()
        vm.firmar(agosto)
        advanceUntilIdle()

        verify(repositorio).firmarMes(2026, 8)
        verify(repositorio, times(2)).getMisMesesParaFirmar()
        assertNotNull(vm.uiState.value.aviso)
        assertNull(vm.uiState.value.firmando)
    }

    @Test
    fun `si el servidor no deja firmar, se enseña su porqué y no se dice que se haya hecho`() = runTest {
        whenever(repositorio.getMisMesesParaFirmar()).thenReturn(Response.success(listOf(agosto)))
        whenever(repositorio.firmarMes(any(), any()))
            .thenReturn(error<FirmaMensualDTO>(422, "Queda una jornada sin cerrar en ese mes."))

        val vm = FirmaMensualViewModel(repositorio)
        advanceUntilIdle()
        vm.firmar(agosto)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.aviso)
        assertNull(vm.uiState.value.firmando)
    }

    @Test
    fun `si falla la carga no se da por cargado`() = runTest {
        whenever(repositorio.getMisMesesParaFirmar()).thenReturn(error<List<MesParaFirmarDTO>>(500))

        val vm = FirmaMensualViewModel(repositorio)
        advanceUntilIdle()

        // Sin esto, la pantalla diría "no hay nada que firmar" cuando lo que
        // pasa es que no se sabe.
        assertFalse(vm.uiState.value.cargado)
        assertNotNull(vm.uiState.value.error)
    }
}
