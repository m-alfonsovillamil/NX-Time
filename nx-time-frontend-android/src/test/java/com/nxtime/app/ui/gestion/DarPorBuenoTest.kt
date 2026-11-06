package com.nxtime.app.ui.gestion

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PaginaDTO
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.RegistroEquipoDTO
import com.nxtime.app.data.dto.UsuarioSimpleDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/** «Dar por bueno» en el historial del equipo, como en la web (ADR 054). */
@OptIn(ExperimentalCoroutinesApi::class)
class DarPorBuenoTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun jornada(id: Long, aRevisar: Boolean) = RegistroEquipoDTO(
        id = id,
        horaEntrada = "2026-10-05T07:00:00Z",
        horaSalida = "2026-10-05T15:00:00Z",
        fecha = "2026-10-05",
        usuario = UsuarioSimpleDTO("Laura"),
        usuarioId = 10,
        revisarPorLlegarTarde = aRevisar
    )

    private val revisada = Registro(id = 1, horaEntrada = "2026-10-05T07:00:00Z", horaSalida = "2026-10-05T15:00:00Z", enPausa = false)

    @Before
    fun historial() = runTest {
        whenever(repositorio.getMisEmpleados()).thenReturn(Response.success(emptyList()))
        whenever(repositorio.getHistorialEquipo(any())).thenReturn(
            Response.success(PaginaDTO(contenido = listOf(jornada(1, true), jornada(2, true)), pagina = 0, totalPaginas = 1, totalElementos = 2L))
        )
    }

    @Test
    fun `al darla por buena se le quita la marca, y solo a ella`() = runTest {
        whenever(repositorio.darPorBueno(1)).thenReturn(Response.success(revisada))
        val viewModel = HistorialEquipoViewModel(repositorio)
        advanceUntilIdle()

        viewModel.darPorBueno(1)
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertEquals(mapOf(1L to false, 2L to true), estado.registros.associate { it.id to it.revisarPorLlegarTarde })
        assertNull(estado.errorAlRevisar)
        assertTrue(estado.revisando.isEmpty())
    }

    /** Un 403 al revisar una jornada no puede esconder todas las demás tras la pantalla de error. */
    @Test
    fun `si el servidor dice que no, se dice por que y la marca se queda`() = runTest {
        whenever(repositorio.darPorBueno(1)).thenReturn(
            Response.error(
                403,
                """{"status":403,"detail":"No puedes revisar tu propia jornada."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )
        val viewModel = HistorialEquipoViewModel(repositorio)
        advanceUntilIdle()

        viewModel.darPorBueno(1)
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertEquals(MensajeUi.Texto("No puedes revisar tu propia jornada."), estado.errorAlRevisar)
        assertNull(estado.error)
        assertTrue(estado.registros.first { it.id == 1L }.revisarPorLlegarTarde)

        viewModel.descartarErrorAlRevisar()
        assertNull(viewModel.uiState.value.errorAlRevisar)
    }

    @Test
    fun `pulsar dos veces seguidas solo lo pide una`() = runTest {
        whenever(repositorio.darPorBueno(1)).thenReturn(Response.success(revisada))
        val viewModel = HistorialEquipoViewModel(repositorio)
        advanceUntilIdle()

        // Sin avanzar el reloj, la primera sigue en marcha cuando llega la segunda.
        viewModel.darPorBueno(1)
        assertTrue(1L in viewModel.uiState.value.revisando)
        viewModel.darPorBueno(1)
        advanceUntilIdle()

        verify(repositorio, times(1)).darPorBueno(1)
        assertFalse(viewModel.uiState.value.registros.first { it.id == 1L }.revisarPorLlegarTarde)
    }
}
