package com.nxtime.app.ui.horasextra

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.BolsaHorasExtraDTO
import com.nxtime.app.data.dto.HorasExtraDTO
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Horas extra vistas desde la app (Fase F).
 *
 * Lo que importa comprobar aquí no es la aritmética —esa vive en el
 * servidor, con sus dos umbrales— sino tres cosas de la app:
 *
 *  - que **el rol decide qué se pide**, no qué se permite: un empleado no
 *    llama al endpoint del equipo, que le daría 403 sin falta;
 *  - que la bolsa es opcional y su fallo no vacía la pantalla;
 *  - que revisar **recarga entero**, porque aceptar un aviso cambia
 *    también la bolsa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HorasExtraViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val sesion: SessionManager = mock()

    private fun aviso(
        id: Long,
        estado: String = "ABIERTO",
        tipo: String = "DIARIA"
    ) = HorasExtraDTO(
        id = id,
        usuarioId = 10L,
        usuario = "Ana",
        tipo = tipo,
        fecha = "2026-03-03",
        fechaFin = "2026-03-03",
        minutosExtra = 120,
        minutosEsperados = 540,
        registroId = 42L,
        estado = estado
    )

    private fun bolsa(consumidos: Int = 0, alLimite: Boolean = false) = BolsaHorasExtraDTO(
        anio = 2026,
        minutosTope = 4800,
        minutosConsumidos = consumidos,
        minutosDisponibles = 4800 - consumidos,
        avisosAbiertos = 2,
        alLimite = alLimite
    )

    /**
     * Con tipo explícito: `Response.error<Nothing>` no encaja donde se
     * espera un `Response<Algo>`, y cada llamada del repositorio
     * devuelve el suyo.
     */
    private fun <T> error(codigo: Int): Response<T> = Response.error(
        codigo, "{}".toResponseBody("application/json".toMediaType()))

    private suspend fun conRespuestasNormales() {
        whenever(repositorio.getMisHorasExtra(anyOrNull())).thenReturn(Response.success(listOf(aviso(1))))
        whenever(repositorio.getBolsaHorasExtra(anyOrNull(), anyOrNull()))
            .thenReturn(Response.success(bolsa()))
        whenever(repositorio.getHorasExtraDelEquipo(anyOrNull()))
            .thenReturn(Response.success(listOf(aviso(2))))
    }

    @Test
    fun `un empleado no pide la bandeja del equipo`() = runTest {
        whenever(sesion.fetchUserRole()).thenReturn("EMPLEADO")
        conRespuestasNormales()

        val vm = HorasExtraViewModel(repositorio, sesion)
        advanceUntilIdle()

        // Pedirlo daría 403 sin falta: el rol no autoriza nada, pero sí
        // evita una petición que se sabe rechazada.
        verify(repositorio, never()).getHorasExtraDelEquipo(anyOrNull())
        assertFalse(vm.uiState.value.puedeRevisar)
        assertTrue(vm.uiState.value.delEquipo.isEmpty())
        assertEquals(1, vm.uiState.value.mios.size)
    }

    @Test
    fun `un gestor si la pide`() = runTest {
        whenever(sesion.fetchUserRole()).thenReturn("GESTOR")
        conRespuestasNormales()

        val vm = HorasExtraViewModel(repositorio, sesion)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.puedeRevisar)
        assertEquals(1, vm.uiState.value.delEquipo.size)
    }

    @Test
    fun `un rol que la app no conoce se trata como el mas bajo`() = runTest {
        // Pasará en cuanto el backend desplegado vaya por delante de la
        // app instalada. La degradación correcta es enseñar de menos, no
        // de más.
        whenever(sesion.fetchUserRole()).thenReturn("DIRECTOR_GENERAL")
        conRespuestasNormales()

        val vm = HorasExtraViewModel(repositorio, sesion)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.puedeRevisar)
        verify(repositorio, never()).getHorasExtraDelEquipo(anyOrNull())
    }

    @Test
    fun `si falla la bolsa la pantalla sigue enseñando los avisos`() = runTest {
        whenever(sesion.fetchUserRole()).thenReturn("EMPLEADO")
        whenever(repositorio.getMisHorasExtra(anyOrNull()))
            .thenReturn(Response.success(listOf(aviso(1))))
        whenever(repositorio.getBolsaHorasExtra(anyOrNull(), anyOrNull()))
            .thenReturn(error<BolsaHorasExtraDTO>(500))

        val vm = HorasExtraViewModel(repositorio, sesion)
        advanceUntilIdle()

        // La barra desaparece, los avisos no: lo que hay que mirar son
        // los avisos, y quedarse en blanco por un total sería peor.
        assertEquals(null, vm.uiState.value.bolsa)
        assertEquals(1, vm.uiState.value.mios.size)
    }

    @Test
    fun `si fallan mis avisos si se enseña el error`() = runTest {
        whenever(sesion.fetchUserRole()).thenReturn("EMPLEADO")
        whenever(repositorio.getMisHorasExtra(anyOrNull()))
            .thenReturn(error<List<HorasExtraDTO>>(500))
        whenever(repositorio.getBolsaHorasExtra(anyOrNull(), anyOrNull()))
            .thenReturn(Response.success(bolsa()))

        val vm = HorasExtraViewModel(repositorio, sesion)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.cargando)
    }

    @Test
    fun `aceptar recarga tambien la bolsa`() = runTest {
        whenever(sesion.fetchUserRole()).thenReturn("GESTOR")
        conRespuestasNormales()
        whenever(repositorio.revisarHorasExtra(eq(2L), eq(true), anyOrNull()))
            .thenReturn(Response.success(aviso(2, estado = "ACEPTADO")))

        val vm = HorasExtraViewModel(repositorio, sesion)
        advanceUntilIdle()

        vm.aceptar(2L)
        advanceUntilIdle()

        // Dos veces: la carga inicial y la de después de revisar. Retocar
        // la lista en local dejaría la barra de la bolsa con la cifra
        // vieja, que es justo el desfase que hace dudar del número.
        verify(repositorio, times(2)).getBolsaHorasExtra(anyOrNull(), anyOrNull())
        assertNotNull(vm.uiState.value.aviso)
    }

    @Test
    fun `justificar manda el motivo`() = runTest {
        whenever(sesion.fetchUserRole()).thenReturn("GESTOR")
        conRespuestasNormales()
        whenever(repositorio.revisarHorasExtra(any(), any(), anyOrNull()))
            .thenReturn(Response.success(aviso(2, estado = "JUSTIFICADO")))

        val vm = HorasExtraViewModel(repositorio, sesion)
        advanceUntilIdle()

        vm.justificar(2L, "Jornada intensiva pactada")
        advanceUntilIdle()

        // El motivo NO es opcional en este camino: el servidor rechaza
        // con 400 una justificación vacía.
        verify(repositorio).revisarHorasExtra(2L, false, "Jornada intensiva pactada")
    }

    @Test
    fun `un fallo al revisar deja el error y no dice que se haya hecho`() = runTest {
        whenever(sesion.fetchUserRole()).thenReturn("GESTOR")
        conRespuestasNormales()
        whenever(repositorio.revisarHorasExtra(any(), any(), anyOrNull()))
            .thenReturn(error<HorasExtraDTO>(409))

        val vm = HorasExtraViewModel(repositorio, sesion)
        advanceUntilIdle()

        vm.aceptar(2L)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertEquals(null, vm.uiState.value.aviso)
    }
}
