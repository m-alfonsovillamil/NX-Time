package com.nxtime.app.ui.fichar

import app.cash.turbine.test
import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.DiaTeoricoDTO
import com.nxtime.app.data.dto.EstadoDelDiaDTO
import com.nxtime.app.data.dto.EstadoUbicacion
import com.nxtime.app.data.dto.Modalidad
import com.nxtime.app.data.dto.UbicacionDelFichaje
import com.nxtime.app.data.session.Ajustes
import com.nxtime.app.data.sinconexion.ColaEnMemoria
import com.nxtime.app.data.sinconexion.EntornoFalso
import com.nxtime.app.data.ubicacion.FuenteDeUbicacion
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.ResumenPersonalDTO
import com.nxtime.app.data.dto.TipoFichaje
import com.nxtime.app.data.dto.TramoTeoricoDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
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
import org.mockito.kotlin.times
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
    private val ajustes: Ajustes = mock()
    private val posicion = UbicacionDelFichaje(EstadoUbicacion.CAPTURADA, 42.2406, -8.7207, 25)
    private val fuente: FuenteDeUbicacion = mock()

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

    private val cola = ColaEnMemoria()
    private val entorno = EntornoFalso()

    private fun nuevoViewModel() = FicharViewModel(repositorio, sesion, ajustes, fuente, cola, entorno)

    private suspend fun viewModelCon(activo: Registro?): FicharViewModel {
        whenever(repositorio.getRegistroActivo()).thenReturn(Response.success(activo))
        return nuevoViewModel()
    }

    /** ADR 054: sin red el fichaje se guarda con la hora de pulsar, y la pantalla sigue como si nada. */
    @Test
    fun `sin red la entrada se guarda en la cola y la pantalla se pone a trabajar`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        entorno.red = false
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        verify(repositorio, never()).registrarFichaje(any())
        val guardado = cola.fichajes.single()
        assertEquals(TipoFichaje.INICIO, guardado.tipo)
        assertEquals("ana@x", guardado.dueno)
        assertEquals("2026-10-26T07:00:00Z", guardado.horaDelDispositivo)
        assertEquals(1, entorno.envios)
        val estado = viewModel.uiState.value
        assertEquals(EstadoJornada.TRABAJANDO, estado.estado)
        assertEquals(1, estado.fichajesPendientes)
        assertNull(estado.error)
    }

    /** La hora es la de pulsar: lo que tarde el GPS no cuenta. */
    @Test
    fun `la hora guardada es la de pulsar, no la de cuando responde el GPS`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        whenever(ajustes.empresaPideUbicacion).thenReturn(true)
        whenever(ajustes.ubicacionExplicada).thenReturn(true)
        entorno.red = false
        whenever(repositorio.getRegistroActivo()).thenReturn(Response.success(null))
        val viewModel = FicharViewModel(repositorio, sesion, ajustes, FuenteDeUbicacion {
            delay(5_000)
            entorno.hora = entorno.hora.plusSeconds(5)
            posicion
        }, cola, entorno)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertEquals("2026-10-26T07:00:00Z", cola.fichajes.single().horaDelDispositivo)
        assertEquals(posicion, cola.fichajes.single().ubicacion)
    }

    @Test
    fun `si la peticion falla sin respuesta tambien se guarda`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        whenever(repositorio.registrarFichaje(any())).thenAnswer { throw java.io.IOException("se corto") }
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertEquals(1, cola.fichajes.size)
        assertEquals(EstadoJornada.TRABAJANDO, viewModel.uiState.value.estado)
    }

    @Test
    fun `un error del servidor se ensena y no se guarda`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        whenever(repositorio.registrarFichaje(any())).thenReturn(error(409, "Ya hay una jornada activa."))
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertTrue(cola.fichajes.isEmpty())
        assertNotNull(viewModel.uiState.value.error)
    }

    /** Si saliera antes, una salida podría llegar antes que su entrada, y se rechazaría. */
    @Test
    fun `con algo en la cola lo nuevo va detras aunque haya red`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        entorno.red = false
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()
        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        entorno.red = true
        viewModel.pulsarBotonPrincipal()
        viewModel.confirmarFinDeJornada()
        advanceUntilIdle()

        verify(repositorio, never()).registrarFichaje(any())
        assertEquals(listOf(TipoFichaje.INICIO, TipoFichaje.FIN), cola.fichajes.map { it.tipo })
        assertEquals(EstadoJornada.SIN_JORNADA, viewModel.uiState.value.estado)
    }

    @Test
    fun `al abrir sin red se parte de la ultima jornada que dijo el servidor`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        cola.guardarUltimaJornada("ana@x", registro())
        entorno.red = false
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        assertEquals(EstadoJornada.TRABAJANDO, viewModel.uiState.value.estado)
        verify(repositorio, never()).getRegistroActivo()
    }

    /** Sin red hay que saber igual si la empresa la pide: se guardó la última vez. */
    @Test
    fun `sin red se respeta lo que pidio la empresa la ultima vez`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        whenever(ajustes.empresaPreguntaModalidad).thenReturn(true)
        entorno.red = false
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.preguntandoModalidad)
    }

    @Test
    fun `al volver a abrir con la cola llena de cosas se lanza el envio`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        cola.anadir(
            com.nxtime.app.data.dto.FichajePendiente(
                idCliente = "a", dueno = "ana@x", tipo = TipoFichaje.INICIO,
                horaDelDispositivo = "2026-10-26T07:00:00Z", elapsedAlFichar = 1, arranqueAlFichar = 5
            )
        )

        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        assertEquals(1, entorno.envios)
        // Lo del servidor más lo de la cola: la entrada aún no ha llegado.
        assertEquals(EstadoJornada.TRABAJANDO, viewModel.uiState.value.estado)
    }

    @Test
    fun `lo que manda el envio refresca la pantalla, y un aviso se puede descartar`() = runTest {
        whenever(sesion.correoDeLaSesion()).thenReturn("ana@x")
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()
        val aviso = com.nxtime.app.data.dto.AvisoDeEnvio(
            "a", "ana@x", TipoFichaje.FIN, "2026-10-26T07:00:00Z", aRevisar = false, motivo = "No"
        )

        cola.anadirAvisos(listOf(aviso))
        advanceUntilIdle()
        assertEquals(listOf(aviso), viewModel.uiState.value.avisosDeEnvio)

        viewModel.descartarAvisoDeEnvio("a")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.avisosDeEnvio.isEmpty())
    }

    /** ADR 056: al entrar y al salir, explicada antes la primera vez, y sin ella se ficha igual. */
    private suspend fun empresaQuePide(modalidad: Boolean = false, ubicacion: Boolean = false) {
        whenever(repositorio.getEstadoDeHoy()).thenReturn(
            Response.success(EstadoDelDiaDTO(laborable = true, preguntarModalidad = modalidad, pedirUbicacion = ubicacion))
        )
        whenever(fuente.tomar()).thenReturn(posicion)
        whenever(repositorio.registrarFichaje(any())).thenReturn(Response.success(registro()))
    }

    @Test
    fun `la primera vez se explica la ubicacion y se pide el permiso antes de fichar`() = runTest {
        empresaQuePide(ubicacion = true)
        whenever(ajustes.ubicacionExplicada).thenReturn(false)
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.explicandoUbicacion)
        verify(repositorio, never()).registrarFichaje(any())

        viewModel.ubicacionExplicada()
        assertTrue(viewModel.uiState.value.pidiendoPermisoDeUbicacion)
        verify(ajustes).ubicacionExplicada = true
        verify(repositorio, never()).registrarFichaje(any())

        // Lo conceda o no: de eso se ocupa la fuente, que dice DENEGADA.
        viewModel.permisoDeUbicacionContestado()
        advanceUntilIdle()
        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO, ubicacion = posicion))
    }

    @Test
    fun `cancelar la explicacion de la ubicacion no ficha`() = runTest {
        empresaQuePide(ubicacion = true)
        whenever(ajustes.ubicacionExplicada).thenReturn(false)
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()
        viewModel.cancelarExplicacionDeUbicacion()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.explicandoUbicacion)
        assertFalse(viewModel.uiState.value.cargando)
        verify(repositorio, never()).registrarFichaje(any())
    }

    @Test
    fun `la ubicacion se manda al salir, y en las pausas no`() = runTest {
        empresaQuePide(ubicacion = true)
        whenever(ajustes.ubicacionExplicada).thenReturn(true)
        val viewModel = viewModelCon(activo = registro())
        advanceUntilIdle()

        viewModel.pulsarBotonPausa()
        advanceUntilIdle()
        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.PAUSA_INICIO))
        verify(fuente, never()).tomar()

        viewModel.pulsarBotonPrincipal()
        viewModel.confirmarFinDeJornada()
        advanceUntilIdle()
        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.FIN, ubicacion = posicion))
    }

    @Test
    fun `sin permiso se ficha igual y queda dicho`() = runTest {
        empresaQuePide(ubicacion = true)
        val denegada = UbicacionDelFichaje(EstadoUbicacion.DENEGADA)
        whenever(fuente.tomar()).thenReturn(denegada)
        whenever(ajustes.ubicacionExplicada).thenReturn(true)
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO, ubicacion = denegada))
        assertEquals(EstadoJornada.TRABAJANDO, viewModel.uiState.value.estado)
    }

    @Test
    fun `si la empresa no la pide no se toma la ubicacion ni se explica`() = runTest {
        empresaQuePide()
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.explicandoUbicacion)
        verify(fuente, never()).tomar()
        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO))
    }

    /** El permiso del sistema tapa la pantalla y al volver se pregunta por la jornada: no debe reactivar el botón. */
    @Test
    fun `volver a la pantalla mientras se toma la ubicacion no deja fichar dos veces`() = runTest {
        empresaQuePide(ubicacion = true)
        whenever(ajustes.ubicacionExplicada).thenReturn(true)
        whenever(repositorio.getRegistroActivo()).thenReturn(Response.success(null))
        // Lo que tarda un GPS: la respuesta llega unos segundos después.
        val viewModel = FicharViewModel(repositorio, sesion, ajustes, FuenteDeUbicacion {
            delay(5_000)
            posicion
        }, cola, entorno)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceTimeBy(1_000)
        viewModel.comprobarEstadoJornada()
        advanceTimeBy(1_000)
        assertTrue(viewModel.uiState.value.cargando)
        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()

        verify(repositorio, times(1)).registrarFichaje(any())
    }

    @Test
    fun `si la empresa lo pregunta, la modalidad se elige y se recuerda`() = runTest {
        empresaQuePide(modalidad = true)
        whenever(ajustes.ultimaModalidad).thenReturn(Modalidad.A_DISTANCIA)
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.preguntandoModalidad)
        assertEquals(Modalidad.A_DISTANCIA, viewModel.uiState.value.ultimaModalidad)
        verify(repositorio, never()).registrarFichaje(any())

        viewModel.elegirModalidad(Modalidad.PRESENCIAL)
        advanceUntilIdle()

        verify(ajustes).ultimaModalidad = Modalidad.PRESENCIAL
        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.INICIO, modalidad = Modalidad.PRESENCIAL))
    }

    @Test
    fun `la modalidad solo se manda al entrar`() = runTest {
        empresaQuePide(modalidad = true)
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()
        viewModel.pulsarBotonPrincipal()
        advanceUntilIdle()
        viewModel.elegirModalidad(Modalidad.A_DISTANCIA)
        advanceUntilIdle()

        viewModel.pulsarBotonPausa()
        advanceUntilIdle()

        verify(repositorio).registrarFichaje(PeticionFichaje(TipoFichaje.PAUSA_INICIO))
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
        val viewModel = nuevoViewModel()
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

    // ------------------------------------------------------------------
    // El cuadrante de hoy (Fase B1)
    // ------------------------------------------------------------------

    private suspend fun conCuadranteDeHoy(dia: DiaTeoricoDTO?): FicharViewModel {
        whenever(repositorio.getMiCuadrante(any(), any()))
            .thenReturn(Response.success(listOfNotNull(dia)))
        return viewModelCon(activo = null)
    }

    private fun dia(origen: String, entrada: String? = null) = DiaTeoricoDTO(
        fecha = "2026-10-05",
        origen = origen,
        minutos = if (entrada != null) 480 else 0,
        entrada = entrada,
        tramos = if (entrada != null) listOf(TramoTeoricoDTO(entrada, "17:00", 480)) else emptyList()
    )

    @Test
    fun `con cuadrante se dice a que hora tocaba entrar`() = runTest {
        val viewModel = conCuadranteDeHoy(dia("CUADRANTE", entrada = "09:00"))
        advanceUntilIdle()

        assertEquals(
            AvisoDeCuadrante.EntradaPrevista("09:00"),
            viewModel.uiState.value.avisoDeCuadrante
        )
    }

    @Test
    fun `con cuadrante pero sin turno hoy se dice que no toca`() = runTest {
        val viewModel = conCuadranteDeHoy(dia("EXCEPCION"))
        advanceUntilIdle()

        assertEquals(AvisoDeCuadrante.SinTurno, viewModel.uiState.value.avisoDeCuadrante)
    }

    /**
     * La mayoría no tiene cuadrante, y para ellos la pantalla tiene que quedar
     * exactamente como estaba: ni un horario inventado ni un "hoy no tienes
     * turno" que no es verdad.
     */
    @Test
    fun `sin cuadrante no se dice nada`() = runTest {
        val viewModel = conCuadranteDeHoy(dia("SIN_CUADRANTE"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.avisoDeCuadrante)
    }

    @Test
    fun `en un festivo tampoco, de eso ya se encarga el aviso de dia no laborable`() = runTest {
        val viewModel = conCuadranteDeHoy(dia("NO_LABORABLE"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.avisoDeCuadrante)
    }

    /** Como el resumen: un extra que falla no puede impedir fichar ni pintar un error. */
    @Test
    fun `si el cuadrante falla se ficha igual y no sale ningun error`() = runTest {
        whenever(repositorio.getMiCuadrante(any(), any())).thenThrow(RuntimeException("sin red"))
        val viewModel = viewModelCon(activo = null)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.avisoDeCuadrante)
        assertNull(viewModel.uiState.value.error)
        assertEquals(EstadoJornada.SIN_JORNADA, viewModel.uiState.value.estado)
    }
}
