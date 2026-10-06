package com.nxtime.app.interfaz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nxtime.app.R
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.TipoFichaje
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.fichar.DetalleDeTiempoViewModel
import com.nxtime.app.ui.fichar.FicharScreen
import com.nxtime.app.ui.fichar.FicharViewModel
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response
import java.time.Instant

/**
 * «Mi jornada» pintada y tocada: una jornada entera, de entrar a salir.
 *
 * El servidor es un doble que se comporta como el de verdad en lo que importa
 * aquí: guarda la jornada abierta y responde a cada fichaje con cómo queda.
 * Así el test no afirma «se llamó a tal método», sino lo que ve la persona
 * después de cada toque.
 */
@RunWith(RobolectricTestRunner::class)
class FicharScreenTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val repositorio: AuthRepository = mock()
    private val sesion: SessionManager = mock { on { fetchUserName() } doReturn "Ana" }

    /** La jornada que tiene el «servidor», y lo que se le ha pedido. */
    private var jornada: Registro? = null
    private val fichajes = mutableListOf<TipoFichaje>()

    private fun texto(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun montar(abierta: Registro? = null) {
        jornada = abierta
        repositorio.stub {
            onBlocking { getRegistroActivo() } doAnswer { Response.success(jornada) }
            onBlocking { registrarFichaje(any()) } doAnswer { llamada ->
                val tipo = llamada.getArgument<PeticionFichaje>(0).tipo
                fichajes += tipo
                val ahora = Instant.now().toString()
                val nueva = when (tipo) {
                    TipoFichaje.INICIO -> Registro(id = 1, horaEntrada = ahora, horaSalida = null, enPausa = false)
                    TipoFichaje.PAUSA_INICIO -> jornada!!.copy(enPausa = true)
                    TipoFichaje.PAUSA_FIN -> jornada!!.copy(enPausa = false)
                    TipoFichaje.FIN -> jornada!!.copy(horaSalida = ahora)
                }
                jornada = nueva.takeIf { it.horaSalida == null }
                Response.success(nueva)
            }
        }
        // Fuera de la composición: dentro, cada recomposición crearía otros.
        val viewModel = FicharViewModel(repositorio, sesion)
        val detalleViewModel = DetalleDeTiempoViewModel(repositorio)
        pantalla.setContent {
            NxTimeTheme {
                FicharScreen(
                    onIrSolicitud = {},
                    onAnadirPausa = {},
                    onIrPerfil = {},
                    contadorAvisos = 0,
                    onIrAvisos = {},
                    iniciales = "A",
                    onIrCuadrante = {},
                    viewModel = viewModel,
                    detalleViewModel = detalleViewModel
                )
            }
        }
    }

    private fun tocar(id: Int) {
        pantalla.onNodeWithText(texto(id)).performClick()
        pantalla.waitForIdle()
    }

    private fun seVe(id: Int) = pantalla.onNodeWithText(texto(id)).assertIsDisplayed()

    private fun noEsta(id: Int) = pantalla.onNodeWithText(texto(id)).assertDoesNotExist()

    @Test
    fun `una jornada entera - entrar, pausar, reanudar y salir confirmando`() {
        montar()
        seVe(R.string.fichar_iniciar)
        // Sin jornada no hay nada que pausar.
        noEsta(R.string.fichar_pausar)

        tocar(R.string.fichar_iniciar)
        seVe(R.string.fichar_finalizar)
        seVe(R.string.fichar_pausar)

        tocar(R.string.fichar_pausar)
        seVe(R.string.fichar_reanudar)

        tocar(R.string.fichar_reanudar)
        seVe(R.string.fichar_pausar)

        // Terminar se pregunta: cerrar por error cuesta una corrección aprobada.
        tocar(R.string.fichar_finalizar)
        seVe(R.string.fichar_confirmar_fin_titulo)
        assertEquals("todavía no se ha mandado la salida", false, TipoFichaje.FIN in fichajes)

        tocar(R.string.fichar_confirmar_fin_si)
        seVe(R.string.fichar_iniciar)

        assertEquals(
            listOf(TipoFichaje.INICIO, TipoFichaje.PAUSA_INICIO, TipoFichaje.PAUSA_FIN, TipoFichaje.FIN),
            fichajes
        )
    }

    @Test
    fun `decir que sigo trabajando cierra la pregunta y no ficha la salida`() {
        montar(abierta = Registro(id = 1, horaEntrada = Instant.now().toString(), horaSalida = null, enPausa = false))

        tocar(R.string.fichar_finalizar)
        tocar(R.string.fichar_confirmar_fin_no)

        noEsta(R.string.fichar_confirmar_fin_titulo)
        seVe(R.string.fichar_finalizar)
        assertEquals(emptyList<TipoFichaje>(), fichajes)
    }

    @Test
    fun `en pausa no deja terminar - pide reanudar y no ficha nada`() {
        montar(abierta = Registro(id = 1, horaEntrada = Instant.now().toString(), horaSalida = null, enPausa = true))
        seVe(R.string.fichar_reanudar)

        tocar(R.string.fichar_finalizar)

        seVe(R.string.fichar_reanuda_antes)
        noEsta(R.string.fichar_confirmar_fin_titulo)
        assertEquals(emptyList<TipoFichaje>(), fichajes)
    }
}
