package com.nxtime.app.interfaz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nxtime.app.R
import com.nxtime.app.data.dto.EstadoDelDiaDTO
import com.nxtime.app.data.dto.EstadoUbicacion
import com.nxtime.app.data.dto.Modalidad
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.UbicacionDelFichaje
import com.nxtime.app.data.session.Ajustes
import com.nxtime.app.data.sinconexion.ColaEnMemoria
import com.nxtime.app.data.sinconexion.EntornoFalso
import com.nxtime.app.data.ubicacion.FuenteDeUbicacion
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.TipoFichaje
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.fichar.DetalleDeTiempoViewModel
import com.nxtime.app.ui.fichar.FicharScreen
import com.nxtime.app.ui.fichar.FicharViewModel
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun montar(abierta: Registro? = null, pide: EstadoDelDiaDTO? = null) {
        jornada = abierta
        repositorio.stub {
            pide?.let { dia -> onBlocking { getEstadoDeHoy() } doReturn Response.success(dia) }
            onBlocking { getRegistroActivo() } doAnswer { Response.success(jornada) }
            onBlocking { registrarFichaje(any()) } doAnswer { llamada ->
                val peticion = llamada.getArgument<PeticionFichaje>(0)
                val tipo = peticion.tipo
                fichajes += tipo
                val ahora = Instant.now().toString()
                val nueva = when (tipo) {
                    TipoFichaje.INICIO -> Registro(
                        id = 1, horaEntrada = ahora, horaSalida = null, enPausa = false,
                        modalidad = peticion.modalidad, ubicacionEntrada = peticion.ubicacion
                    )
                    TipoFichaje.PAUSA_INICIO -> jornada!!.copy(enPausa = true)
                    TipoFichaje.PAUSA_FIN -> jornada!!.copy(enPausa = false)
                    TipoFichaje.FIN -> jornada!!.copy(horaSalida = ahora, ubicacionSalida = peticion.ubicacion)
                }
                jornada = nueva.takeIf { it.horaSalida == null }
                Response.success(nueva)
            }
        }
        // Fuera de la composición: dentro, cada recomposición crearía otros.
        val viewModel = FicharViewModel(
            repositorio,
            sesion,
            Ajustes(RuntimeEnvironment.getApplication()),
            FuenteDeUbicacion { UbicacionDelFichaje(EstadoUbicacion.CAPTURADA, 42.2406, -8.7207, 25) },
            ColaEnMemoria(),
            EntornoFalso()
        )
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

    /**
     * En pausa, el botón grande decía «Finalizar jornada» y solo avisaba de que
     * había que reanudar antes. Ahora dice «Reanudar» y reanuda; «Pausar» no está.
     */
    @Test
    fun `en pausa el boton grande reanuda y no hay otro boton`() {
        montar(abierta = Registro(id = 1, horaEntrada = Instant.now().toString(), horaSalida = null, enPausa = true))
        seVe(R.string.fichar_reanudar)
        noEsta(R.string.fichar_finalizar)
        noEsta(R.string.fichar_pausar)

        tocar(R.string.fichar_reanudar)

        assertEquals(listOf(TipoFichaje.PAUSA_FIN), fichajes)
        seVe(R.string.fichar_finalizar)
        seVe(R.string.fichar_pausar)
    }

    /** Antes decía la hora de la entrada: la app no tenía la de la pausa. */
    @Test
    fun `en pausa dice desde cuando es la pausa, no la entrada`() {
        montar(
            abierta = Registro(
                id = 1, horaEntrada = "2026-08-29T07:00:00Z", horaSalida = null, enPausa = true,
                inicioPausaActual = "2026-08-29T09:30:00Z"
            )
        )

        val hora = com.nxtime.app.ui.util.DateFormats.hora("2026-08-29T09:30:00Z")
        assertTrue(hora.startsWith("11:30")) // 09:30 UTC son las 11:30 en Madrid en agosto
        pantalla.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.fichar_en_pausa, hora))
            .assertIsDisplayed()
    }

    /** ADR 055 y 056: lo que ve quien entra si su empresa pide las dos cosas. */
    @Test
    fun `si la empresa pide ubicacion y modalidad se explica y se pregunta antes de fichar`() {
        montar(pide = EstadoDelDiaDTO(laborable = true, preguntarModalidad = true, pedirUbicacion = true))
        pantalla.waitForIdle()

        tocar(R.string.fichar_iniciar)
        seVe(R.string.ubicacion_explicar_titulo)
        assertEquals(emptyList<TipoFichaje>(), fichajes)

        // Sin la explicación no se llega a nada más: cancelar no ficha.
        tocar(R.string.cancelar)
        noEsta(R.string.ubicacion_explicar_titulo)
        assertEquals(emptyList<TipoFichaje>(), fichajes)
    }

    @Test
    fun `presencial o a distancia se pregunta con dos botones y ficha`() {
        Ajustes(RuntimeEnvironment.getApplication()).ubicacionExplicada = true
        montar(pide = EstadoDelDiaDTO(laborable = true, preguntarModalidad = true))
        pantalla.waitForIdle()

        tocar(R.string.fichar_iniciar)
        seVe(R.string.modalidad_titulo)
        tocar(R.string.modalidad_a_distancia)

        pantalla.waitForIdle()
        assertEquals(listOf(TipoFichaje.INICIO), fichajes)
        assertEquals(Modalidad.A_DISTANCIA, Ajustes(RuntimeEnvironment.getApplication()).ultimaModalidad)
    }

    /**
     * Lo que se guardó, a la vista: el servidor ya guardaba modalidad y
     * ubicación, pero quien fichaba no sabía si el permiso había funcionado.
     */
    @Test
    fun `tras fichar se ve la modalidad y la ubicacion de la entrada, y luego la de la salida`() {
        Ajustes(RuntimeEnvironment.getApplication()).ubicacionExplicada = true
        montar(pide = EstadoDelDiaDTO(laborable = true, preguntarModalidad = true, pedirUbicacion = true))
        pantalla.waitForIdle()

        tocar(R.string.fichar_iniciar)
        tocar(R.string.modalidad_presencial)
        val conUbicacion = RuntimeEnvironment.getApplication().getString(R.string.fichar_con_ubicacion_precision, 25)
        pantalla.onNodeWithText("${texto(R.string.modalidad_presencial)} · $conUbicacion").assertIsDisplayed()

        tocar(R.string.fichar_finalizar)
        tocar(R.string.fichar_confirmar_fin_si)
        pantalla.onNodeWithText(conUbicacion).assertIsDisplayed()
        pantalla.onNodeWithText(
            RuntimeEnvironment.getApplication().getString(R.string.fichar_salida_fichada, "")
                .trim(), substring = true
        ).assertIsDisplayed()
    }
}
