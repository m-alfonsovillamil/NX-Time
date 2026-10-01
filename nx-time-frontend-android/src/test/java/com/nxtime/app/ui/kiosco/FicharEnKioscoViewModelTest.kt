package com.nxtime.app.ui.kiosco

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.EstadoKioscoDTO
import com.nxtime.app.data.dto.TarjetaKioscoDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/** El PIN y la tarjeta del kiosco desde el perfil (ADR 033). */
@OptIn(ExperimentalCoroutinesApi::class)
class FicharEnKioscoViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()

    private suspend fun conEstado(tienePin: Boolean = false) {
        whenever(repositorio.getEstadoKiosco()).thenReturn(Response.success(EstadoKioscoDTO(tienePin = tienePin)))
    }

    @Test
    fun `el PIN solo admite cifras y seis como mucho`() = runTest {
        conEstado()
        val vm = FicharEnKioscoViewModel(repositorio)
        advanceUntilIdle()

        vm.cambiarPin("12a3-4567")

        assertEquals("123456", vm.uiState.value.pin)
    }

    @Test
    fun `un PIN corto no llega al servidor`() = runTest {
        conEstado()
        val vm = FicharEnKioscoViewModel(repositorio)
        advanceUntilIdle()

        vm.cambiarPin("12")
        vm.guardarPin()
        advanceUntilIdle()

        assertEquals(MensajeUi.de(R.string.kiosco_pin_invalido), vm.uiState.value.errorPin)
        verify(repositorio, never()).fijarPinKiosco(any())
    }

    @Test
    fun `guardar el PIN lo manda, lo borra del campo y avisa`() = runTest {
        conEstado()
        whenever(repositorio.fijarPinKiosco("4827")).thenReturn(Response.success(EstadoKioscoDTO(tienePin = true)))
        val vm = FicharEnKioscoViewModel(repositorio)
        advanceUntilIdle()

        vm.cambiarPin("4827")
        vm.guardarPin()
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado.estado!!.tienePin)
        assertEquals("", estado.pin)
        assertEquals(MensajeUi.de(R.string.kiosco_pin_guardado), estado.aviso)
        assertFalse(estado.guardando)
    }

    @Test
    fun `la tarjeta solo se pide al querer verla`() = runTest {
        conEstado()
        whenever(repositorio.getTarjetaKiosco())
            .thenReturn(Response.success(TarjetaKioscoDTO(7, "Lucía", "NXK1.7.1.abcdef0123456789")))
        val vm = FicharEnKioscoViewModel(repositorio)
        advanceUntilIdle()
        verify(repositorio, never()).getTarjetaKiosco()

        vm.verTarjeta()
        advanceUntilIdle()
        assertEquals("NXK1.7.1.abcdef0123456789", vm.uiState.value.tarjeta?.codigo)

        vm.ocultarTarjeta()
        assertNull(vm.uiState.value.tarjeta)
    }

    @Test
    fun `el QR que se pinta se lee de vuelta con el mismo codigo`() {
        val codigo = "NXK1.7.1.abcdef0123456789"
        val modulos = CodigoQr.modulos(codigo)
        // Cada módulo como un cuadrado de 8×8 píxeles, como en una foto: a un
        // píxel por módulo el lector no encuentra las esquinas.
        val escala = 8
        val ancho = modulos.size * escala
        val pixeles = IntArray(ancho * ancho) { i ->
            val x = (i % ancho) / escala
            val y = (i / ancho) / escala
            if (modulos[y][x]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val leido = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(ancho, ancho, pixeles)))
        )

        assertEquals(codigo, leido.text)
    }
}
