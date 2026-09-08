package com.nxtime.app.ui.ofertas

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.CandidaturaDTO
import com.nxtime.app.data.dto.OfertaDTO
import com.nxtime.app.data.repository.AuthRepository
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * El tablón de vacantes visto por la plantilla (Fase H).
 *
 * Lo que se comprueba aquí es que la app **no decide** lo que decide el
 * servidor: si se admite una candidatura son dos condiciones (publicada
 * y en plazo) y llegan resueltas, el CV no se elige desde aquí, y el
 * error de "no tienes CV" se enseña tal como lo redactó el backend en
 * vez de inventarse uno propio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfertasViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun oferta(
        id: Long = 5L,
        admite: Boolean = true,
        yaMePresente: Boolean = false,
        plazoVencido: Boolean = false,
        candidaturas: Long? = null
    ) = OfertaDTO(
        id = id,
        titulo = "Backend sénior",
        descripcion = "Java 21 y Spring Boot.",
        puesto = "Desarrollador/a",
        departamento = "Ingeniería",
        publicadaPor = "Marta",
        estado = "ABIERTA",
        fechaPublicacion = "2026-09-01T08:00:00Z",
        fechaCierre = "2026-09-30",
        admiteCandidaturas = admite,
        plazoVencido = plazoVencido,
        yaMePresente = yaMePresente,
        candidaturas = candidaturas
    )

    private fun candidatura(id: Long = 30L, estado: String = "RECIBIDA") = CandidaturaDTO(
        id = id,
        ofertaId = 5L,
        ofertaTitulo = "Backend sénior",
        usuarioId = 10L,
        candidato = "Ana",
        carta = "Me presento.",
        estado = estado,
        creadoEn = "2026-09-02T08:00:00Z",
        cvAdjuntoId = 77L,
        cvNombre = "cv-ana.pdf",
        puedoValorar = false
    )

    private fun <T> error(codigo: Int): Response<T> = Response.error(
        codigo, "{}".toResponseBody("application/json".toMediaType()))

    private suspend fun conTablon(vararg ofertas: OfertaDTO) {
        whenever(repositorio.getOfertas()).thenReturn(Response.success(ofertas.toList()))
        whenever(repositorio.getMisCandidaturas()).thenReturn(Response.success(emptyList()))
    }

    @Test
    fun `si se admite o no la candidatura lo dice el servidor, no la app`() = runTest {
        // Publicada pero fuera de plazo: 'admiteCandidaturas' llega false
        // y el estado sigue siendo ABIERTA. Una app que dedujera esto del
        // estado ofrecería presentarse a una oferta caducada.
        conTablon(oferta(admite = false, plazoVencido = true))

        val vm = OfertasViewModel(repositorio)
        advanceUntilIdle()

        val fila = vm.uiState.value.ofertas.single()
        assertFalse(fila.admiteCandidaturas)
        assertTrue(fila.plazoVencido)
        assertEquals("ABIERTA", fila.estado)
    }

    @Test
    fun `presentarse manda solo la carta, nunca un CV`() = runTest {
        conTablon(oferta())
        whenever(repositorio.presentarCandidatura(any(), anyOrNull()))
            .thenReturn(Response.success(candidatura()))

        val vm = OfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.presentarse(5L, "Me presento.")
        advanceUntilIdle()

        // El CV lo pone el servidor: el repositorio no tiene por dónde
        // recibirlo, y eso es la garantía de que no se elige desde aquí.
        verify(repositorio).presentarCandidatura(eq(5L), eq("Me presento."))
    }

    @Test
    fun `una carta vacia se manda como nula, no como cadena vacia`() = runTest {
        conTablon(oferta())
        whenever(repositorio.presentarCandidatura(any(), anyOrNull()))
            .thenReturn(Response.success(candidatura()))

        val vm = OfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.presentarse(5L, null)
        advanceUntilIdle()

        verify(repositorio).presentarCandidatura(eq(5L), isNull())
    }

    @Test
    fun `presentarse recarga el tablon y mis candidaturas`() = runTest {
        conTablon(oferta())
        whenever(repositorio.presentarCandidatura(any(), anyOrNull()))
            .thenReturn(Response.success(candidatura()))

        val vm = OfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.presentarse(5L, null)
        advanceUntilIdle()

        // Dos veces cada una: la de `init` y la de después. Presentarse
        // cambia 'yaMePresente' de la oferta Y añade una fila a mis
        // candidaturas; refrescar solo una deja la pantalla mintiendo.
        verify(repositorio, times(2)).getOfertas()
        verify(repositorio, times(2)).getMisCandidaturas()
    }

    @Test
    fun `sin CV el error del servidor llega tal cual y no se presenta nada`() = runTest {
        conTablon(oferta())
        whenever(repositorio.presentarCandidatura(any(), anyOrNull())).thenReturn(error(400))

        val vm = OfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.presentarse(5L, null)
        advanceUntilIdle()

        // El 400 de "necesitas un CV" viene redactado del backend: es más
        // concreto que cualquier texto genérico que pusiéramos aquí.
        assertNotNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.misCandidaturas.isEmpty())
    }

    @Test
    fun `si fallan mis candidaturas el tablon se ve igual`() = runTest {
        whenever(repositorio.getOfertas()).thenReturn(Response.success(listOf(oferta())))
        whenever(repositorio.getMisCandidaturas()).thenReturn(error(500))

        val vm = OfertasViewModel(repositorio)
        advanceUntilIdle()

        // Lo que hay que mirar son las vacantes: perder la lista de
        // candidaturas no puede vaciar la pantalla entera.
        assertEquals(1, vm.uiState.value.ofertas.size)
        assertTrue(vm.uiState.value.misCandidaturas.isEmpty())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `el contador de candidaturas llega nulo a quien no las valora`() = runTest {
        conTablon(oferta(candidaturas = null))

        val vm = OfertasViewModel(repositorio)
        advanceUntilIdle()

        // Null y no cero: cuántos compañeros han optado a un puesto no es
        // dato para el resto de la plantilla.
        assertNull(vm.uiState.value.ofertas.single().candidaturas)
    }

    @Test
    fun `abrir y cerrar el detalle no toca las listas`() = runTest {
        conTablon(oferta())

        val vm = OfertasViewModel(repositorio)
        advanceUntilIdle()
        val fila = vm.uiState.value.ofertas.single()

        vm.abrir(fila)
        assertEquals(5L, vm.uiState.value.seleccionada?.id)

        vm.cerrar()
        assertNull(vm.uiState.value.seleccionada)
        assertEquals(1, vm.uiState.value.ofertas.size)
    }
}
