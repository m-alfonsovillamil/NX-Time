package com.nxtime.app.ui.perfil

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PerfilDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val sesion: SessionManager = mock()

    private val perfil = PerfilDTO(
        id = 10L,
        email = "ana@nxtime.test",
        nombre = "Ana",
        apellidos = "Fernández",
        nombreCompleto = "Ana Fernández",
        iniciales = "AF",
        fechaNacimiento = "1995-03-14",
        puesto = "Analista",
        departamentoId = 3L,
        departamentoNombre = "Operaciones",
        rol = "EMPLEADO",
        horasSemanales = "40.0",
        diasVacaciones = 22
    )

    private suspend fun conPerfilCargado(): PerfilViewModel {
        whenever(repositorio.getMiPerfil()).thenReturn(Response.success(perfil))
        return PerfilViewModel(repositorio, sesion).also { it.cargar() }
    }

    @Test
    fun `al construirse no pide nada, porque tambien existe en el login`() = runTest {
        PerfilViewModel(repositorio, sesion)
        advanceUntilIdle()

        // NxTimeNavHost lo crea para tener las iniciales del avatar, así
        // que existe antes de que haya sesión: con init { cargar() },
        // el 401 de esa primera llamada se quedaba pegado al estado y la
        // pantalla lo enseñaba después de iniciar sesión.
        verify(repositorio, never()).getMiPerfil()
    }

    @Test
    fun `cargar trae el perfil`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()

        assertEquals(perfil, viewModel.uiState.value.perfil)
        assertFalse(viewModel.uiState.value.cargando)
    }

    @Test
    fun `limpiar borra el perfil al cerrar sesion`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()

        viewModel.limpiar()

        // Vive en la Activity y sobrevive al cambio de usuario: sin esto
        // el avatar enseñaría las iniciales del anterior.
        assertEquals(null, viewModel.uiState.value.perfil)
        assertFalse(viewModel.uiState.value.cargando)
    }

    @Test
    fun `editar precarga el formulario con lo que hay guardado`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()

        viewModel.empezarAEditar()

        val estado = viewModel.uiState.value
        assertTrue(estado.editando)
        assertEquals("Ana", estado.nombre)
        assertEquals("Fernández", estado.apellidos)
        assertEquals("1995-03-14", estado.fechaNacimiento)
        assertEquals("Analista", estado.puesto)
    }

    @Test
    fun `un perfil sin apellidos ni puesto abre el formulario con los campos vacios, no con nulls`() = runTest {
        whenever(repositorio.getMiPerfil()).thenReturn(
            Response.success(perfil.copy(apellidos = null, puesto = null, fechaNacimiento = null))
        )
        val viewModel = PerfilViewModel(repositorio, sesion).also { it.cargar() }
        advanceUntilIdle()

        viewModel.empezarAEditar()

        assertEquals("", viewModel.uiState.value.apellidos)
        assertEquals("", viewModel.uiState.value.puesto)
        assertEquals("", viewModel.uiState.value.fechaNacimiento)
    }

    @Test
    fun `el nombre no se puede vaciar`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()
        viewModel.empezarAEditar()

        viewModel.onNombreCambia("   ")
        viewModel.guardar()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.perfil_nombre_obligatorio),
            viewModel.uiState.value.errorFormulario
        )
        verify(repositorio, never()).actualizarMiPerfil(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `una fecha mal escrita se para aqui`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()
        viewModel.empezarAEditar()

        viewModel.onFechaNacimientoCambia("14/03/1995")
        viewModel.guardar()
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Recurso(R.string.perfil_fecha_invalida),
            viewModel.uiState.value.errorFormulario
        )
        verify(repositorio, never()).actualizarMiPerfil(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `no se nace manana`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()
        viewModel.empezarAEditar()

        viewModel.onFechaNacimientoCambia(LocalDate.now().plusDays(1).toString())
        viewModel.guardar()
        advanceUntilIdle()

        // El backend también lo rechaza con @Past; pararlo aquí ahorra
        // el viaje de ida y vuelta.
        assertEquals(
            MensajeUi.Recurso(R.string.perfil_fecha_futura),
            viewModel.uiState.value.errorFormulario
        )
        verify(repositorio, never()).actualizarMiPerfil(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `vaciar el puesto manda cadena vacia, que es como se borra`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()
        viewModel.empezarAEditar()
        whenever(repositorio.actualizarMiPerfil(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Response.success(perfil.copy(puesto = null)))

        viewModel.onPuestoCambia("")
        viewModel.guardar()
        advanceUntilIdle()

        // Cadena vacía y no null: null significaría "no lo toques".
        verify(repositorio).actualizarMiPerfil(eq("Ana"), eq("Fernández"), eq("1995-03-14"), eq(""))
        assertFalse(viewModel.uiState.value.editando)
    }

    @Test
    fun `una fecha en blanco no se manda, porque no se puede borrar`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()
        viewModel.empezarAEditar()
        whenever(repositorio.actualizarMiPerfil(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Response.success(perfil))

        viewModel.onFechaNacimientoCambia("")
        viewModel.guardar()
        advanceUntilIdle()

        // Un LocalDate no tiene cadena vacía, así que dejar el campo en
        // blanco es "no la toques", no "quítamela".
        verify(repositorio).actualizarMiPerfil(any(), any(), eq(null), any())
    }

    @Test
    fun `guardar bien cierra el formulario y deja el perfil nuevo`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()
        viewModel.empezarAEditar()
        val actualizado = perfil.copy(apellidos = "Ruiz", nombreCompleto = "Ana Ruiz", iniciales = "AR")
        whenever(repositorio.actualizarMiPerfil(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Response.success(actualizado))

        viewModel.onApellidosCambia("Ruiz")
        viewModel.guardar()
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertFalse(estado.editando)
        assertEquals("AR", estado.perfil?.iniciales)
        assertEquals(null, estado.errorFormulario)
    }

    @Test
    fun `si el servidor rechaza, el formulario sigue abierto`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()
        viewModel.empezarAEditar()
        whenever(repositorio.actualizarMiPerfil(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Response.error(400, okhttp3.ResponseBody.create(null, "")))

        viewModel.guardar()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.editando)
        assertEquals(
            MensajeUi.Recurso(R.string.error_datos_invalidos),
            viewModel.uiState.value.errorFormulario
        )
    }

    @Test
    fun `cancelar cierra el formulario sin llamar a nadie`() = runTest {
        val viewModel = conPerfilCargado()
        advanceUntilIdle()
        viewModel.empezarAEditar()

        viewModel.cancelarEdicion()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.editando)
        verify(repositorio, never()).actualizarMiPerfil(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `cerrar sesion borra los datos guardados`() = runTest {
        // Se mudó aquí desde FicharViewModel en la Fase B, junto con el
        // botón: el menú de tres puntos de "Mi jornada" ya no existe.
        val viewModel = conPerfilCargado()
        advanceUntilIdle()

        viewModel.cerrarSesion()

        verify(sesion).clearAuthData()
    }

    @Test
    fun `un fallo de red al cargar deja un mensaje y no la pantalla colgada`() = runTest {
        whenever(repositorio.getMiPerfil()).thenThrow(RuntimeException("sin red"))
        val viewModel = PerfilViewModel(repositorio, sesion).also { it.cargar() }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.cargando)
        assertEquals(
            MensajeUi.Recurso(R.string.error_sin_conexion),
            viewModel.uiState.value.error
        )
    }
}
