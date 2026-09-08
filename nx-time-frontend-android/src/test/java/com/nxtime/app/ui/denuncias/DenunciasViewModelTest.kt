package com.nxtime.app.ui.denuncias

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.DenunciaCreadaDTO
import com.nxtime.app.data.dto.DenunciaDTO
import com.nxtime.app.data.dto.MensajeDenunciaDTO
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * El canal de denuncias visto por quien denuncia (Fase G).
 *
 * Lo que se comprueba aquí no es que la pantalla pinte: es que la app
 * **no deshace por su cuenta** lo que el servidor se ha cuidado de no
 * guardar. Tres cosas concretas:
 *
 *  - el anonimato viaja tal cual lo eligió la persona, sin defectos que
 *    decidan por ella;
 *  - el código deja de existir en la app en cuanto se confirma que se ha
 *    guardado, y no se recuerda "por comodidad";
 *  - se contesta por la puerta por la que se entró, que no es la misma
 *    para una anónima que para una identificada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DenunciasViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private fun resumen(id: Long = 1L, anonima: Boolean = false) = ResumenDenunciaDTO(
        id = id,
        categoria = "FRAUDE",
        categoriaEtiqueta = "Fraude o irregularidad contable",
        estado = "RECIBIDA",
        anonima = anonima,
        creadoEn = "2026-09-01T08:00:00Z",
        diasHastaAcuse = 7,
        diasHastaRespuesta = 90
    )

    private fun expediente(
        id: Long = 1L,
        anonima: Boolean = true,
        estado: String = "EN_INVESTIGACION"
    ) = DenunciaDTO(
        id = id,
        categoria = "ACOSO",
        categoriaEtiqueta = "Acoso laboral o sexual",
        descripcion = "Los hechos.",
        estado = estado,
        anonima = anonima,
        denunciante = if (anonima) null else "Ana",
        creadoEn = "2026-09-01T08:00:00Z",
        diasHastaRespuesta = 88,
        mensajes = listOf(
            MensajeDenunciaDTO(
                id = 1L,
                autorRol = "DENUNCIANTE",
                autor = null,
                texto = "Aporto fechas.",
                creadoEn = "2026-09-02T08:00:00Z"
            )
        )
    )

    private fun <T> error(codigo: Int): Response<T> = Response.error(
        codigo, "{}".toResponseBody("application/json".toMediaType()))

    private suspend fun conListaVacia() {
        whenever(repositorio.getMisDenuncias()).thenReturn(Response.success(emptyList()))
    }

    @Test
    fun `el anonimato viaja tal cual se eligio`() = runTest {
        conListaVacia()
        whenever(repositorio.presentarDenuncia(any(), any(), any()))
            .thenReturn(Response.success(
                DenunciaCreadaDTO("codigo-1", true, "2026-09-08T08:00:00Z", "Guárdalo.")))

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.presentar(CategoriaDenuncia.SEGURIDAD, "Salidas bloqueadas.", anonima = true)
        advanceUntilIdle()

        // Sin traducciones ni defectos por el camino: lo que marcó la
        // persona es lo que llega al servidor.
        verify(repositorio).presentarDenuncia("SEGURIDAD", "Salidas bloqueadas.", true)
    }

    @Test
    fun `el codigo se ensena una vez y se suelta al confirmar`() = runTest {
        conListaVacia()
        whenever(repositorio.presentarDenuncia(any(), any(), any()))
            .thenReturn(Response.success(
                DenunciaCreadaDTO("codigo-1", true, "2026-09-08T08:00:00Z", "Guárdalo.")))

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.presentar(CategoriaDenuncia.ACOSO, "Los hechos.", anonima = true)
        advanceUntilIdle()

        assertEquals("codigo-1", vm.uiState.value.recienCreada?.codigoSeguimiento)

        vm.codigoGuardado()

        // A partir de aquí no queda en ninguna parte de la app, y el
        // servidor solo guardó su hash: no hay de dónde sacarlo otra vez.
        assertNull(vm.uiState.value.recienCreada)
        assertNull(vm.uiState.value.codigoAbierto)
    }

    @Test
    fun `una descripcion vacia ni se manda`() = runTest {
        conListaVacia()

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.presentar(CategoriaDenuncia.OTRA, "   ", anonima = false)
        advanceUntilIdle()

        verify(repositorio, never()).presentarDenuncia(any(), any(), any())
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `abierta con codigo, se contesta con codigo`() = runTest {
        conListaVacia()
        whenever(repositorio.getDenunciaPorCodigo("codigo-1"))
            .thenReturn(Response.success(expediente(anonima = true)))
        whenever(repositorio.responderDenunciaPorCodigo(any(), any()))
            .thenReturn(Response.success(expediente(anonima = true)))

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.buscarPorCodigo("codigo-1")
        advanceUntilIdle()
        vm.responder("Aporto más detalles.")
        advanceUntilIdle()

        verify(repositorio).responderDenunciaPorCodigo("codigo-1", "Aporto más detalles.")
        // Nunca por la puerta del titular: en una anónima no hay titular
        // que el servidor pueda reconocer.
        verify(repositorio, never()).responderMiDenuncia(any(), any())
    }

    @Test
    fun `abierta como titular, se contesta como titular`() = runTest {
        whenever(repositorio.getMisDenuncias())
            .thenReturn(Response.success(listOf(resumen(id = 7L))))
        whenever(repositorio.getMiDenuncia(7L))
            .thenReturn(Response.success(expediente(id = 7L, anonima = false)))
        whenever(repositorio.responderMiDenuncia(any(), any()))
            .thenReturn(Response.success(expediente(id = 7L, anonima = false)))

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.abrirMia(7L)
        advanceUntilIdle()
        vm.responder("Ya lo he aportado.")
        advanceUntilIdle()

        verify(repositorio).responderMiDenuncia(7L, "Ya lo he aportado.")
        verify(repositorio, never()).responderDenunciaPorCodigo(any(), any())
    }

    @Test
    fun `cerrar el expediente suelta tambien el codigo`() = runTest {
        conListaVacia()
        whenever(repositorio.getDenunciaPorCodigo("codigo-1"))
            .thenReturn(Response.success(expediente()))

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.buscarPorCodigo("codigo-1")
        advanceUntilIdle()
        assertEquals("codigo-1", vm.uiState.value.codigoAbierto)

        vm.cerrarExpediente()

        // Volver atrás no puede dejarlo en memoria a la espera de que
        // alguien lo reutilice: el usuario ya decidió que lo guarda él.
        assertNull(vm.uiState.value.expediente)
        assertNull(vm.uiState.value.codigoAbierto)
    }

    @Test
    fun `un codigo que no existe deja el error del servidor y ningun expediente`() = runTest {
        conListaVacia()
        whenever(repositorio.getDenunciaPorCodigo(any())).thenReturn(error(404))

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.buscarPorCodigo("inventado")
        advanceUntilIdle()

        // El servidor da el MISMO 404 a un código inventado y a uno de
        // otra empresa, así que la app tampoco distingue: decir "ese
        // código existe pero no es tuyo" sería confirmar que existe.
        assertNull(vm.uiState.value.expediente)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `un codigo en blanco ni se consulta`() = runTest {
        conListaVacia()

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()
        vm.buscarPorCodigo("  ")
        advanceUntilIdle()

        verify(repositorio, never()).getDenunciaPorCodigo(any())
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `mis denuncias solo trae las identificadas, que es lo que manda el servidor`() = runTest {
        // La app no filtra nada: es que las anónimas no pueden salir de
        // la base, porque no hay columna que las relacione con nadie.
        whenever(repositorio.getMisDenuncias())
            .thenReturn(Response.success(listOf(resumen(id = 3L, anonima = false))))

        val vm = DenunciasViewModel(repositorio)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.mias.size)
        assertTrue(vm.uiState.value.mias.none { it.anonima })
    }

    @Test
    fun `presentar recarga la lista, aunque la denuncia fuera anonima y no vaya a aparecer`() =
        runTest {
            conListaVacia()
            whenever(repositorio.presentarDenuncia(any(), any(), eq(true)))
                .thenReturn(Response.success(
                    DenunciaCreadaDTO("codigo-1", true, "2026-09-08T08:00:00Z", "Guárdalo.")))

            val vm = DenunciasViewModel(repositorio)
            advanceUntilIdle()
            vm.presentar(CategoriaDenuncia.ACOSO, "Los hechos.", anonima = true)
            advanceUntilIdle()

            // Dos veces: la de `init` y la de después de presentar. Que
            // la lista siga vacía es parte de lo que hay que enseñar.
            verify(repositorio, org.mockito.kotlin.times(2)).getMisDenuncias()
            assertTrue(vm.uiState.value.mias.isEmpty())
        }
}
