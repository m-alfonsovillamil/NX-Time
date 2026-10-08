package com.nxtime.app.interfaz

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nxtime.app.R
import com.nxtime.app.data.dto.IdentidadVinculadaDTO
import com.nxtime.app.data.dto.ProveedorSsoDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.sso.AccesoSso
import com.nxtime.app.data.sso.GuardaDelVerificador
import com.nxtime.app.ui.ajustes.CuentasVinculadas
import com.nxtime.app.ui.ajustes.CuentasVinculadasViewModel
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response

/**
 * La tarjeta «Cuentas vinculadas» de Ajustes, pintada de verdad y tocada.
 *
 * `CuentasVinculadasViewModelTest` prueba qué decide el ViewModel; esto, que
 * la tarjeta lo enseña, que no ocupa sitio cuando no hay proveedores y que
 * desvincular pasa por su confirmación.
 */
@RunWith(RobolectricTestRunner::class)
class CuentasVinculadasTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val repositorio: AuthRepository = mock()
    private val guarda = object : GuardaDelVerificador {
        override var verificador: String? = null
        override var paraVincular: Boolean = false
    }

    private val google = ProveedorSsoDTO("google", "Google", "https://api.nxtime-web.com/auth/sso/google/iniciar")
    private val microsoft =
        ProveedorSsoDTO("microsoft", "Microsoft", "https://api.nxtime-web.com/auth/sso/microsoft/iniciar")

    private fun texto(id: Int, vararg argumentos: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *argumentos)

    private fun montar() {
        // Fuera de la composición: dentro, cada recomposición crearía otro.
        val viewModel = CuentasVinculadasViewModel(repositorio, AccesoSso(guarda))
        pantalla.setContent {
            NxTimeTheme {
                Column {
                    Text("antes")
                    CuentasVinculadas(viewModel = viewModel)
                    Text("después")
                }
            }
        }
    }

    @Test
    fun `sin proveedores la tarjeta no se pinta`() {
        repositorio.stub { onBlocking { getProveedoresSso() } doReturn Response.success(emptyList()) }

        montar()

        pantalla.onNodeWithText("antes").assertIsDisplayed()
        pantalla.onNodeWithText("después").assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.vinculadas_titulo)).assertDoesNotExist()
    }

    @Test
    fun `cada proveedor sale con su cuenta o con el boton de vincular`() {
        repositorio.stub {
            onBlocking { getProveedoresSso() } doReturn Response.success(listOf(google, microsoft))
            onBlocking { getIdentidadesVinculadas() } doReturn
                Response.success(listOf(IdentidadVinculadaDTO("google", "Google", "ana.personal@gmail.test")))
        }

        montar()

        pantalla.onNodeWithText(texto(R.string.vinculadas_titulo)).assertIsDisplayed()
        pantalla.onNodeWithText("ana.personal@gmail.test").assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.vinculadas_desvincular, "Google")).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.vinculadas_sin_vincular)).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.vinculadas_vincular, "Microsoft")).assertIsDisplayed()
    }

    @Test
    fun `vincular guarda el verificador de una ida para vincular`() {
        repositorio.stub {
            onBlocking { getProveedoresSso() } doReturn Response.success(listOf(google))
            onBlocking { getIdentidadesVinculadas() } doReturn Response.success(emptyList())
        }
        montar()

        pantalla.onNodeWithText(texto(R.string.vinculadas_vincular, "Google")).performClick()
        pantalla.waitForIdle()

        assertTrue(guarda.verificador != null)
        assertTrue(guarda.paraVincular)
    }

    @Test
    fun `desvincular pregunta antes y cancelar no quita nada`() {
        repositorio.stub {
            onBlocking { getProveedoresSso() } doReturn Response.success(listOf(google))
            onBlocking { getIdentidadesVinculadas() } doReturn
                Response.success(listOf(IdentidadVinculadaDTO("google", "Google", "ana.personal@gmail.test")))
            onBlocking { desvincularSso(any()) } doReturn Response.success(Unit)
        }
        montar()

        pantalla.onNodeWithText(texto(R.string.vinculadas_desvincular, "Google")).performClick()
        pantalla.onNodeWithText(texto(R.string.vinculadas_confirmar_titulo, "Google")).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.cancelar)).performClick()

        verifyBlocking(repositorio, never()) { desvincularSso(any()) }
        pantalla.onNodeWithText("ana.personal@gmail.test").assertIsDisplayed()

        // Y confirmando, sí.
        pantalla.onNodeWithText(texto(R.string.vinculadas_desvincular, "Google")).performClick()
        pantalla.onNodeWithText(texto(R.string.vinculadas_confirmar_si)).performClick()
        pantalla.waitForIdle()

        verifyBlocking(repositorio) { desvincularSso("google") }
        pantalla.onNodeWithText(texto(R.string.vinculadas_vincular, "Google")).assertIsDisplayed()
    }
}
