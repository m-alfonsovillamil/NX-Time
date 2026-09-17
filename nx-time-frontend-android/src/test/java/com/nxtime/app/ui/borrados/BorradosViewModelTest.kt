package com.nxtime.app.ui.borrados

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.CandidatoBorradoDTO
import com.nxtime.app.data.dto.SolicitudBorradoDTO
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * La bandeja de borrados de datos (ADR 016).
 *
 * Lo que importa: que tras ejecutar o rechazar la lista se recargue
 * **también cuando falla**, porque un 409 suele significar que los bloqueos
 * han cambiado desde que se cargó, y la tarjeta tiene que enseñar los de ahora.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BorradosViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun solicitud(id: Long, bloqueos: List<String> = emptyList(), estado: String = "PENDIENTE") =
        SolicitudBorradoDTO(
            id = id, usuarioId = 10L, nombre = "Ana Pruebas", email = "ana@test",
            estado = estado, creadaEn = "2026-09-17T08:00:00Z", bloqueos = bloqueos
        )

    private fun error409() = Response.error<SolicitudBorradoDTO>(
        409,
        """{"detail":"Todavía no se puede ejecutar. Tiene una jornada abierta."}"""
            .toResponseBody("application/problem+json".toMediaType())
    )

    @Test
    fun `al construirse carga las pendientes con sus bloqueos`() = runTest {
        whenever(repositorio.getBorradosPendientes())
            .thenReturn(Response.success(listOf(solicitud(1, listOf("Tiene una jornada abierta.")))))

        val vm = BorradosViewModel(repositorio)
        advanceUntilIdle()

        assertEquals(listOf("Tiene una jornada abierta."), vm.uiState.value.pendientes.single().bloqueos)
        assertFalse(vm.uiState.value.cargando)
    }

    @Test
    fun `ejecutar con exito avisa y recarga la lista`() = runTest {
        whenever(repositorio.getBorradosPendientes())
            .thenReturn(Response.success(listOf(solicitud(1))))
            .thenReturn(Response.success(emptyList()))
        whenever(repositorio.ejecutarBorrado(1L)).thenReturn(Response.success(solicitud(1, estado = "EJECUTADA")))

        val vm = BorradosViewModel(repositorio)
        advanceUntilIdle()
        vm.ejecutar(1L)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.aviso)
        assertNull(vm.uiState.value.error)
        assertEquals(emptyList<SolicitudBorradoDTO>(), vm.uiState.value.pendientes)
        assertFalse(vm.uiState.value.enviando)
    }

    @Test
    fun `si el servidor lo rechaza, se ve el error y la lista se recarga igual`() = runTest {
        whenever(repositorio.getBorradosPendientes())
            .thenReturn(Response.success(listOf(solicitud(1))))
            .thenReturn(Response.success(listOf(solicitud(1, listOf("Tiene una jornada abierta.")))))
        whenever(repositorio.ejecutarBorrado(1L)).thenReturn(error409())

        val vm = BorradosViewModel(repositorio)
        advanceUntilIdle()
        vm.ejecutar(1L)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertEquals(listOf("Tiene una jornada abierta."), vm.uiState.value.pendientes.single().bloqueos)
        verify(repositorio, times(2)).getBorradosPendientes()
    }

    @Test
    fun `abrir el registro carga los candidatos, y registrar bien cierra el dialogo y recarga`() = runTest {
        whenever(repositorio.getBorradosPendientes())
            .thenReturn(Response.success(emptyList()))
            .thenReturn(Response.success(listOf(solicitud(7))))
        whenever(repositorio.getCandidatosBorrado()).thenReturn(
            Response.success(listOf(CandidatoBorradoDTO(10L, "Javi", "javi@test", activo = false)))
        )
        whenever(repositorio.registrarBorrado(10L, "Correo del 12/09"))
            .thenReturn(Response.success(solicitud(7)))

        val vm = BorradosViewModel(repositorio)
        advanceUntilIdle()
        vm.abrirRegistro()
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.candidatos!!.single().activo)

        vm.registrar(10L, "Correo del 12/09")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.registrando)
        assertNotNull(vm.uiState.value.aviso)
        assertEquals(7L, vm.uiState.value.pendientes.single().id)
    }

    /* Si el servidor lo rechaza, el diálogo sigue abierto: lo escrito no se pierde. */
    @Test
    fun `si registrar falla, el dialogo sigue abierto con el error`() = runTest {
        whenever(repositorio.getBorradosPendientes()).thenReturn(Response.success(emptyList()))
        whenever(repositorio.getCandidatosBorrado()).thenReturn(Response.success(emptyList()))
        whenever(repositorio.registrarBorrado(any(), any())).thenReturn(error409())

        val vm = BorradosViewModel(repositorio)
        advanceUntilIdle()
        vm.abrirRegistro()
        vm.registrar(10L, "Carta")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.registrando)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `registrar sin decir como llego no llega al servidor`() = runTest {
        whenever(repositorio.getBorradosPendientes()).thenReturn(Response.success(emptyList()))

        val vm = BorradosViewModel(repositorio)
        advanceUntilIdle()
        vm.registrar(10L, " ")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        verify(repositorio, never()).registrarBorrado(any(), any())
    }

    @Test
    fun `rechazar sin comentario no llega al servidor`() = runTest {
        whenever(repositorio.getBorradosPendientes()).thenReturn(Response.success(listOf(solicitud(1))))

        val vm = BorradosViewModel(repositorio)
        advanceUntilIdle()
        vm.rechazar(1L, "   ")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        verify(repositorio, never()).rechazarBorrado(any(), any())
    }
}
