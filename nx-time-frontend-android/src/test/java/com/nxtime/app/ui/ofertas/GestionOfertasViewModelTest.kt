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
 * Publicar vacantes y valorar candidaturas (Fase H).
 *
 * Lo que se fija aquí es que la app no reproduce las reglas de
 * valoración —las aplica el servidor— y que sí respeta la única
 * distinción que la fase añade a la interfaz: **crear no es publicar**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GestionOfertasViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun oferta(id: Long = 5L, estado: String = "BORRADOR", candidaturas: Long? = 0L) =
        OfertaDTO(
            id = id,
            titulo = "Backend sénior",
            descripcion = "Java 21.",
            publicadaPor = "Marta",
            estado = estado,
            admiteCandidaturas = estado == "ABIERTA",
            plazoVencido = false,
            yaMePresente = false,
            candidaturas = candidaturas
        )

    private fun candidatura(estado: String = "RECIBIDA", puedoValorar: Boolean = true) =
        CandidaturaDTO(
            id = 30L,
            ofertaId = 5L,
            ofertaTitulo = "Backend sénior",
            usuarioId = 10L,
            candidato = "Ana",
            estado = estado,
            creadoEn = "2026-09-02T08:00:00Z",
            cvAdjuntoId = 77L,
            cvNombre = "cv-ana.pdf",
            puedoValorar = puedoValorar
        )

    private fun <T> error(codigo: Int): Response<T> = Response.error(
        codigo, "{}".toResponseBody("application/json".toMediaType()))

    private suspend fun conOfertas(vararg ofertas: OfertaDTO) {
        whenever(repositorio.getOfertasDeGestion())
            .thenReturn(Response.success(ofertas.toList()))
    }

    @Test
    fun `crear una oferta no la publica`() = runTest {
        conOfertas()
        whenever(repositorio.crearOferta(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Response.success(oferta()))

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.crear("Backend sénior", "Java 21.", null, null)
        advanceUntilIdle()

        // Crear y publicar son dos llamadas distintas: con el estado
        // dentro del formulario, guardar una redacción a medias avisaría
        // a toda la plantilla.
        verify(repositorio).crearOferta(eq("Backend sénior"), eq("Java 21."), isNull(), isNull())
        verify(repositorio, never()).cambiarEstadoOferta(any(), any())
    }

    @Test
    fun `una oferta sin titulo o sin descripcion ni se manda`() = runTest {
        conOfertas()

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.crear("  ", "Java 21.", null, null)
        advanceUntilIdle()

        verify(repositorio, never()).crearOferta(any(), any(), anyOrNull(), anyOrNull())
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `publicar recarga la lista`() = runTest {
        conOfertas(oferta())
        whenever(repositorio.cambiarEstadoOferta(any(), any()))
            .thenReturn(Response.success(oferta(estado = "ABIERTA")))

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.cambiarEstado(5L, EstadoOferta.ABIERTA)
        advanceUntilIdle()

        verify(repositorio).cambiarEstadoOferta(5L, "ABIERTA")
        verify(repositorio, times(2)).getOfertasDeGestion()
    }

    @Test
    fun `un comentario en blanco se manda como nulo`() = runTest {
        conOfertas(oferta(estado = "ABIERTA"))
        whenever(repositorio.getCandidaturasDeOferta(5L))
            .thenReturn(Response.success(listOf(candidatura())))
        whenever(repositorio.valorarCandidatura(any(), any(), anyOrNull()))
            .thenReturn(Response.success(candidatura(estado = "EN_PROCESO")))

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrirCandidaturas(oferta(estado = "ABIERTA"))
        advanceUntilIdle()
        vm.valorar(30L, EstadoCandidatura.EN_PROCESO, "   ")
        advanceUntilIdle()

        // "" no es "sin comentario" para el servidor: en un descarte
        // pasaría la validación diciendo nada.
        verify(repositorio).valorarCandidatura(eq(30L), eq("EN_PROCESO"), isNull())
    }

    @Test
    fun `valorar recarga las candidaturas y la lista de ofertas`() = runTest {
        conOfertas(oferta(estado = "ABIERTA", candidaturas = 1L))
        whenever(repositorio.getCandidaturasDeOferta(5L))
            .thenReturn(Response.success(listOf(candidatura())))
        whenever(repositorio.valorarCandidatura(any(), any(), anyOrNull()))
            .thenReturn(Response.success(candidatura(estado = "SELECCIONADA")))

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrirCandidaturas(oferta(estado = "ABIERTA", candidaturas = 1L))
        advanceUntilIdle()
        vm.valorar(30L, EstadoCandidatura.SELECCIONADA, null)
        advanceUntilIdle()

        verify(repositorio, times(2)).getCandidaturasDeOferta(5L)
        verify(repositorio, times(2)).getOfertasDeGestion()
    }

    @Test
    fun `sin oferta abierta no se valora nada`() = runTest {
        conOfertas()

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.valorar(30L, EstadoCandidatura.SELECCIONADA, null)
        advanceUntilIdle()

        verify(repositorio, never()).valorarCandidatura(any(), any(), anyOrNull())
    }

    @Test
    fun `el error del servidor no cierra la lista de candidaturas`() = runTest {
        conOfertas(oferta(estado = "ABIERTA"))
        whenever(repositorio.getCandidaturasDeOferta(5L))
            .thenReturn(Response.success(listOf(candidatura())))
        whenever(repositorio.valorarCandidatura(any(), any(), anyOrNull())).thenReturn(error(400))

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrirCandidaturas(oferta(estado = "ABIERTA"))
        advanceUntilIdle()
        vm.valorar(30L, EstadoCandidatura.DESCARTADA, null)
        advanceUntilIdle()

        // El 400 llega porque se descarta sin comentario. Cerrar el
        // diálogo obligaría a reabrirlo para leer el error.
        assertNotNull(vm.uiState.value.seleccionada)
        assertNotNull(vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.candidaturas.size)
    }

    @Test
    fun `ver el CV baja el adjunto congelado, no el vigente de esa persona`() = runTest {
        conOfertas(oferta(estado = "ABIERTA"))
        whenever(repositorio.getCandidaturasDeOferta(5L))
            .thenReturn(Response.success(listOf(candidatura())))
        whenever(repositorio.descargarAdjunto(77L)).thenReturn(
            Response.success("%PDF-1.7".toResponseBody("application/pdf".toMediaType())))

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        var nombreAbierto: String? = null
        vm.descargarCv(candidatura()) { _, nombre -> nombreAbierto = nombre }
        advanceUntilIdle()

        // 77 es 'cvAdjuntoId': si pidiera el CV vigente de esa persona, se
        // valoraría un documento distinto del que se presentó.
        verify(repositorio).descargarAdjunto(77L)
        assertEquals("cv-ana.pdf", nombreAbierto)
        assertNull(vm.uiState.value.cvDescargandose)
    }

    @Test
    fun `si el servidor niega el CV no se abre nada y se explica`() = runTest {
        conOfertas(oferta(estado = "ABIERTA"))
        whenever(repositorio.descargarAdjunto(77L)).thenReturn(error(403))

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        var abierto = false
        vm.descargarCv(candidatura()) { _, _ -> abierto = true }
        advanceUntilIdle()

        assertEquals(false, abierto)
        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.cvDescargandose)
    }

    @Test
    fun `cerrar la lista de candidaturas la vacia`() = runTest {
        conOfertas(oferta(estado = "ABIERTA"))
        whenever(repositorio.getCandidaturasDeOferta(5L))
            .thenReturn(Response.success(listOf(candidatura())))

        val vm = GestionOfertasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrirCandidaturas(oferta(estado = "ABIERTA"))
        advanceUntilIdle()
        vm.cerrarCandidaturas()

        assertNull(vm.uiState.value.seleccionada)
        assertEquals(0, vm.uiState.value.candidaturas.size)
    }
}
