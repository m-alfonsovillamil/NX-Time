package com.nxtime.app.ui.fichar

import app.cash.turbine.test
import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.ResumenPersonalDTO
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
import org.junit.Assert.assertNotNull
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

    private fun proyectos(vararg codigos: String, enCurso: Int? = null): com.nxtime.app.data.dto.ProyectosParaFicharDTO {
        val lista = codigos.mapIndexed { i, codigo -> com.nxtime.app.data.dto.ProyectoParaFichar(i + 1L, codigo, "Proyecto $codigo") }
        return com.nxtime.app.data.dto.ProyectosParaFicharDTO(lista, enCurso?.let { lista[it] })
    }

    /* ADR 017: con dos o más proyectos se pregunta en cuál se empieza. */
    @Test
    fun `con varios proyectos pregunta en cual empezar y ficha con el elegido`() = runTest {
        whenever(repositorio.getProyectosParaFichar()).thenReturn(Response.success(proyectos("CORE", "APP")))
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()
        whenever(repositorio.registrarFichaje(any())).thenReturn(Response.success(registro()))

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.eligiendoProyectoAlIniciar)
        verify(repositorio, org.mockito.kotlin.never()).registrarFichaje(any())

        viewModel.iniciarEnProyecto(2L)
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO, 2L))
        assertFalse(viewModel.uiState.value.eligiendoProyectoAlIniciar)
    }

    @Test
    fun `con un solo proyecto no pregunta y decide el servidor`() = runTest {
        whenever(repositorio.getProyectosParaFichar()).thenReturn(Response.success(proyectos("CORE")))
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()
        whenever(repositorio.registrarFichaje(any())).thenReturn(Response.success(registro()))

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.eligiendoProyectoAlIniciar)
        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO))
    }

    @Test
    fun `cambiar de proyecto actualiza el proyecto en curso, y un rechazo se ve como error`() = runTest {
        whenever(repositorio.getProyectosParaFichar()).thenReturn(Response.success(proyectos("CORE", "APP", enCurso = 0)))
        val viewModel = viewModelCon(activo = registro())
        advanceUntilIdle()
        whenever(repositorio.cambiarProyecto(1L, 2L))
            .thenReturn(Response.success(proyectos("CORE", "APP", enCurso = 1)))

        viewModel.pedirCambioDeProyecto()
        viewModel.cambiarAProyecto(2L)
        advanceUntilIdle()

        assertEquals("APP", viewModel.uiState.value.proyectos?.enCurso?.codigo)
        assertFalse(viewModel.uiState.value.cambiandoDeProyecto)

        whenever(repositorio.cambiarProyecto(1L, 1L)).thenReturn(
            Response.error(409, """{"detail":"Reanuda la jornada antes de cambiar de proyecto."}"""
                .toResponseBody("application/problem+json".toMediaType()))
        )
        viewModel.cambiarAProyecto(1L)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertEquals("APP", viewModel.uiState.value.proyectos?.enCurso?.codigo)
    }

    /* Un festivo o una ausencia aprobada: se pregunta antes, y no se ficha hasta confirmar. */
    @Test
    fun `en un dia no laborable pregunta antes de iniciar, y solo inicia al confirmar`() = runTest {
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()
        whenever(repositorio.getEstadoDeHoy())
            .thenReturn(Response.success(com.nxtime.app.data.dto.EstadoDelDiaDTO(false, "Festivo: Navidad")))
        whenever(repositorio.registrarFichaje(any())).thenReturn(Response.success(registro()))

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertEquals("Festivo: Navidad", viewModel.uiState.value.confirmandoInicioNoLaborable)
        verify(repositorio, org.mockito.kotlin.never()).registrarFichaje(any())

        viewModel.confirmarInicioNoLaborable()
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO))
        assertNull(viewModel.uiState.value.confirmandoInicioNoLaborable)
    }

    @Test
    fun `cancelar en un dia no laborable no ficha`() = runTest {
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()
        whenever(repositorio.getEstadoDeHoy())
            .thenReturn(Response.success(com.nxtime.app.data.dto.EstadoDelDiaDTO(false, "Vacaciones")))

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()
        viewModel.cancelarInicioNoLaborable()
        advanceUntilIdle()

        verify(repositorio, org.mockito.kotlin.never()).registrarFichaje(any())
        assertEquals(EstadoJornada.SIN_JORNADA, viewModel.uiState.value.estado)
        assertFalse(viewModel.uiState.value.cargando)
    }

    /* Fichar no puede quedarse bloqueado por la comprobación: si falla, se inicia. */
    @Test
    fun `si no se puede saber si hoy es laborable, inicia igual`() = runTest {
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()
        whenever(repositorio.getEstadoDeHoy()).thenThrow(RuntimeException("sin red"))
        whenever(repositorio.registrarFichaje(any())).thenReturn(Response.success(registro()))

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO))
        assertNull(viewModel.uiState.value.confirmandoInicioNoLaborable)
    }

    /*
     * Finalizar pide confirmación e iniciar no, y la asimetría es
     * deliberada: cerrar la jornada por error obliga a pedir una
     * corrección y a que alguien la apruebe, mientras que empezarla por
     * error se arregla terminándola.
     */
    @Test
    fun `el boton principal NO termina la jornada, primero pregunta`() = runTest {
        val viewModel = viewModelCon(activo = registro())
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.confirmandoFin)
        verify(repositorio, never()).registrarFichaje(any())
        assertEquals(EstadoJornada.TRABAJANDO, viewModel.uiState.value.estado)
    }

    @Test
    fun `confirmar termina la jornada de verdad`() = runTest {
        val viewModel = viewModelCon(activo = registro())
        advanceUntilIdle()
        viewModel.pulsarBotonPrincipal()

        whenever(repositorio.registrarFichaje(any()))
            .thenReturn(Response.success(registro(horaSalida = "2026-08-29T15:00:00Z")))
        viewModel.confirmarFinDeJornada()
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.FIN))
        assertEquals(EstadoJornada.SIN_JORNADA, viewModel.uiState.value.estado)
        assertFalse(viewModel.uiState.value.confirmandoFin)
    }

    @Test
    fun `cancelar deja la jornada como estaba, sin tocar la red`() = runTest {
        val viewModel = viewModelCon(activo = registro())
        advanceUntilIdle()
        viewModel.pulsarBotonPrincipal()

        viewModel.cancelarFinDeJornada()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.confirmandoFin)
        assertEquals(EstadoJornada.TRABAJANDO, viewModel.uiState.value.estado)
        verify(repositorio, never()).registrarFichaje(any())
    }

    /*
     * La regla de la pausa va ANTES del diálogo: no tiene sentido pedir
     * confirmación de algo que se va a rechazar igualmente.
     */
    @Test
    fun `estando en pausa no se llega ni a preguntar`() = runTest {
        val viewModel = viewModelCon(activo = registro(enPausa = true))
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.confirmandoFin)
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

    // Cerrar sesión se mudó a PerfilViewModel en la Fase B, junto con
    // el botón: ver `PerfilViewModelTest`.

    /*
     * Los totales del resumen.
     *
     * `GET /dashboard/resumen` filtra por `hora_salida IS NOT NULL`, así
     * que sus minutos cuentan SOLO jornadas cerradas. La pantalla tiene
     * que sumarle la que está abierta o enseñaría "Hoy: 0h 00m" a quien
     * lleva dos horas fichado. Estos tests fijan esa suma.
     */

    private fun resumen(
        minutosHoy: Long = 0,
        minutosSemana: Long = 0,
        minutosMes: Long = 0
    ) = ResumenPersonalDTO(
        estadoActual = "TRABAJANDO",
        minutosHoy = minutosHoy,
        minutosSemana = minutosSemana,
        minutosMes = minutosMes,
        ausenciasPendientes = 0,
        saldoVacaciones = null
    )

    @Test
    fun `la jornada abierta se suma a hoy, a la semana y al mes`() {
        val estado = FicharUiState(
            resumen = resumen(minutosHoy = 120, minutosSemana = 600, minutosMes = 2400),
            segundosEnCurso = 90 * 60
        )

        assertEquals(210L, estado.minutosHoy)
        assertEquals(690L, estado.minutosSemana)
        assertEquals(2490L, estado.minutosMes)
    }

    /**
     * Sin ninguna jornada cerrada todavía -- el primer día de alguien --
     * lo que se enseña es justo lo que lleva en curso, no un cero.
     */
    @Test
    fun `sin jornadas cerradas el total es el tiempo en curso`() {
        val estado = FicharUiState(resumen = resumen(), segundosEnCurso = 45 * 60)

        assertEquals(45L, estado.minutosHoy)
    }

    /**
     * Los segundos sueltos de la jornada en curso no ascienden a minuto
     * hasta cumplirlo: 119 segundos son 1 minuto, no 2. Es la misma
     * regla que aplica el backend, y mezclarlas daría totales que no
     * cuadran con el informe.
     */
    @Test
    fun `los segundos en curso se truncan igual que en el backend`() {
        val estado = FicharUiState(resumen = resumen(minutosHoy = 10), segundosEnCurso = 119)

        assertEquals(11L, estado.minutosHoy)
    }

    @Test
    fun `sin resumen los totales no inventan datos`() {
        val estado = FicharUiState(resumen = null, segundosEnCurso = 60)

        assertEquals(1L, estado.minutosHoy)
    }
}
