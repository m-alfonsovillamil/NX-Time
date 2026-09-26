package com.nxtime.app.ui.cuadrante

import com.nxtime.app.data.dto.PaginaDTO
import com.nxtime.app.unaPagina
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.IncidenciaDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Incidencias de cuadrante vistas desde la app (Fase B2).
 *
 * Qué es una incidencia lo decide el barrido del servidor; aquí se comprueba
 * lo que es de la app: que el rol decide qué se pide (y no qué se permite),
 * que un fallo en cualquiera de las dos listas se enseña, y que cada acción
 * manda lo que el usuario escribió y recarga.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidenciasViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val sesion: SessionManager = mock()

    private fun incidencia(id: Long, estado: String = "PENDIENTE") = IncidenciaDTO(
        id = id,
        usuarioId = 10L,
        usuario = "Ana",
        fecha = "2026-10-05",
        tipo = "RETRASO",
        minutos = 25,
        horaPrevista = "09:00",
        horaReal = "2026-10-05T07:25:00Z",
        estado = estado
    )

    private fun <T> error(codigo: Int): Response<T> = Response.error(
        codigo, "{}".toResponseBody("application/json".toMediaType()))

    private suspend fun conRespuestasNormales() {
        whenever(repositorio.getMisIncidencias(anyOrNull())).thenReturn(Response.success(listOf(incidencia(1))))
        whenever(repositorio.getIncidenciasDelEquipo(any(), any()))
            .thenReturn(unaPagina(listOf(incidencia(2), incidencia(3, "JUSTIFICADA"))))
    }

    @Test
    fun `un empleado no pide la bandeja del equipo`() = runTest {
        whenever(sesion.fetchAuthorities()).thenReturn(EMPLEADO)
        conRespuestasNormales()

        val vm = IncidenciasViewModel(repositorio, sesion)
        advanceUntilIdle()

        verify(repositorio, never()).getIncidenciasDelEquipo(any(), any())
        assertFalse(vm.uiState.value.puedeRevisar)
        assertEquals(1, vm.uiState.value.mias.size)
        assertTrue(vm.uiState.value.delEquipo.isEmpty())
    }

    @Test
    fun `quien revisa ve las suyas y las del equipo`() = runTest {
        whenever(sesion.fetchAuthorities()).thenReturn(GESTOR)
        conRespuestasNormales()

        val vm = IncidenciasViewModel(repositorio, sesion)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.puedeRevisar)
        // Por defecto, las que esperan decisión: no las resueltas.
        verify(repositorio).getIncidenciasDelEquipo(false, 0)
        assertEquals(2, vm.uiState.value.delEquipo.size)
        assertFalse(vm.uiState.value.cargando)
    }

    @Test
    fun `si falla la bandeja se enseña el error, no una bandeja vacia`() = runTest {
        // Una bandeja vacía diría "no hay nada que decidir", que sería mentira.
        whenever(sesion.fetchAuthorities()).thenReturn(GESTOR)
        whenever(repositorio.getMisIncidencias(anyOrNull())).thenReturn(Response.success(emptyList()))
        whenever(repositorio.getIncidenciasDelEquipo(any(), any())).thenReturn(error<PaginaDTO<IncidenciaDTO>>(500))

        val vm = IncidenciasViewModel(repositorio, sesion)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.cargando)
    }

    @Test
    fun `explicar manda el texto sin espacios de mas y recarga`() = runTest {
        whenever(sesion.fetchAuthorities()).thenReturn(EMPLEADO)
        conRespuestasNormales()
        whenever(repositorio.justificarIncidencia(any(), any()))
            .thenReturn(Response.success(incidencia(1, "JUSTIFICADA")))

        val vm = IncidenciasViewModel(repositorio, sesion)
        advanceUntilIdle()
        vm.justificar(1L, "  Avería del metro  ")
        advanceUntilIdle()

        verify(repositorio).justificarIncidencia(1L, "Avería del metro")
        verify(repositorio, times(2)).getMisIncidencias(anyOrNull())
        assertNotNull(vm.uiState.value.aviso)
    }

    @Test
    fun `aceptar va sin comentario y rechazar con el suyo`() = runTest {
        whenever(sesion.fetchAuthorities()).thenReturn(GESTOR)
        conRespuestasNormales()
        whenever(repositorio.resolverIncidencia(any(), any(), anyOrNull()))
            .thenReturn(Response.success(incidencia(2, "ACEPTADA")))

        val vm = IncidenciasViewModel(repositorio, sesion)
        advanceUntilIdle()
        vm.aceptar(2L)
        vm.rechazar(3L, "Tercera vez esta semana")
        advanceUntilIdle()

        verify(repositorio).resolverIncidencia(2L, true, null)
        verify(repositorio).resolverIncidencia(3L, false, "Tercera vez esta semana")
    }

    @Test
    fun `un fallo al decidir deja el error y no dice que se haya hecho`() = runTest {
        // El 403 de decidir sobre una propia, o el 409 de una ya decidida.
        whenever(sesion.fetchAuthorities()).thenReturn(GESTOR)
        conRespuestasNormales()
        whenever(repositorio.resolverIncidencia(any(), any(), anyOrNull()))
            .thenReturn(error<IncidenciaDTO>(409))

        val vm = IncidenciasViewModel(repositorio, sesion)
        advanceUntilIdle()
        vm.aceptar(2L)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.aviso)
    }

    @Test
    fun `el estado de una incidencia dice si espera decision`() {
        assertTrue(EstadoIncidencia.de("PENDIENTE")!!.esperaDecision)
        assertTrue(EstadoIncidencia.de("JUSTIFICADA")!!.esperaDecision)
        assertFalse(EstadoIncidencia.de("ACEPTADA")!!.esperaDecision)
        assertFalse(EstadoIncidencia.de("RECHAZADA")!!.esperaDecision)
        // Un valor que esta versión no conoce no se inventa.
        assertNull(EstadoIncidencia.de("ANULADA"))
        assertNull(TipoIncidencia.de("OTRA"))
    }

    private companion object {
        val EMPLEADO = setOf("fichaje:leer", "fichaje:escribir", "cuadrante:leer")
        val GESTOR = EMPLEADO + "cuadrante:incidencias:revisar"
    }
}
