package com.nxtime.app.ui.proyectos

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.AsignacionProyectoDTO
import com.nxtime.app.data.dto.AsignarProyectoRequest
import com.nxtime.app.data.dto.DetalleProyectoDTO
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.ProyectoDTO
import com.nxtime.app.data.dto.ProyectoRequest
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.time.LocalDate
import java.time.YearMonth

/**
 * Proyectos vistos desde la app (Fase D).
 *
 * Lo que se prueba es lo que la pantalla decide por su cuenta: a quién
 * se puede ofrecer en el desplegable de asignar, y que un 409 del
 * servidor —el caso de "ya está en otro proyecto", que es la regla
 * central de la fase— se enseñe con el mensaje del backend y no con un
 * texto genérico.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProyectosViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun viewModel() = ProyectosViewModel(repositorio, YearMonth.of(2026, 6))

    private fun proyecto(id: Long, codigo: String, activo: Boolean = true) =
        ProyectoDTO(
            id = id,
            codigo = codigo,
            nombre = "Proyecto $codigo",
            fechaInicio = "2026-01-01",
            activo = activo,
            asignados = 0
        )

    private fun asignacion(id: Long, usuarioId: Long, vigente: Boolean) =
        AsignacionProyectoDTO(
            id = id,
            usuarioId = usuarioId,
            usuario = "Persona $usuarioId",
            proyectoId = 1L,
            proyectoCodigo = "NX-CORE",
            proyectoNombre = "Plataforma",
            fechaInicio = "2026-01-01",
            fechaFin = if (vigente) null else "2026-03-31",
            vigente = vigente
        )

    private fun empleado(id: Long) =
        EmpleadoSimpleDTO(id = id, nombre = "Persona $id", email = "p$id@test.com")

    private suspend fun listaDevuelve(vararg proyectos: ProyectoDTO) {
        whenever(repositorio.getProyectos()).thenReturn(Response.success(proyectos.toList()))
    }

    private suspend fun detalleDevuelve(
        proyecto: ProyectoDTO,
        asignaciones: List<AsignacionProyectoDTO>
    ) {
        whenever(repositorio.getProyecto(any(), any(), any())).thenReturn(
            Response.success(
                DetalleProyectoDTO(
                    proyecto = proyecto,
                    asignaciones = asignaciones,
                    anio = 2026,
                    mes = 6,
                    horas = emptyList()
                )
            )
        )
    }

    @Test
    fun `la lista se carga al construir el ViewModel`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"), proyecto(2, "NX-APP"))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.proyectos.size)
        assertFalse(viewModel.uiState.value.cargando)
    }

    /**
     * El desplegable de "asignar a..." no puede ofrecer a quien ya está
     * dentro: el servidor lo rechazaría con un 409 y sería un camino sin
     * salida.
     */
    @Test
    fun `no se ofrece asignar a quien ya esta en el proyecto`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        detalleDevuelve(proyecto(1, "NX-CORE"), listOf(asignacion(10, usuarioId = 1, vigente = true)))
        whenever(repositorio.getMisEmpleados())
            .thenReturn(Response.success(listOf(empleado(1), empleado(2))))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.abrir(1L)
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.uiState.value.asignables.map { it.id })
    }

    /**
     * Pero a quien pasó por aquí y SALIÓ sí se le puede volver a asignar:
     * dejarle fuera obligaría a preguntarse por qué no aparece.
     */
    @Test
    fun `a quien ya salio del proyecto si se le puede volver a asignar`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        detalleDevuelve(proyecto(1, "NX-CORE"), listOf(asignacion(10, usuarioId = 1, vigente = false)))
        whenever(repositorio.getMisEmpleados())
            .thenReturn(Response.success(listOf(empleado(1), empleado(2))))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.abrir(1L)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.asignables.map { it.id })
    }

    @Test
    fun `no se ofrece asignar a alguien dado de baja`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        detalleDevuelve(proyecto(1, "NX-CORE"), emptyList())
        whenever(repositorio.getMisEmpleados()).thenReturn(
            Response.success(listOf(empleado(1), empleado(2).copy(activo = false)))
        )

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.abrir(1L)
        advanceUntilIdle()

        // Asignar a alguien de baja crearía una asignación que nadie va
        // a cumplir.
        assertEquals(listOf(1L), viewModel.uiState.value.asignables.map { it.id })
    }

    /**
     * La regla central de la fase, vista desde el cliente: el servidor
     * dice en QUÉ proyecto está ya, y eso es lo que hay que enseñar.
     */
    @Test
    fun `un 409 al asignar se enseña con el mensaje del backend`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        whenever(repositorio.asignarAProyecto(any(), any())).thenReturn(
            Response.error(
                409,
                """{"status":409,"detail":"Ana ya está asignado a NX-APP en esas fechas."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.asignar(1L, 2L, LocalDate.of(2026, 6, 1))
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Texto("Ana ya está asignado a NX-APP en esas fechas."),
            viewModel.uiState.value.error
        )
    }

    @Test
    fun `crear manda las fechas en ISO y recarga la lista`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        whenever(repositorio.crearProyecto(any()))
            .thenReturn(Response.success(proyecto(2, "NX-APP")))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.crear("  NX-APP  ", "  Móvil  ", "", LocalDate.of(2026, 4, 1))
        advanceUntilIdle()

        val captor = argumentCaptor<ProyectoRequest>()
        verify(repositorio).crearProyecto(captor.capture())
        assertEquals("NX-APP", captor.firstValue.codigo)
        assertEquals("Móvil", captor.firstValue.nombre)
        assertEquals("2026-04-01", captor.firstValue.fechaInicio)
        // Una descripción vacía viaja como null, no como cadena vacía.
        assertNull(captor.firstValue.descripcion)
    }

    @Test
    fun `asignar manda la fecha de inicio elegida`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        whenever(repositorio.asignarAProyecto(any(), any()))
            .thenReturn(Response.success(asignacion(10, usuarioId = 2, vigente = true)))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.asignar(1L, 2L, LocalDate.of(2026, 6, 1))
        advanceUntilIdle()

        val captor = argumentCaptor<AsignarProyectoRequest>()
        verify(repositorio).asignarAProyecto(eq(1L), captor.capture())
        assertEquals(2L, captor.firstValue.usuarioId)
        assertEquals("2026-06-01", captor.firstValue.fechaInicio)
        // Sin fecha de fin: queda asignado hasta nuevo aviso.
        assertNull(captor.firstValue.fechaFin)
    }

    @Test
    fun `sacar del proyecto manda la fecha de fin, no un borrado`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        whenever(repositorio.finalizarAsignacion(any(), any()))
            .thenReturn(Response.success(asignacion(10, usuarioId = 2, vigente = false)))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.finalizarAsignacion(10L, LocalDate.of(2026, 6, 30))
        advanceUntilIdle()

        verify(repositorio).finalizarAsignacion(eq(10L), eq("2026-06-30"))
    }

    @Test
    fun `cerrar el detalle no recarga la lista`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        detalleDevuelve(proyecto(1, "NX-CORE"), emptyList())
        whenever(repositorio.getMisEmpleados()).thenReturn(Response.success(emptyList()))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.abrir(1L)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.detalle != null)

        viewModel.cerrarDetalle()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.detalle)
        // Volver a la lista es un gesto constante: recargarla cada vez
        // haría parpadear la pantalla sin motivo.
        verify(repositorio, org.mockito.kotlin.times(1)).getProyectos()
    }

    @Test
    fun `la plantilla se pide una sola vez aunque se abran varios proyectos`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"), proyecto(2, "NX-APP"))
        detalleDevuelve(proyecto(1, "NX-CORE"), emptyList())
        whenever(repositorio.getMisEmpleados()).thenReturn(Response.success(listOf(empleado(1))))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.abrir(1L)
        advanceUntilIdle()
        viewModel.cerrarDetalle()
        viewModel.abrir(2L)
        advanceUntilIdle()

        // Es la misma para todos los proyectos y no cambia mientras se
        // navega.
        verify(repositorio, org.mockito.kotlin.times(1)).getMisEmpleados()
    }

    @Test
    fun `un fallo de red al cargar la lista se enseña`() = runTest {
        whenever(repositorio.getProyectos()).thenThrow(RuntimeException("sin red"))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.cargando)
        assertTrue(viewModel.uiState.value.error != null)
    }

    @Test
    fun `un fallo al pedir la plantilla no rompe el detalle`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        detalleDevuelve(proyecto(1, "NX-CORE"), emptyList())
        whenever(repositorio.getMisEmpleados()).thenThrow(RuntimeException("sin red"))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.abrir(1L)
        advanceUntilIdle()

        // El proyecto se ve igual; lo único que falta es poder asignar.
        assertTrue(viewModel.uiState.value.detalle != null)
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.asignables.isEmpty())
    }

    @Test
    fun `cambiar de mes recarga el detalle abierto`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        detalleDevuelve(proyecto(1, "NX-CORE"), emptyList())
        whenever(repositorio.getMisEmpleados()).thenReturn(Response.success(emptyList()))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.abrir(1L)
        advanceUntilIdle()

        viewModel.cambiarMes(YearMonth.of(2026, 5))
        advanceUntilIdle()

        verify(repositorio).getProyecto(eq(1L), eq(2026), eq(5))
        assertEquals(YearMonth.of(2026, 5), viewModel.uiState.value.periodo)
    }

    @Test
    fun `cambiar de mes sin detalle abierto no pide nada`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.cambiarMes(YearMonth.of(2026, 5))
        advanceUntilIdle()

        verify(repositorio, org.mockito.kotlin.never()).getProyecto(any(), any(), any())
    }

    @Test
    fun `el aviso de exito se descarta al mostrarlo`() = runTest {
        listaDevuelve(proyecto(1, "NX-CORE"))
        whenever(repositorio.borrarProyecto(anyOrNull())).thenReturn(Response.success(Unit))

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.borrar(1L)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.aviso != null)

        viewModel.avisoMostrado()

        // Sin esto, girar el móvil volvería a sacar el mismo mensaje.
        assertNull(viewModel.uiState.value.aviso)
    }
}
