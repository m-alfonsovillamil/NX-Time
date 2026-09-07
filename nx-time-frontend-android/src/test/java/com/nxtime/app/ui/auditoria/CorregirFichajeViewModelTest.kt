package com.nxtime.app.ui.auditoria

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.CorreccionFichajeRequest
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Corrección de un fichaje pasado.
 *
 * Lo que de verdad tiene lógica aquí es la conversión de horas: la
 * pantalla recoge hora **española** y el backend espera un instante en
 * **UTC**. Equivocarse ahí produce un fichaje desplazado una o dos horas
 * según la época del año -- y encima queda firmado en la auditoría, que
 * es inmutable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CorregirFichajeViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun viewModel() = CorregirFichajeViewModel(7L, repositorio)

    /**
     * Desde la Fase E el endpoint devuelve la SOLICITUD, no el fichaje
     * corregido: casi siempre queda pendiente y el fichaje no cambia.
     */
    private fun solicitud(estado: String = "PENDIENTE") = CorreccionDTO(
        id = 1,
        fichajeId = 8,
        empleado = UsuarioSimpleDTO("Ana"),
        solicitante = UsuarioSimpleDTO("Ana"),
        horaEntradaPropuesta = "2026-09-03T07:00:00Z",
        horaSalidaPropuesta = "2026-09-03T15:00:00Z",
        motivo = "Motivo",
        estado = estado
    )

    /**
     * El 3 de septiembre España está en horario de verano (UTC+2), así
     * que las 09:00 de la pantalla son las 07:00Z. Es exactamente el
     * desfase que se colaría al componer la cadena a mano.
     */
    @Test
    fun `las horas se mandan en UTC, no en hora local`() = runTest {
        whenever(repositorio.solicitarCorreccion(any(), any()))
            .thenReturn(Response.success(solicitud()))

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")
        viewModel.cambiarMotivo("Se olvidó fichar la salida")
        viewModel.guardar()
        advanceUntilIdle()

        val captor = argumentCaptor<CorreccionFichajeRequest>()
        verify(repositorio).solicitarCorreccion(eq(7L), captor.capture())
        assertEquals("2026-09-03T07:00:00Z", captor.firstValue.horaEntrada)
        assertEquals("2026-09-03T15:00:00Z", captor.firstValue.horaSalida)
    }

    /**
     * En invierno España va a UTC+1, así que la misma hora de pantalla
     * produce un instante distinto. Es la comprobación que caza un
     * `ZoneOffset` fijo escrito a mano.
     */
    @Test
    fun `el desfase cambia con el horario de invierno`() = runTest {
        whenever(repositorio.solicitarCorreccion(any(), any()))
            .thenReturn(Response.success(solicitud()))

        val viewModel = viewModel()
        // 15 de enero: las 09:00 españolas son las 08:00Z.
        viewModel.precargar("2026-01-15T08:00:00Z", "2026-01-15T17:00:00Z")
        viewModel.cambiarMotivo("Corrección de invierno")
        viewModel.guardar()
        advanceUntilIdle()

        val captor = argumentCaptor<CorreccionFichajeRequest>()
        verify(repositorio).solicitarCorreccion(eq(7L), captor.capture())
        assertEquals("2026-01-15T08:00:00Z", captor.firstValue.horaEntrada)
        assertEquals("2026-01-15T17:00:00Z", captor.firstValue.horaSalida)
    }

    @Test
    fun `el formulario se precarga con las horas del fichaje`() = runTest {
        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")

        val estado = viewModel.uiState.value
        assertEquals(9, estado.horaEntrada)
        assertEquals(0, estado.minutoEntrada)
        assertEquals(17, estado.horaSalida)
        assertEquals(0, estado.minutoSalida)
    }

    /**
     * El motivo es lo único que da valor a la traza de auditoría. Se
     * comprueba en la app para no gastar una ida y vuelta, pero el
     * backend lo valida igualmente.
     */
    @Test
    fun `sin motivo no se envia nada`() = runTest {
        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")
        viewModel.guardar()
        advanceUntilIdle()

        verify(repositorio, never()).solicitarCorreccion(any(), any())
        assertEquals(
            MensajeUi.Recurso(R.string.correccion_motivo_vacio),
            viewModel.uiState.value.error
        )
    }

    @Test
    fun `una salida anterior a la entrada no se envia`() = runTest {
        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")
        viewModel.cambiarMotivo("Motivo válido")
        viewModel.cambiarSalida(8, 0)
        viewModel.guardar()
        advanceUntilIdle()

        verify(repositorio, never()).solicitarCorreccion(any(), any())
        assertEquals(
            MensajeUi.Recurso(R.string.correccion_salida_anterior),
            viewModel.uiState.value.error
        )
    }

    /**
     * El 409 del backend ("el fichaje está activo o ya fue corregido") es
     * un estado esperable, no un fallo genérico: se enseña el `detail`
     * que escribe el servidor, no un "409 Conflict".
     */
    @Test
    fun `un 409 se enseña con el mensaje del backend`() = runTest {
        whenever(repositorio.solicitarCorreccion(any(), any())).thenReturn(
            Response.error(
                409,
                """{"status":409,"detail":"El fichaje ya fue corregido."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")
        viewModel.cambiarMotivo("Otro intento")
        viewModel.guardar()
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertEquals(MensajeUi.Texto("El fichaje ya fue corregido."), estado.error)
        assertFalse(estado.enviando)
        assertFalse(estado.corregido)
    }

    // -----------------------------------------------------------------
    // Turno de noche (Fase C)
    // -----------------------------------------------------------------
    // Antes de esto, una jornada que cruzaba la medianoche NO SE PODÍA
    // corregir: las dos horas se componían sobre la misma fecha, así que
    // la salida quedaba veintidós horas antes de la entrada y el
    // formulario se negaba a enviar. Y es justo la jornada que más falta
    // hace corregir, porque es la que el cierre nocturno automático deja
    // marcada como incompleta.

    @Test
    fun `una jornada que cruza la medianoche llega con el interruptor puesto`() = runTest {
        val viewModel = viewModel()
        // 22:52 del 3 de septiembre a 00:29 del 4 (hora española: UTC+2).
        viewModel.precargar("2026-09-03T20:52:00Z", "2026-09-03T22:29:00Z")

        val estado = viewModel.uiState.value
        assertTrue(estado.salidaEsOtroDia)
        assertEquals(22, estado.horaEntrada)
        assertEquals(0, estado.horaSalida)
        assertEquals(29, estado.minutoSalida)
    }

    @Test
    fun `con el interruptor puesto la salida se manda con la fecha del dia siguiente`() = runTest {
        whenever(repositorio.solicitarCorreccion(any(), any()))
            .thenReturn(Response.success(solicitud()))

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T20:52:00Z", "2026-09-03T22:29:00Z")
        viewModel.cambiarSalida(0, 45)
        viewModel.cambiarMotivo("La salida real fueron las 00:45")
        viewModel.guardar()
        advanceUntilIdle()

        val captor = argumentCaptor<CorreccionFichajeRequest>()
        verify(repositorio).solicitarCorreccion(eq(7L), captor.capture())
        assertEquals("2026-09-03T20:52:00Z", captor.firstValue.horaEntrada)
        // Las 00:45 del DÍA 4 en hora española son las 22:45Z del día 3.
        assertEquals("2026-09-03T22:45:00Z", captor.firstValue.horaSalida)
    }

    @Test
    fun `sin el interruptor, esa misma salida sigue siendo anterior a la entrada`() = runTest {
        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T20:52:00Z", "2026-09-03T22:29:00Z")
        // Quien desmarque el interruptor con estas horas está diciendo
        // que la jornada acabó veintidós horas antes de empezar: eso
        // sigue sin poder enviarse.
        viewModel.cambiarSalidaEsOtroDia(false)
        viewModel.cambiarMotivo("Motivo válido")
        viewModel.guardar()
        advanceUntilIdle()

        verify(repositorio, never()).solicitarCorreccion(any(), any())
        assertEquals(
            MensajeUi.Recurso(R.string.correccion_salida_anterior),
            viewModel.uiState.value.error
        )
    }

    @Test
    fun `una jornada normal no marca el interruptor`() = runTest {
        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")

        assertFalse(viewModel.uiState.value.salidaEsOtroDia)
    }

    /**
     * El caso normal desde la Fase E: la solicitud se acepta pero el
     * fichaje NO se ha tocado. La pantalla tiene que poder decirlo, y
     * por eso `aplicadaEnElActo` es false.
     */
    @Test
    fun `una correccion PEDIDA termina la pantalla pero no dice que se haya aplicado`() = runTest {
        whenever(repositorio.solicitarCorreccion(any(), any()))
            .thenReturn(Response.success(solicitud("PENDIENTE")))

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")
        viewModel.cambiarMotivo("Se olvido fichar la salida")
        viewModel.guardar()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.corregido)
        assertFalse(viewModel.uiState.value.aplicadaEnElActo)
    }

    @Test
    fun `una auto-aprobada si dice que se ha aplicado`() = runTest {
        whenever(repositorio.solicitarCorreccion(any(), any()))
            .thenReturn(Response.success(solicitud("APROBADA")))

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")
        viewModel.cambiarMotivo("Se olvido fichar la salida")
        viewModel.guardar()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.aplicadaEnElActo)
    }

    @Test
    fun `una correccion aceptada marca la pantalla como terminada`() = runTest {
        whenever(repositorio.solicitarCorreccion(any(), any()))
            .thenReturn(Response.success(solicitud()))

        val viewModel = viewModel()
        viewModel.precargar("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")
        viewModel.cambiarMotivo("Se olvidó fichar la salida")
        viewModel.guardar()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.corregido)
        assertFalse(viewModel.uiState.value.enviando)
    }
}
