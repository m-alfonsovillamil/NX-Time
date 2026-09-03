package com.nxtime.app.ui.fichar

import app.cash.turbine.test
import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.TipoFichaje
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * La pantalla principal: iniciar, pausar, reanudar y terminar jornada.
 *
 * Se prueban las dos cosas que son lógica de la app y no del servidor:
 * cómo se traduce la respuesta del backend al estado de la pantalla, y
 * las dos reglas que se resuelven en local para no gastar una ida y
 * vuelta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FicharViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val sesion: SessionManager = mock()

    @Before
    fun configurarSesion() {
        whenever(sesion.fetchUserName()).thenReturn("Ana")
        whenever(sesion.fetchUserRole()).thenReturn("EMPLEADO")
    }

    private fun registro(
        horaSalida: String? = null,
        enPausa: Boolean = false
    ) = Registro(
        id = 1,
        horaEntrada = "2026-08-29T07:00:00Z",
        horaSalida = horaSalida,
        enPausa = enPausa,
        minutosPausaAcumulados = 0
    )

    private fun error(codigo: Int, detalle: String) = Response.error<Registro>(
        codigo,
        """{"status":$codigo,"detail":"$detalle"}"""
            .toResponseBody("application/problem+json".toMediaType())
    )

    private suspend fun viewModelCon(activo: Registro?): FicharViewModel {
        whenever(repositorio.getRegistroActivo()).thenReturn(Response.success(activo))
        return FicharViewModel(repositorio, sesion)
    }

    @Test
    fun `sin jornada activa la pantalla arranca parada`() = runTest {
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.uiState.test {
            val estado = awaitItem()
            assertEquals(EstadoJornada.SIN_JORNADA, estado.estado)
            assertFalse(estado.cargando)
            assertEquals("Ana", estado.nombreUsuario)
        }
    }

    @Test
    fun `una jornada abierta deja la pantalla trabajando`() = runTest {
        val viewModel = viewModelCon(activo = registro())
        advanceUntilIdle()

        assertEquals(EstadoJornada.TRABAJANDO, viewModel.uiState.value.estado)
    }

    @Test
    fun `una jornada abierta y en pausa se distingue de una en marcha`() = runTest {
        val viewModel = viewModelCon(activo = registro(enPausa = true))
        advanceUntilIdle()

        assertEquals(EstadoJornada.EN_PAUSA, viewModel.uiState.value.estado)
    }

    /**
     * Al terminar, el backend devuelve el fichaje recién cerrado y no
     * null. Tomarlo por una jornada viva dejaría el botón en "finalizar"
     * después de haber fichado la salida.
     */
    @Test
    fun `un registro con hora de salida ya no es una jornada activa`() = runTest {
        val viewModel = viewModelCon(activo = registro(horaSalida = "2026-08-29T15:00:00Z"))
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertEquals(EstadoJornada.SIN_JORNADA, estado.estado)
        assertNull(estado.registro)
    }

    @Test
    fun `el boton principal inicia la jornada cuando no hay ninguna`() = runTest {
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        whenever(repositorio.registrarFichaje(any())).thenReturn(Response.success(registro()))
        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO))
        assertEquals(EstadoJornada.TRABAJANDO, viewModel.uiState.value.estado)
    }

    @Test
    fun `el boton principal termina la jornada cuando hay una en marcha`() = runTest {
        val viewModel = viewModelCon(activo = registro())
        advanceUntilIdle()

        whenever(repositorio.registrarFichaje(any()))
            .thenReturn(Response.success(registro(horaSalida = "2026-08-29T15:00:00Z")))
        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.FIN))
        assertEquals(EstadoJornada.SIN_JORNADA, viewModel.uiState.value.estado)
    }

    @Test
    fun `el boton de pausa reanuda si ya se estaba en pausa`() = runTest {
        val viewModel = viewModelCon(activo = registro(enPausa = true))
        advanceUntilIdle()

        whenever(repositorio.registrarFichaje(any())).thenReturn(Response.success(registro()))
        viewModel.pulsarBotonPausa()
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.PAUSA_FIN))
    }

    /**
     * Las dos reglas que no llegan a salir a la red: el estado de la
     * pantalla ya sabe que la acción no tiene sentido, así que el aviso
     * es inmediato y no consume una petición.
     */
    @Test
    fun `no se puede terminar la jornada estando en pausa`() = runTest {
        val viewModel = viewModelCon(activo = registro(enPausa = true))
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.fichar_reanuda_antes),
            viewModel.uiState.value.error
        )
        verify(repositorio, never()).registrarFichaje(any())
    }

    @Test
    fun `no se puede pausar sin haber iniciado la jornada`() = runTest {
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPausa()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.fichar_inicia_antes),
            viewModel.uiState.value.error
        )
        verify(repositorio, never()).registrarFichaje(any())
    }

    /**
     * El defecto que motivó ApiErrorParser: al fichar dos veces se leía
     * "Error al registrar fichaje: 409 Conflict" en lugar del mensaje
     * que el backend escribe en el "detail".
     */
    @Test
    fun `un conflicto del servidor llega con su mensaje, no con el codigo HTTP`() = runTest {
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        whenever(repositorio.registrarFichaje(any()))
            .thenReturn(error(409, "Ya hay una jornada activa."))
        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertEquals(MensajeUi.Texto("Ya hay una jornada activa."), estado.error)
        assertFalse(estado.cargando)
    }

    @Test
    fun `un fallo de red no deja la pantalla cargando para siempre`() = runTest {
        whenever(repositorio.getRegistroActivo()).thenThrow(RuntimeException("sin red"))
        val viewModel = FicharViewModel(repositorio, sesion)
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertFalse(estado.cargando)
        assertEquals(MensajeUi.Recurso(R.string.error_sin_conexion), estado.error)
    }

    // Qué ve cada rol ya no se decide en este ViewModel: se mudó a
    // `ui/util/Permisos.kt` y se prueba en `PermisosTest`.

    @Test
    fun `cerrar sesion borra los datos guardados`() = runTest {
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.cerrarSesion()

        verify(sesion).clearAuthData()
    }
}
