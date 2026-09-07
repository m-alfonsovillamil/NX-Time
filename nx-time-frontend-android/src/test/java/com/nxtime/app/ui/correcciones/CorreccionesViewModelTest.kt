package com.nxtime.app.ui.correcciones

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.CorreccionDTO
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
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Correcciones vistas desde la app (Fase E).
 *
 * Lo que se prueba aquí es sobre todo que la app **no decide** quién
 * puede resolver qué: los botones salen de `puedoResolver` y
 * `puedoDisputar`, que calcula el servidor. Si esta clase empezara a
 * deducirlo por su cuenta, habría dos copias de la regla y acabarían
 * discrepando.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CorreccionesViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun correccion(
        id: Long,
        estado: String = "PENDIENTE",
        puedoResolver: Boolean = false,
        puedoDisputar: Boolean = false
    ) = CorreccionDTO(
        id = id,
        fichajeId = 5L,
        empleado = UsuarioSimpleDTO("Ana"),
        solicitante = UsuarioSimpleDTO("Marta"),
        horaEntradaActual = "2026-06-01T07:00:00Z",
        horaSalidaActual = "2026-06-01T15:00:00Z",
        horaEntradaPropuesta = "2026-06-01T06:45:00Z",
        horaSalidaPropuesta = "2026-06-01T15:00:00Z",
        motivo = "El reloj iba adelantado",
        estado = estado,
        puedoResolver = puedoResolver,
        puedoDisputar = puedoDisputar
    )

    private suspend fun devuelve(
        pendientes: List<CorreccionDTO> = emptyList(),
        mias: List<CorreccionDTO> = emptyList()
    ) {
        whenever(repositorio.getCorreccionesPendientes()).thenReturn(Response.success(pendientes))
        whenever(repositorio.getMisCorrecciones()).thenReturn(Response.success(mias))
    }

    @Test
    fun `al construirse carga las dos listas`() = runTest {
        devuelve(pendientes = listOf(correccion(1)), mias = listOf(correccion(2)))

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.pendientes.size)
        assertEquals(1, viewModel.uiState.value.mias.size)
        assertFalse(viewModel.uiState.value.cargando)
    }

    /**
     * Los permisos vienen resueltos del servidor porque dependen de
     * quién pidió la corrección: al dueño del fichaje le sale resoluble
     * la misma fila que a un tercero no.
     */
    @Test
    fun `los permisos de cada fila llegan del servidor, no se deducen aqui`() = runTest {
        devuelve(pendientes = listOf(
            correccion(1, puedoResolver = true, puedoDisputar = true),
            correccion(2, puedoResolver = false, puedoDisputar = false)
        ))

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()

        val filas = viewModel.uiState.value.pendientes
        assertTrue(filas[0].puedoResolver && filas[0].puedoDisputar)
        assertFalse(filas[1].puedoResolver || filas[1].puedoDisputar)
    }

    @Test
    fun `aprobar manda aprobada=true y recarga`() = runTest {
        devuelve(pendientes = listOf(correccion(1, puedoResolver = true)))
        whenever(repositorio.resolverCorreccion(any(), any(), anyOrNull()))
            .thenReturn(Response.success(correccion(1, estado = "APROBADA")))

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()
        viewModel.aprobar(1L, null)
        advanceUntilIdle()

        verify(repositorio).resolverCorreccion(eq(1L), eq(true), eq(null))
        // Resolver cambia también el historial y el contador de avisos:
        // por eso se recarga en vez de retocar la lista en local.
        verify(repositorio, times(2)).getCorreccionesPendientes()
    }

    @Test
    fun `rechazar manda aprobada=false con el comentario`() = runTest {
        devuelve(pendientes = listOf(correccion(1, puedoResolver = true)))
        whenever(repositorio.resolverCorreccion(any(), any(), anyOrNull()))
            .thenReturn(Response.success(correccion(1, estado = "RECHAZADA")))

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()
        viewModel.rechazar(1L, "Las horas no cuadran")
        advanceUntilIdle()

        verify(repositorio).resolverCorreccion(eq(1L), eq(false), eq("Las horas no cuadran"))
    }

    @Test
    fun `disputar es una llamada distinta de rechazar`() = runTest {
        devuelve(pendientes = listOf(correccion(1, puedoDisputar = true)))
        whenever(repositorio.disputarCorreccion(any(), any()))
            .thenReturn(Response.success(correccion(1, estado = "EN_DISPUTA")))

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()
        viewModel.disputar(1L, "Ese día salí a mi hora")
        advanceUntilIdle()

        // No es un rechazo con otro nombre: rechazar cierra la solicitud
        // y disputar la escala a RRHH.
        verify(repositorio).disputarCorreccion(eq(1L), eq("Ese día salí a mi hora"))
        verify(repositorio, org.mockito.kotlin.never()).resolverCorreccion(any(), any(), anyOrNull())
    }

    @Test
    fun `un 403 al resolver se enseña con el mensaje del backend`() = runTest {
        devuelve(pendientes = listOf(correccion(1, puedoResolver = true)))
        whenever(repositorio.resolverCorreccion(any(), any(), anyOrNull())).thenReturn(
            Response.error(
                403,
                """{"status":403,"detail":"No te toca a ti resolver esa corrección."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()
        viewModel.aprobar(1L, null)
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Texto("No te toca a ti resolver esa corrección."),
            viewModel.uiState.value.error
        )
    }

    @Test
    fun `si falla la lista de pendientes se enseña el error`() = runTest {
        whenever(repositorio.getCorreccionesPendientes()).thenReturn(
            Response.error(500, "".toResponseBody("application/json".toMediaType()))
        )
        whenever(repositorio.getMisCorrecciones()).thenReturn(Response.success(emptyList()))

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error != null)
        assertFalse(viewModel.uiState.value.cargando)
    }

    @Test
    fun `el aviso de exito se descarta al mostrarlo`() = runTest {
        devuelve(pendientes = listOf(correccion(1, puedoResolver = true)))
        whenever(repositorio.resolverCorreccion(any(), any(), anyOrNull()))
            .thenReturn(Response.success(correccion(1, estado = "APROBADA")))

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()
        viewModel.aprobar(1L, null)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.aviso != null)

        viewModel.avisoMostrado()
        assertNull(viewModel.uiState.value.aviso)
    }

    @Test
    fun `un estado que esta version no conoce no rompe la pantalla`() = runTest {
        devuelve(pendientes = listOf(correccion(1, estado = "ALGO_NUEVO")))

        val viewModel = CorreccionesViewModel(repositorio)
        advanceUntilIdle()

        // Se sigue pintando la fila; lo único que falta es la etiqueta.
        assertEquals(1, viewModel.uiState.value.pendientes.size)
        assertNull(EstadoCorreccion.de("ALGO_NUEVO"))
    }

    @Test
    fun `los estados vivos son los que siguen esperando a alguien`() {
        assertTrue(EstadoCorreccion.PENDIENTE.estaViva)
        assertTrue(EstadoCorreccion.EN_DISPUTA.estaViva)
        assertFalse(EstadoCorreccion.APROBADA.estaViva)
        assertFalse(EstadoCorreccion.RECHAZADA.estaViva)
    }
}
