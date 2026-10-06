package com.nxtime.app.interfaz

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nxtime.app.R
import com.nxtime.app.data.dto.PendientesDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.gestion.PanelGestionScreen
import com.nxtime.app.ui.gestion.PanelGestionViewModel
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response

/**
 * El panel de gestión: qué entradas ve cada cual y cuánto tiene pendiente.
 *
 * `PermisosTest` prueba qué puede hacer cada rol. Esto prueba que la pantalla
 * le hace caso: una entrada que se enseña a quien no puede usarla acaba en un
 * 403 al tocarla.
 */
@RunWith(RobolectricTestRunner::class)
class PanelGestionScreenTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val repositorio: AuthRepository = mock {
        onBlocking { getPendientes() } doReturn
            Response.success(PendientesDTO(ausencias = 2, correcciones = 0, horasExtra = 1))
    }
    private val tocadas = mutableListOf<String>()

    private val recursos get() = RuntimeEnvironment.getApplication().resources

    private fun texto(id: Int): String = recursos.getString(id)

    private fun montar(todo: Boolean) {
        // Fuera de la composición: dentro, cada recomposición crearía otro.
        val viewModel = PanelGestionViewModel(repositorio)
        pantalla.setContent {
            NxTimeTheme {
                PanelGestionScreen(
                    contadorAvisos = 0,
                    onIrAvisos = {},
                    iniciales = "A",
                    onIrPerfil = {},
                    puedeCrearGestores = todo,
                    puedeVerPanelEmpresa = todo,
                    onIrPanelEmpresa = { tocadas += "empresa" },
                    onIrProyectos = {},
                    onIrCorrecciones = {},
                    onIrHorasExtra = {},
                    puedeRevisarIncidencias = todo,
                    onIrIncidencias = {},
                    puedeInstruirDenuncias = todo,
                    onIrCanalDenuncias = {},
                    puedePublicarOfertas = todo,
                    onIrGestionOfertas = {},
                    onIrHistorialEquipo = { tocadas += "historial" },
                    onIrPendientes = { tocadas += "pendientes" },
                    onIrResueltas = {},
                    onIrAltaEmpleado = {},
                    onIrAltaGestor = {},
                    puedeGestionarBorrados = todo,
                    onIrBorrados = {},
                    viewModel = viewModel
                )
            }
        }
        pantalla.waitForIdle()
    }

    private val soloDeQuienPuede = listOf(
        R.string.gestion_crear_gestor,
        R.string.empresa_titulo,
        R.string.gestion_incidencias,
        R.string.gestion_borrados,
        R.string.gestion_canal_denuncias,
        R.string.gestion_ofertas
    )

    private val deCualquierGestor = listOf(
        R.string.gestion_ausencias_pendientes,
        R.string.correcciones_titulo,
        R.string.gestion_horas_extra,
        R.string.gestion_historial_equipo,
        R.string.gestion_crear_empleado,
        R.string.proyectos_titulo
    )

    @Test
    fun `un gestor ve lo de su equipo y nada de lo que no puede usar`() {
        montar(todo = false)

        deCualquierGestor.forEach { pantalla.onNodeWithText(texto(it)).assertExists() }
        soloDeQuienPuede.forEach { pantalla.onNodeWithText(texto(it)).assertDoesNotExist() }
    }

    @Test
    fun `quien lo puede todo lo ve todo`() {
        montar(todo = true)

        (deCualquierGestor + soloDeQuienPuede).forEach { pantalla.onNodeWithText(texto(it)).assertExists() }
    }

    @Test
    fun `las bandejas dicen cuanto hay pendiente, y la que no tiene nada no dice nada`() {
        montar(todo = false)

        fun globoDe(cuantos: Int) =
            pantalla.onNodeWithContentDescription(recursos.getQuantityString(R.plurals.gestion_pendientes, cuantos, cuantos))

        globoDe(2).assertExists()
        globoDe(1).assertExists()
        // Correcciones tiene 0: sin globo.
        globoDe(0).assertDoesNotExist()
    }

    @Test
    fun `cada entrada lleva a su sitio`() {
        montar(todo = true)

        pantalla.onNodeWithText(texto(R.string.gestion_ausencias_pendientes)).performScrollTo().performClick()
        pantalla.onNodeWithText(texto(R.string.gestion_historial_equipo)).performScrollTo().performClick()
        pantalla.onNodeWithText(texto(R.string.empresa_titulo)).performScrollTo().performClick()

        assertEquals(listOf("pendientes", "historial", "empresa"), tocadas)
    }
}
