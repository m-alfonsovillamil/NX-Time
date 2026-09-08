package com.nxtime.app.ui.denuncias

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.DenunciaDTO
import com.nxtime.app.data.dto.ResumenDenunciaDTO
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * El canal visto por quien lo instruye (Fase G).
 *
 * Lo que se fija aquí es que la app **no reimplementa** las reglas de
 * instrucción —cerrar exige conclusión, un cerrado no se reabre, nadie
 * instruye lo suyo— sino que las deja al servidor y enseña su respuesta.
 * Lo que sí es responsabilidad suya es que la bandeja no se quede con la
 * cifra vieja después de tocar un expediente, porque acusar recibo apaga
 * un plazo y cerrar mueve la fila.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CanalDenunciasViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun resumen(id: Long = 1L, diasHastaAcuse: Long? = -2L) = ResumenDenunciaDTO(
        id = id,
        categoria = "SEGURIDAD",
        categoriaEtiqueta = "Seguridad y salud en el trabajo",
        estado = "RECIBIDA",
        anonima = true,
        creadoEn = "2026-09-01T08:00:00Z",
        diasHastaAcuse = diasHastaAcuse,
        diasHastaRespuesta = 81
    )

    private fun expediente(id: Long = 1L, estado: String = "RECIBIDA") = DenunciaDTO(
        id = id,
        categoria = "SEGURIDAD",
        categoriaEtiqueta = "Seguridad y salud en el trabajo",
        descripcion = "Los hechos.",
        estado = estado,
        anonima = true,
        creadoEn = "2026-09-01T08:00:00Z",
        diasHastaAcuse = if (estado == "RECIBIDA") -2L else null,
        diasHastaRespuesta = 81
    )

    private fun <T> error(codigo: Int): Response<T> = Response.error(
        codigo, "{}".toResponseBody("application/json".toMediaType()))

    private suspend fun conBandeja(vararg filas: ResumenDenunciaDTO) {
        whenever(repositorio.getBandejaDenuncias()).thenReturn(Response.success(filas.toList()))
    }

    @Test
    fun `la bandeja llega tal cual la ordena el servidor`() = runTest {
        // El orden es una regla de negocio, no de presentación: las
        // abiertas primero y las más antiguas arriba, porque son las que
        // están más cerca de incumplir el plazo. Reordenar aquí sería
        // tener dos criterios.
        conBandeja(resumen(id = 5L), resumen(id = 2L))

        val vm = CanalDenunciasViewModel(repositorio)
        advanceUntilIdle()

        assertEquals(listOf(5L, 2L), vm.uiState.value.bandeja.map { it.id })
    }

    @Test
    fun `pasar a investigacion no manda conclusion`() = runTest {
        conBandeja(resumen())
        whenever(repositorio.getDenuncia(1L)).thenReturn(Response.success(expediente()))
        whenever(repositorio.cambiarEstadoDenuncia(any(), any(), anyOrNull()))
            .thenReturn(Response.success(expediente(estado = "EN_INVESTIGACION")))

        val vm = CanalDenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrir(1L)
        advanceUntilIdle()
        vm.cambiarEstado(EstadoDenuncia.EN_INVESTIGACION, null)
        advanceUntilIdle()

        // Con conclusión, un expediente que sigue abierto lo rechaza el
        // CHECK de la base: mandarla aquí sería provocar un 400 seguro.
        verify(repositorio).cambiarEstadoDenuncia(eq(1L), eq("EN_INVESTIGACION"), isNull())
    }

    @Test
    fun `una conclusion en blanco se manda como nula, no como cadena vacia`() = runTest {
        conBandeja(resumen())
        whenever(repositorio.getDenuncia(1L)).thenReturn(Response.success(expediente()))
        whenever(repositorio.cambiarEstadoDenuncia(any(), any(), anyOrNull()))
            .thenReturn(Response.success(expediente(estado = "EN_INVESTIGACION")))

        val vm = CanalDenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrir(1L)
        advanceUntilIdle()
        vm.cambiarEstado(EstadoDenuncia.EN_INVESTIGACION, "   ")
        advanceUntilIdle()

        // "" no es "sin conclusión" para el servidor: es una conclusión
        // vacía, y en un cierre pasaría la validación diciendo nada.
        verify(repositorio).cambiarEstadoDenuncia(eq(1L), eq("EN_INVESTIGACION"), isNull())
    }

    @Test
    fun `cerrar manda la conclusion recortada`() = runTest {
        conBandeja(resumen())
        whenever(repositorio.getDenuncia(1L)).thenReturn(Response.success(expediente()))
        whenever(repositorio.cambiarEstadoDenuncia(any(), any(), anyOrNull()))
            .thenReturn(Response.success(expediente(estado = "ARCHIVADA")))

        val vm = CanalDenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrir(1L)
        advanceUntilIdle()
        vm.cambiarEstado(EstadoDenuncia.ARCHIVADA, "  No se sostiene.  ")
        advanceUntilIdle()

        verify(repositorio).cambiarEstadoDenuncia(eq(1L), eq("ARCHIVADA"), eq("No se sostiene."))
    }

    @Test
    fun `tocar un expediente recarga tambien la bandeja`() = runTest {
        conBandeja(resumen())
        whenever(repositorio.getDenuncia(1L)).thenReturn(Response.success(expediente()))
        whenever(repositorio.responderDenunciaComoInstructor(any(), any()))
            .thenReturn(Response.success(expediente(estado = "EN_INVESTIGACION")))

        val vm = CanalDenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrir(1L)
        advanceUntilIdle()
        vm.responder("¿Puedes concretar fechas?")
        advanceUntilIdle()

        // Dos veces: la de `init` y la de después de escribir. El primer
        // mensaje vale como acuse de recibo, así que la fila de la
        // bandeja deja de estar en rojo -- y con una sola carga se
        // quedaría enseñando un plazo vencido que ya no lo está.
        verify(repositorio, times(2)).getBandejaDenuncias()
    }

    @Test
    fun `un error del servidor no borra el expediente abierto`() = runTest {
        conBandeja(resumen())
        whenever(repositorio.getDenuncia(1L)).thenReturn(Response.success(expediente()))
        whenever(repositorio.cambiarEstadoDenuncia(any(), any(), anyOrNull()))
            .thenReturn(error(400))

        val vm = CanalDenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrir(1L)
        advanceUntilIdle()
        vm.cambiarEstado(EstadoDenuncia.ARCHIVADA, null)
        advanceUntilIdle()

        // El 400 llega porque se cierra sin conclusión. Cerrar el
        // diálogo encima obligaría a volver a abrirlo para leer el
        // error, que es la peor forma de contar un fallo corregible.
        assertNotNull(vm.uiState.value.expediente)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `sin expediente abierto no se manda nada`() = runTest {
        conBandeja()

        val vm = CanalDenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.responder("Algo")
        vm.cambiarEstado(EstadoDenuncia.RESUELTA, "Algo")
        advanceUntilIdle()

        assertNull(vm.uiState.value.expediente)
        verify(repositorio, never()).responderDenunciaComoInstructor(any(), any())
        verify(repositorio, never()).cambiarEstadoDenuncia(any(), any(), anyOrNull())
    }
}
