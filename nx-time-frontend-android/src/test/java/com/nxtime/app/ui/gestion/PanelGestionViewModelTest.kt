package com.nxtime.app.ui.gestion

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PendientesDTO
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Los contadores del panel de gestión. Son una ayuda: si no cargan, el panel
 * se ve como antes, sin números inventados.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelGestionViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    @Test
    fun `carga los tres contadores`() = runTest {
        whenever(repositorio.getPendientes()).thenReturn(Response.success(PendientesDTO(3, 1, 7)))

        val viewModel = PanelGestionViewModel(repositorio)
        viewModel.cargar()
        advanceUntilIdle()

        assertEquals(PendientesDTO(3, 1, 7), viewModel.pendientes.value)
    }

    /*
     * Sin datos no hay ceros: `null`, y la pantalla no pinta ningún número.
     * Un "0" inventado diría "no tienes nada pendiente", que puede ser falso.
     */
    @Test
    fun `si falla, no se inventa ningun cero`() = runTest {
        whenever(repositorio.getPendientes()).thenThrow(RuntimeException("sin red"))

        val viewModel = PanelGestionViewModel(repositorio)
        viewModel.cargar()
        advanceUntilIdle()

        assertNull(viewModel.pendientes.value)
    }

    @Test
    fun `si la recarga falla, se queda el ultimo contador bueno`() = runTest {
        whenever(repositorio.getPendientes())
            .thenReturn(Response.success(PendientesDTO(2, 0, 0)))
            .thenReturn(Response.error(500, "{}".toResponseBody("application/json".toMediaType())))

        val viewModel = PanelGestionViewModel(repositorio)
        viewModel.cargar()
        advanceUntilIdle()
        viewModel.cargar()
        advanceUntilIdle()

        assertEquals(PendientesDTO(2, 0, 0), viewModel.pendientes.value)
    }
}
