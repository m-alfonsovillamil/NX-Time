package com.nxtime.app.interfaz

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nxtime.app.R
import com.nxtime.app.data.dto.AvisoDePrivacidadDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.privacidad.AvisoDePrivacidadViewModel
import com.nxtime.app.ui.privacidad.DialogoDeAvisoDePrivacidad
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response

/**
 * El aviso de privacidad de la primera entrada (ADR 047), pintado de verdad:
 * dice quién es la responsable y a quién escribir, y con «Entendido» se va y
 * se le dice al servidor. Si ya se vio, no sale.
 */
@RunWith(RobolectricTestRunner::class)
class AvisoDePrivacidadTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val repositorio: AuthRepository = mock()

    private fun texto(id: Int, vararg argumentos: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *argumentos)

    private fun montar(aviso: AvisoDePrivacidadDTO): AvisoDePrivacidadViewModel {
        repositorio.stub {
            onBlocking { getAvisoDePrivacidad() } doReturn Response.success(aviso)
            onBlocking { marcarAvisoDePrivacidadVisto() } doReturn Response.success(Unit)
        }
        val viewModel = AvisoDePrivacidadViewModel(repositorio)
        viewModel.cargar()
        pantalla.setContent {
            NxTimeTheme {
                val pendiente by viewModel.aviso.collectAsStateWithLifecycle()
                pendiente?.let { DialogoDeAvisoDePrivacidad(aviso = it, onEntendido = viewModel::entendido) }
            }
        }
        return viewModel
    }

    @Test
    fun dice_a_quien_escribir_y_con_entendido_se_va() {
        montar(AvisoDePrivacidadDTO(empresa = "Talleres Ana", contacto = "privacidad@talleres.es", visto = false))

        pantalla.onNodeWithText(texto(R.string.privacidad_texto, "Talleres Ana")).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.privacidad_contacto_correo, "privacidad@talleres.es")).assertIsDisplayed()

        pantalla.onNodeWithText(texto(R.string.privacidad_entendido)).performClick()

        pantalla.onNodeWithText(texto(R.string.privacidad_titulo)).assertDoesNotExist()
        verifyBlocking(repositorio) { marcarAvisoDePrivacidadVisto() }
    }

    @Test
    fun sin_contacto_manda_a_la_empresa() {
        montar(AvisoDePrivacidadDTO(empresa = "Talleres Ana", contacto = null, visto = false))

        pantalla.onNodeWithText(texto(R.string.privacidad_sin_contacto)).assertIsDisplayed()
    }

    @Test
    fun si_ya_lo_vio_no_sale() {
        montar(AvisoDePrivacidadDTO(empresa = "Talleres Ana", contacto = null, visto = true))

        pantalla.onNodeWithText(texto(R.string.privacidad_titulo)).assertDoesNotExist()
        verifyBlocking(repositorio, never()) { marcarAvisoDePrivacidadVisto() }
    }
}
