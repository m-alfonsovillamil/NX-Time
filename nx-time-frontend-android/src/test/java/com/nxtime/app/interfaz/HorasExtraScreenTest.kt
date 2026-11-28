package com.nxtime.app.interfaz

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nxtime.app.R
import com.nxtime.app.data.dto.BolsaHorasExtraDTO
import com.nxtime.app.data.dto.HorasExtraDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.horasextra.HorasExtraScreen
import com.nxtime.app.ui.horasextra.HorasExtraViewModel
import com.nxtime.app.ui.theme.NxTimeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response

/**
 * Las tarjetas de horas extra del equipo (revisión del 28/11/2026).
 *
 * Pagar o compensar con descanso no tiene vuelta atrás, y los tres botones
 * van juntos en una fila: lo que se prueba es que un toque no decide nada
 * hasta confirmarlo, y que la tarjeta empieza por quién.
 */
@RunWith(RobolectricTestRunner::class)
class HorasExtraScreenTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val repositorio: AuthRepository = mock()
    private val sesion: SessionManager = mock()

    private fun texto(id: Int): String = RuntimeEnvironment.getApplication().resources.getString(id)

    private val delEquipo = HorasExtraDTO(
        id = 7L,
        usuarioId = 10L,
        usuario = "Lucía Moreno Vega",
        tipo = "SEMANAL",
        fecha = "2026-11-16",
        fechaFin = "2026-11-22",
        minutosExtra = 195,
        minutosEsperados = 2400,
        estado = "ABIERTO"
    )

    @Before
    fun preparar() = runBlocking<Unit> {
        whenever(sesion.fetchAuthorities()).thenReturn(setOf("fichaje:leer", "horasextra:revisar"))
        whenever(repositorio.getMisHorasExtra(anyOrNull())).thenReturn(Response.success(emptyList()))
        whenever(repositorio.getBolsaHorasExtra(anyOrNull(), anyOrNull())).thenReturn(
            Response.success(BolsaHorasExtraDTO(2026, 4800, 0, 4800, 1, false))
        )
        whenever(repositorio.getHorasExtraDelEquipo(anyOrNull())).thenReturn(Response.success(listOf(delEquipo)))
        whenever(repositorio.revisarHorasExtra(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Response.success(delEquipo.copy(estado = "ACEPTADO", compensacion = "PAGO")))
        Unit
    }

    private fun montar() {
        val viewModel = HorasExtraViewModel(repositorio, sesion)
        pantalla.setContent { NxTimeTheme { HorasExtraScreen(onVolver = {}, viewModel = viewModel) } }
        pantalla.waitForIdle()
    }

    @Test
    fun `la tarjeta empieza por la persona y destaca las horas de mas`() {
        montar()

        pantalla.onNodeWithText("Lucía Moreno Vega").performScrollTo().assertExists()
        pantalla.onNodeWithText("+3h 15m").assertExists()
    }

    @Test
    fun `se pagan no decide nada hasta confirmarlo`() = runBlocking<Unit> {
        montar()

        pantalla.onNodeWithText(texto(R.string.horas_extra_aceptar)).performScrollTo().performClick()
        pantalla.waitForIdle()

        pantalla.onNodeWithText(texto(R.string.horas_extra_confirmar_pagan_titulo)).assertExists()
        verify(repositorio, never()).revisarHorasExtra(any(), any(), anyOrNull(), anyOrNull())

        // El botón del diálogo es el último con ese texto (el de la tarjeta sigue debajo).
        pantalla.onAllNodesWithText(texto(R.string.horas_extra_aceptar)).onLast().performClick()
        pantalla.waitForIdle()

        verify(repositorio).revisarHorasExtra(eq(7L), eq(true), anyOrNull(), eq("PAGO"))
    }

    @Test
    fun `cancelar la confirmacion no decide nada`() = runBlocking<Unit> {
        montar()

        pantalla.onNodeWithText(texto(R.string.horas_extra_boton_descanso)).performScrollTo().performClick()
        pantalla.waitForIdle()
        pantalla.onNodeWithText(texto(R.string.cancelar)).performClick()
        pantalla.waitForIdle()

        verify(repositorio, never()).revisarHorasExtra(any(), any(), anyOrNull(), anyOrNull())
    }
}
