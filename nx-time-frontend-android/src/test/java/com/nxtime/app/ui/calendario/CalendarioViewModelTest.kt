package com.nxtime.app.ui.calendario

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.AusenciaCalendarioDTO
import com.nxtime.app.data.dto.CalendarioDTO
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.FestivoDTO
import com.nxtime.app.data.dto.FestivoRequest
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.time.LocalDate
import java.time.YearMonth

/**
 * El calendario del mes.
 *
 * Lo que se prueba es el índice por día, que es la única lógica de
 * verdad de esta pantalla: una ausencia llega como un rango con las
 * fechas COMPLETAS (puede empezar el mes anterior y acabar el
 * siguiente), y hay que repartirla por celdas sin desbordar el mes que
 * se está mirando.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarioViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    /**
     * Junio de 2026 y no "el mes actual": el reparto de una ausencia por
     * días se comprueba contra un mes de 30 días concreto, y con el mes
     * real el test diría una cosa distinta cada mes.
     */
    private fun viewModelDeJunio() =
        CalendarioViewModel(repositorio, YearMonth.of(2026, 6))

    private fun festivo(
        id: Long,
        fecha: String,
        ambito: String = "LOCAL",
        editable: Boolean = true
    ) = FestivoDTO(id, fecha, "Un festivo", ambito, editable)

    private fun ausencia(
        id: Long,
        desde: String,
        hasta: String,
        propia: Boolean = true
    ) = AusenciaCalendarioDTO(
        id = id,
        usuarioId = if (propia) 1L else 2L,
        usuario = if (propia) "Yo" else "Ana",
        fechaInicio = desde,
        fechaFin = hasta,
        tipo = TipoAusencia.VACACIONES,
        estado = EstadoAusencia.APROBADA,
        propia = propia
    )

    private suspend fun devuelve(
        festivos: List<FestivoDTO> = emptyList(),
        ausencias: List<AusenciaCalendarioDTO> = emptyList(),
        incluyeEquipo: Boolean = false
    ) {
        whenever(repositorio.getCalendario(any(), any(), any())).thenReturn(
            Response.success(
                CalendarioDTO(
                    anio = 2026,
                    mes = 6,
                    incluyeEquipo = incluyeEquipo,
                    festivos = festivos,
                    ausencias = ausencias
                )
            )
        )
    }

    @Test
    fun `una ausencia de varios dias ocupa todas sus celdas`() = runTest {
        devuelve(ausencias = listOf(ausencia(1, "2026-06-10", "2026-06-12")))

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()

        val porDia = viewModel.uiState.value.ausenciasPorDia
        assertEquals(3, porDia.size)
        assertTrue(porDia.containsKey(LocalDate.of(2026, 6, 10)))
        assertTrue(porDia.containsKey(LocalDate.of(2026, 6, 11)))
        assertTrue(porDia.containsKey(LocalDate.of(2026, 6, 12)))
        assertFalse(porDia.containsKey(LocalDate.of(2026, 6, 13)))
    }

    /**
     * El caso que rompería una implementación ingenua: el servidor manda
     * las fechas sin recortar a propósito -- para poder pintar que la
     * ausencia viene de antes y sigue después -- así que si el reparto no
     * las recorta, el mapa acaba con días de mayo y de julio dentro.
     */
    @Test
    fun `una ausencia que desborda el mes se recorta a sus dias`() = runTest {
        devuelve(ausencias = listOf(ausencia(1, "2026-05-28", "2026-07-03")))

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()

        val porDia = viewModel.uiState.value.ausenciasPorDia
        assertEquals(30, porDia.size)   // junio entero, ni un día más
        assertTrue(porDia.containsKey(LocalDate.of(2026, 6, 1)))
        assertTrue(porDia.containsKey(LocalDate.of(2026, 6, 30)))
        assertFalse(porDia.containsKey(LocalDate.of(2026, 5, 31)))
        assertFalse(porDia.containsKey(LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun `dos personas ausentes el mismo dia caen en la misma celda`() = runTest {
        devuelve(
            ausencias = listOf(
                ausencia(1, "2026-06-10", "2026-06-10", propia = true),
                ausencia(2, "2026-06-10", "2026-06-11", propia = false)
            )
        )

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()

        val porDia = viewModel.uiState.value.ausenciasPorDia
        assertEquals(2, porDia[LocalDate.of(2026, 6, 10)]?.size)
        assertEquals(1, porDia[LocalDate.of(2026, 6, 11)]?.size)
    }

    @Test
    fun `una fecha ilegible no tira la pantalla abajo`() = runTest {
        devuelve(
            festivos = listOf(festivo(1, "no-es-una-fecha")),
            ausencias = listOf(ausencia(1, "tampoco", "2026-06-10"))
        )

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()

        // Se ignoran las que no se pueden leer y el resto del mes se
        // pinta igual: un dato raro del servidor no puede dejar sin
        // calendario a nadie.
        assertTrue(viewModel.uiState.value.festivoPorDia.isEmpty())
        assertTrue(viewModel.uiState.value.ausenciasPorDia.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `cambiar de mes suelta el dia seleccionado`() = runTest {
        devuelve()

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()
        viewModel.seleccionarDia(LocalDate.of(2026, 6, 10))
        assertEquals(LocalDate.of(2026, 6, 10), viewModel.uiState.value.diaSeleccionado)

        viewModel.mesSiguiente()
        advanceUntilIdle()

        // Un día marcado que ya no está en la rejilla no se ve, pero
        // seguiría mandando en el detalle de abajo.
        assertNull(viewModel.uiState.value.diaSeleccionado)
    }

    @Test
    fun `tocar dos veces el mismo dia lo deselecciona`() = runTest {
        devuelve()

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()
        val dia = LocalDate.of(2026, 6, 10)
        viewModel.seleccionarDia(dia)
        viewModel.seleccionarDia(dia)

        assertNull(viewModel.uiState.value.diaSeleccionado)
    }

    @Test
    fun `el interruptor de equipo se manda al servidor`() = runTest {
        devuelve(incluyeEquipo = true)

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()
        viewModel.alternarEquipo()
        advanceUntilIdle()

        verify(repositorio).getCalendario(any(), any(), eq(true))
        assertTrue(viewModel.uiState.value.verEquipo)
        assertTrue(viewModel.uiState.value.incluyeEquipo)
    }

    @Test
    fun `crear un festivo manda la fecha y el ambito elegidos, y recarga`() = runTest {
        devuelve()
        whenever(repositorio.crearFestivo(any()))
            .thenReturn(Response.success(festivo(9, "2026-06-15")))

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()
        viewModel.crearFestivo(LocalDate.of(2026, 6, 15), "  San Isidro  ", AmbitoFestivo.LOCAL)
        advanceUntilIdle()

        val captor = argumentCaptor<FestivoRequest>()
        verify(repositorio).crearFestivo(captor.capture())
        assertEquals("2026-06-15", captor.firstValue.fecha)
        assertEquals("San Isidro", captor.firstValue.descripcion)
        assertEquals("LOCAL", captor.firstValue.ambito)
    }

    @Test
    fun `un error al crear se enseña con el mensaje del backend`() = runTest {
        devuelve()
        whenever(repositorio.crearFestivo(any())).thenReturn(
            Response.error(
                409,
                """{"status":409,"detail":"Esa fecha ya es festivo para la empresa: San Isidro"}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )

        val viewModel = viewModelDeJunio()
        advanceUntilIdle()
        viewModel.crearFestivo(LocalDate.of(2026, 6, 15), "Otro", AmbitoFestivo.EMPRESA)
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Texto("Esa fecha ya es festivo para la empresa: San Isidro"),
            viewModel.uiState.value.error
        )
    }

    /**
     * NACIONAL no se ofrece en el desplegable: el servidor lo rechaza
     * con un 400, así que enseñarlo sería ofrecer un camino sin salida.
     */
    @Test
    fun `el ambito nacional no se puede elegir al crear`() {
        assertFalse(AmbitoFestivo.ELEGIBLES.contains(AmbitoFestivo.NACIONAL))
        assertEquals(3, AmbitoFestivo.ELEGIBLES.size)
        assertNull(AmbitoFestivo.de("ALGO_QUE_NO_EXISTE"))
        assertEquals(AmbitoFestivo.LOCAL, AmbitoFestivo.de("LOCAL"))
    }
}
