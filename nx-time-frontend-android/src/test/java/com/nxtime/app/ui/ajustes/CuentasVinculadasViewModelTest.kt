package com.nxtime.app.ui.ajustes

import com.nxtime.app.R
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.IdentidadVinculadaDTO
import com.nxtime.app.data.dto.ProveedorSsoDTO
import com.nxtime.app.data.dto.RespuestaAutenticacion
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.sso.AccesoSso
import com.nxtime.app.data.sso.GuardaDelVerificador
import com.nxtime.app.ui.acceso.LoginViewModel
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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

/**
 * «Cuentas vinculadas» en Ajustes: vincular desde la app una cuenta de Google
 * o de Microsoft, y quitarla (ADR 036 y 038).
 *
 * Lo que hay que sostener es lo mismo que al entrar, más una cosa: que la
 * vuelta de VINCULAR y la de ENTRAR no se confundan. Un código de vínculo que
 * acabara en la pantalla de acceso se canjearía como una sesión, y uno de
 * sesión que acabara aquí, como un vínculo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CuentasVinculadasViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val guarda = object : GuardaDelVerificador {
        override var verificador: String? = null
        override var paraVincular: Boolean = false
    }
    private val sso = AccesoSso(guarda)
    private val viewModel by lazy { CuentasVinculadasViewModel(repositorio, sso) }

    private val google = ProveedorSsoDTO("google", "Google", "https://api.nxtime-web.com/auth/sso/google/iniciar")
    private val microsoft =
        ProveedorSsoDTO("microsoft", "Microsoft", "https://api.nxtime-web.com/auth/sso/microsoft/iniciar")
    private val deGoogle = IdentidadVinculadaDTO("google", "Google", "ana.personal@gmail.test")

    private fun <T> rechazo(codigo: Int, detalle: String = "No.") = Response.error<T>(
        codigo,
        """{"status":$codigo,"detail":"$detalle"}""".toResponseBody("application/problem+json".toMediaType())
    )

    private suspend fun conProveedores(vararg mias: IdentidadVinculadaDTO) {
        whenever(repositorio.getProveedoresSso()).thenReturn(Response.success(listOf(google, microsoft)))
        whenever(repositorio.getIdentidadesVinculadas()).thenReturn(Response.success(mias.toList()))
    }

    // ------------------------------------------------------------------
    // Qué se enseña
    // ------------------------------------------------------------------

    @Test
    fun `cada proveedor sale con la cuenta que tiene vinculada o sin ella`() = runTest {
        conProveedores(deGoogle)

        viewModel
        advanceUntilIdle()

        assertEquals(
            listOf(CuentaDeProveedor(google, deGoogle), CuentaDeProveedor(microsoft, null)),
            viewModel.uiState.value.cuentas
        )
    }

    @Test
    fun `sin proveedores no hay tarjeta ni se pregunta por mis cuentas`() = runTest {
        whenever(repositorio.getProveedoresSso()).thenReturn(Response.success(emptyList()))

        viewModel
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.cuentas.isEmpty())
        verify(repositorio, never()).getIdentidadesVinculadas()
    }

    /** Ajustes no puede depender de esto. */
    @Test
    fun `si la carga falla no hay tarjeta ni error`() = runTest {
        whenever(repositorio.getProveedoresSso()).thenThrow(RuntimeException("sin red"))

        viewModel
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.cuentas.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    // ------------------------------------------------------------------
    // Vincular
    // ------------------------------------------------------------------

    @Test
    fun `vincular abre el navegador pidiendo un vinculo y con el reto del verificador guardado`() = runTest {
        conProveedores()
        viewModel
        advanceUntilIdle()

        viewModel.vincular(google)

        assertEquals(
            "${google.inicio}?cliente=app&vincular=1&reto=${AccesoSso.retoDe(guarda.verificador!!)}",
            viewModel.uiState.value.abrirEnNavegador
        )
        assertTrue(guarda.paraVincular)
    }

    @Test
    fun `al volver con un codigo de vinculo se confirma con el verificador y la cuenta queda vinculada`() = runTest {
        conProveedores()
        whenever(repositorio.confirmarVinculoSso(any(), any())).thenReturn(Response.success(listOf(deGoogle)))
        viewModel
        advanceUntilIdle()
        viewModel.vincular(google)
        val verificador = guarda.verificador!!

        sso.recibir(codigo = null, error = null, vinculo = "el-vinculo")
        advanceUntilIdle()

        verify(repositorio).confirmarVinculoSso("el-vinculo", verificador)
        assertEquals(CuentaDeProveedor(google, deGoogle), viewModel.uiState.value.cuentas.first())
        assertEquals(MensajeUi.Recurso(R.string.vinculadas_vinculada), viewModel.uiState.value.aviso)
        // Gastado: ese código y ese verificador no vuelven a servir.
        assertNull(guarda.verificador)
        assertFalse(guarda.paraVincular)
        assertNull(sso.vueltaDeVinculo.value)
    }

    @Test
    fun `si la cuenta ya es de otra persona se dice lo que responde el servidor y no se vincula`() = runTest {
        conProveedores()
        whenever(repositorio.confirmarVinculoSso(any(), any()))
            .thenReturn(rechazo(409, "Esa cuenta ya está vinculada a otra persona de NX Time."))
        viewModel
        advanceUntilIdle()
        viewModel.vincular(google)

        sso.recibir(codigo = null, error = null, vinculo = "el-vinculo")
        advanceUntilIdle()

        assertEquals(
            MensajeUi.Texto("Esa cuenta ya está vinculada a otra persona de NX Time."),
            viewModel.uiState.value.error
        )
        assertNull(viewModel.uiState.value.cuentas.first().vinculada)
        assertFalse(viewModel.uiState.value.trabajando)
    }

    @Test
    fun `si cancela en el navegador se dice y no se confirma nada`() = runTest {
        conProveedores()
        viewModel
        advanceUntilIdle()
        viewModel.vincular(google)

        sso.recibir(codigo = null, error = "cancelado")
        advanceUntilIdle()

        assertEquals(MensajeUi.Recurso(R.string.vinculadas_error_cancelado), viewModel.uiState.value.error)
        verify(repositorio, never()).confirmarVinculoSso(any(), any())
        assertNull(guarda.verificador)
    }

    // ------------------------------------------------------------------
    // Que entrar y vincular no se confundan
    // ------------------------------------------------------------------

    /**
     * Un enlace `nxtime://sso?codigo=…` (de ENTRAR) que llegue mientras se
     * vincula: no es lo que se pidió. No se confirma como vínculo ni se
     * canjea como sesión.
     */
    @Test
    fun `un codigo de entrar que llega mientras se vincula no vincula nada`() = runTest {
        conProveedores()
        viewModel
        advanceUntilIdle()
        viewModel.vincular(google)

        sso.recibir(codigo = "un-codigo-de-entrar", error = null)
        advanceUntilIdle()

        verify(repositorio, never()).confirmarVinculoSso(any(), any())
        verify(repositorio, never()).canjearSso(any(), any())
        assertEquals(MensajeUi.Recurso(R.string.vinculadas_error_fallo), viewModel.uiState.value.error)
    }

    /**
     * Lo que justifica que haya dos flujos: la vuelta de vincular no la ve la
     * pantalla de acceso. En uno solo, al cerrar la sesión el acceso se la
     * encontraría pendiente y la canjearía como un intento de entrar.
     */
    @Test
    fun `la pantalla de acceso no ve la vuelta de vincular`() = runTest {
        whenever(repositorio.getProveedoresSso()).thenReturn(Response.success(listOf(google)))
        sso.empezarAVincular(google.inicio)
        sso.recibir(codigo = null, error = null, vinculo = "el-vinculo")

        val acceso = LoginViewModel(repositorio, sso)
        advanceUntilIdle()

        verify(repositorio, never()).canjearSso(any(), any())
        assertNull(acceso.uiState.value.error)
        assertNull(sso.vuelta.value)
        assertEquals("el-vinculo", sso.vueltaDeVinculo.value?.codigo)
    }

    @Test
    fun `sin una ida pendiente una vuelta de vinculo se ignora`() = runTest {
        conProveedores()
        viewModel
        advanceUntilIdle()

        sso.recibir(codigo = null, error = null, vinculo = "el-vinculo")
        advanceUntilIdle()

        verify(repositorio, never()).confirmarVinculoSso(any(), any())
        assertNull(viewModel.uiState.value.error)
    }

    // ------------------------------------------------------------------
    // Desvincular
    // ------------------------------------------------------------------

    @Test
    fun `desvincular pide confirmacion y solo entonces la quita`() = runTest {
        conProveedores(deGoogle)
        whenever(repositorio.desvincularSso("google")).thenReturn(Response.success(Unit))
        viewModel
        advanceUntilIdle()

        viewModel.pedirDesvincular(google)
        assertEquals(google, viewModel.uiState.value.confirmandoDesvincular)
        verify(repositorio, never()).desvincularSso(any())

        viewModel.desvincular()
        advanceUntilIdle()

        verify(repositorio).desvincularSso("google")
        assertNull(viewModel.uiState.value.cuentas.first().vinculada)
        assertNull(viewModel.uiState.value.confirmandoDesvincular)
        assertEquals(MensajeUi.Recurso(R.string.vinculadas_desvinculada), viewModel.uiState.value.aviso)
    }

    @Test
    fun `cancelar la confirmacion no desvincula`() = runTest {
        conProveedores(deGoogle)
        viewModel
        advanceUntilIdle()

        viewModel.pedirDesvincular(google)
        viewModel.cancelarDesvincular()
        viewModel.desvincular()
        advanceUntilIdle()

        verify(repositorio, never()).desvincularSso(any())
        assertEquals(deGoogle, viewModel.uiState.value.cuentas.first().vinculada)
    }

    @Test
    fun `si desvincular falla la cuenta sigue vinculada y se dice`() = runTest {
        conProveedores(deGoogle)
        whenever(repositorio.desvincularSso("google")).thenReturn(rechazo(500, "Ha ocurrido un error inesperado."))
        viewModel
        advanceUntilIdle()

        viewModel.pedirDesvincular(google)
        viewModel.desvincular()
        advanceUntilIdle()

        assertEquals(deGoogle, viewModel.uiState.value.cuentas.first().vinculada)
        assertTrue(viewModel.uiState.value.error != null)
    }

    @Suppress("unused")
    private val sesion = RespuestaAutenticacion(token = "jwt", refreshToken = "refresh", nombre = "Ana", rol = "EMPLEADO")
}
