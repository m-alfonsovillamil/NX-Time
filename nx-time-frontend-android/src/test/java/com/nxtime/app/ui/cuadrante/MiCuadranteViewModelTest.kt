package com.nxtime.app.ui.cuadrante

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.DiaTeoricoDTO
import com.nxtime.app.data.repository.AuthRepository
import java.time.LocalDate
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
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Mi cuadrante (Fase B1): qué pide la pantalla y qué hace con lo que llega.
 *
 * La precedencia —un festivo manda sobre el cuadrante— no se prueba aquí: la
 * aplica el servidor y la cubre CuadranteIT. Lo que es de la app es el rango
 * que se pide y cómo se degrada si falla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MiCuadranteViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val lunes = LocalDate.of(2026, 10, 5)

    private fun dia(fecha: String, origen: String = "CUADRANTE") =
        DiaTeoricoDTO(fecha = fecha, origen = origen, minutos = 480, entrada = "09:00")

    @Test
    fun `pide las dos proximas semanas, hoy incluido`() = runTest {
        whenever(repositorio.getMiCuadrante(any(), any())).thenReturn(Response.success(emptyList()))

        MiCuadranteViewModel(repositorio, hoy = { lunes })
        advanceUntilIdle()

        // Catorce días: del lunes al domingo de la semana siguiente.
        verify(repositorio).getMiCuadrante(lunes, lunes.plusDays(13))
    }

    @Test
    fun `los dias llegan en el orden en que los manda el servidor`() = runTest {
        whenever(repositorio.getMiCuadrante(any(), any()))
            .thenReturn(Response.success(listOf(dia("2026-10-05"), dia("2026-10-06", "NO_LABORABLE"))))

        val viewModel = MiCuadranteViewModel(repositorio, hoy = { lunes })
        advanceUntilIdle()

        val estado = viewModel.uiState.value
        assertFalse(estado.cargando)
        assertNull(estado.error)
        assertEquals(listOf("2026-10-05", "2026-10-06"), estado.dias.map { it.fecha })
    }

    @Test
    fun `un error del servidor se ensena con su mensaje`() = runTest {
        whenever(repositorio.getMiCuadrante(any(), any())).thenReturn(
            Response.error(
                400,
                """{"status":400,"detail":"Como mucho 62 días de una vez."}"""
                    .toResponseBody("application/problem+json".toMediaType())
            )
        )

        val viewModel = MiCuadranteViewModel(repositorio, hoy = { lunes })
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.dias.isEmpty())
    }

    @Test
    fun `sin red se dice sin red, y recargar vuelve a pedir`() = runTest {
        whenever(repositorio.getMiCuadrante(any(), any())).thenThrow(RuntimeException("sin red"))
        val viewModel = MiCuadranteViewModel(repositorio, hoy = { lunes })
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        whenever(repositorio.getMiCuadrante(any(), any())).thenReturn(Response.success(listOf(dia("2026-10-05"))))
        viewModel.cargar()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.dias.size)
    }
}
