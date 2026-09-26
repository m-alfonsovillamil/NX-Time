package com.nxtime.app.ui.ajustes

import com.nxtime.app.push.RegistroDePush
import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PerfilDTO
import com.nxtime.app.data.dto.PeticionLogin
import com.nxtime.app.data.dto.RespuestaAutenticacion
import com.nxtime.app.data.dto.SolicitudBorradoDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.Ajustes
import com.nxtime.app.data.session.Tema
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Ajustes de la aplicación.
 *
 * Lo que se fija aquí es que **la preferencia no vive en el ViewModel**:
 * se escribe en [Ajustes], que es quien la guarda y la publica para toda
 * la aplicación. Y que cerrar la sesión en todos los dispositivos solo
 * saca al usuario **si el servidor lo confirma**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {

    @get:Rule
    val reglaDispatcher = ReglaDispatcherPrincipal()

    private val repositorio: AuthRepository = mock()
    private val ajustes: Ajustes = mock()
    private val registroDePush: RegistroDePush = mock()

    private fun viewModel(
        tema: Tema = Tema.SISTEMA,
        informes: Boolean = true,
        huella: Boolean = false
    ): AjustesViewModel {
        whenever(ajustes.tema).thenReturn(MutableStateFlow(tema))
        whenever(ajustes.informesDeErrores).thenReturn(MutableStateFlow(informes))
        // El ViewModel las lee TODAS al construirse: si alguna se queda sin
        // simular, revienta antes del primer assert. Ya pasó al añadir la
        // huella, otra vez al añadir el recordatorio, y otra con el push (B5).
        whenever(ajustes.huella).thenReturn(MutableStateFlow(huella))
        whenever(ajustes.recordatorio).thenReturn(MutableStateFlow(false))
        whenever(ajustes.horaEntrada).thenReturn(MutableStateFlow("09:30"))
        whenever(ajustes.horaSalida).thenReturn(MutableStateFlow("18:30"))
        whenever(ajustes.push).thenReturn(MutableStateFlow(false))
        return AjustesViewModel(repositorio, ajustes, registroDePush)
    }

    @Test
    fun `encender el push registra el movil, y apagarlo lo da de baja`() = runTest {
        val vm = viewModel()

        vm.cambiarPush(true)
        assertTrue(vm.uiState.value.push)
        verify(registroDePush).encender()

        vm.cambiarPush(false)
        assertFalse(vm.uiState.value.push)
        verify(registroDePush).apagar()
    }

    @Test
    fun `el estado arranca con lo que ya estaba guardado`() = runTest {
        val vm = viewModel(tema = Tema.OSCURO, informes = false)

        assertEquals(Tema.OSCURO, vm.uiState.value.tema)
        assertFalse(vm.uiState.value.informesDeErrores)
    }

    @Test
    fun `cambiar el tema lo guarda en los ajustes, no solo en la pantalla`() = runTest {
        val vm = viewModel()

        vm.cambiarTema(Tema.OSCURO)

        // Si solo cambiara el estado del ViewModel, el tema se perdería al
        // salir de la pantalla y MainActivity no se enteraría.
        verify(ajustes).cambiarTema(Tema.OSCURO)
        assertEquals(Tema.OSCURO, vm.uiState.value.tema)
    }

    @Test
    fun `apagar los informes de errores queda guardado`() = runTest {
        val vm = viewModel()

        vm.cambiarInformesDeErrores(false)

        verify(ajustes).cambiarInformesDeErrores(false)
        assertFalse(vm.uiState.value.informesDeErrores)
    }

    private fun perfil() = PerfilDTO(
        id = 1L,
        email = "marta@techcorp.demo",
        nombre = "Marta",
        nombreCompleto = "Marta Sánchez",
        iniciales = "MS",
        rol = "GESTOR"
    )

    @Test
    fun `activar la huella no la guarda hasta confirmar la contrasena`() = runTest {
        val vm = viewModel()

        vm.cambiarHuella(true)
        advanceUntilIdle()

        // Si se guardara al pulsar, a quien cogiera el móvil desbloqueado
        // le bastaría con su propia huella para blindar la cuenta de otro.
        verify(ajustes, never()).cambiarHuella(any())
        assertTrue(vm.uiState.value.confirmandoHuella)
        assertFalse(vm.uiState.value.huella)
    }

    @Test
    fun `con la contrasena correcta se activa, y se verifica contra el servidor`() = runTest {
        whenever(repositorio.verificarContrasena(any())).thenReturn(Response.success(Unit))
        val vm = viewModel()

        vm.cambiarHuella(true)
        vm.confirmarHuellaCon("demo1234")
        advanceUntilIdle()

        // Un endpoint que solo dice si o no, no un login completo (A11): hacer
        // login emitia tokens nuevos, dejaba vivo el refresh anterior en el
        // servidor y gastaba el limite de intentos del login.
        verify(repositorio).verificarContrasena("demo1234")
        verify(repositorio, never()).login(any())
        // Y ya no hace falta consultar el perfil para saber el correo: el
        // servidor comprueba contra quien trae el token.
        verify(repositorio, never()).getMiPerfil()
        verify(ajustes).cambiarHuella(true)
        assertTrue(vm.uiState.value.huella)
        assertFalse(vm.uiState.value.confirmandoHuella)
    }

    @Test
    fun `con la contrasena equivocada no se activa y se explica`() = runTest {
        whenever(repositorio.verificarContrasena(any())).thenReturn(
            Response.error(401, "{}".toResponseBody("application/json".toMediaType()))
        )
        val vm = viewModel()

        vm.cambiarHuella(true)
        vm.confirmarHuellaCon("la-que-no-es")
        advanceUntilIdle()

        verify(ajustes, never()).cambiarHuella(any())
        assertFalse(vm.uiState.value.huella)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `desactivar la huella no pide contrasena`() = runTest {
        val vm = viewModel(huella = true)

        vm.cambiarHuella(false)
        advanceUntilIdle()

        // Asimetría deliberada: quitarla solo deja las cosas como estaban,
        // y bloquear esa salida sería encerrar a alguien fuera de su cuenta.
        verify(ajustes).cambiarHuella(false)
        verify(repositorio, never()).login(any())
        assertFalse(vm.uiState.value.huella)
    }

    @Test
    fun `encender el recordatorio lo guarda en los ajustes`() = runTest {
        val vm = viewModel()

        vm.cambiarRecordatorio(true)

        verify(ajustes).cambiarRecordatorio(true)
        assertTrue(vm.uiState.value.recordatorio)
    }

    @Test
    fun `una hora mal escrita no se guarda y se explica`() = runTest {
        val vm = viewModel()

        vm.cambiarHoras("25:70", "18:30")

        // Guardarla dejaría el trabajo programado a una hora que no existe:
        // no avisaría nunca y no habría forma de saber por qué.
        verify(ajustes, never()).cambiarHoras(any(), any())
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `unas horas correctas se guardan`() = runTest {
        val vm = viewModel()

        vm.cambiarHoras("08:00", "17:15")

        verify(ajustes).cambiarHoras("08:00", "17:15")
        assertEquals("08:00", vm.uiState.value.horaEntrada)
        assertEquals("17:15", vm.uiState.value.horaSalida)
    }

    @Test
    fun `cerrar sesion en todos los dispositivos saca al usuario cuando el servidor lo confirma`() = runTest {
        whenever(repositorio.cerrarTodasLasSesiones()).thenReturn(Response.success(Unit))
        val vm = viewModel()
        var salido = false

        vm.cerrarTodasLasSesiones { salido = true }
        advanceUntilIdle()

        verify(repositorio).cerrarTodasLasSesiones()
        assertTrue(salido)
        assertFalse(vm.uiState.value.cerrandoSesiones)
    }

    @Test
    fun `si el servidor falla no se sale de la sesion y se explica`() = runTest {
        whenever(repositorio.cerrarTodasLasSesiones()).thenReturn(
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
        )
        val vm = viewModel()
        var salido = false

        vm.cerrarTodasLasSesiones { salido = true }
        advanceUntilIdle()

        // Sacar al usuario sin haber revocado nada sería mentirle: creería
        // que las otras sesiones están cerradas y seguirían abiertas.
        assertFalse(salido)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.cerrandoSesiones)
    }

    @Test
    fun `dos pulsaciones seguidas no mandan dos peticiones`() = runTest {
        whenever(repositorio.cerrarTodasLasSesiones()).thenReturn(Response.success(Unit))
        val vm = viewModel()

        vm.cerrarTodasLasSesiones { }
        vm.cerrarTodasLasSesiones { }
        advanceUntilIdle()

        // La segunda cae en el guardia de 'cerrandoSesiones': revocar dos
        // veces no rompe nada, pero el doble toque es un accidente común y
        // la segunda respuesta llegaría con la sesión ya cerrada.
        verify(repositorio, times(1)).cerrarTodasLasSesiones()
    }

    // ------------------------------------------------------------------
    // Descargar mis datos (RGPD)
    // ------------------------------------------------------------------

    @Test
    fun `descargar en PDF pide el PDF y entrega el cuerpo con su nombre`() = runTest {
        val cuerpo = "%PDF".toResponseBody("application/pdf".toMediaType())
        whenever(repositorio.descargarMisDatosPdf()).thenReturn(Response.success(cuerpo))
        val vm = viewModel()
        var nombreRecibido: String? = null

        vm.descargarMisDatos(FormatoDeExportacion.PDF) { _, nombre -> nombreRecibido = nombre }
        advanceUntilIdle()

        verify(repositorio).descargarMisDatosPdf()
        verify(repositorio, never()).descargarMisDatosJson()
        assertEquals("nxtime-mis-datos.pdf", nombreRecibido)
        assertFalse(vm.uiState.value.descargandoDatos)
    }

    @Test
    fun `si el servidor falla, se dice y no se entrega nada`() = runTest {
        whenever(repositorio.descargarMisDatosJson()).thenReturn(
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
        )
        val vm = viewModel()
        var entregado = false

        vm.descargarMisDatos(FormatoDeExportacion.JSON) { _, _ -> entregado = true }
        advanceUntilIdle()

        assertFalse(entregado)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.descargandoDatos)
    }

    /*
     * Un segundo toque mientras se prepara no lanza otra descarga: con todos
     * los datos de alguien, dos peticiones seguidas son trabajo doble para el
     * servidor y dos visores abriéndose.
     */
    @Test
    fun `un segundo toque mientras descarga no pide otra vez`() = runTest {
        val cuerpo = "{}".toResponseBody("application/json".toMediaType())
        whenever(repositorio.descargarMisDatosJson()).thenReturn(Response.success(cuerpo))
        val vm = viewModel()

        vm.descargarMisDatos(FormatoDeExportacion.JSON) { _, _ -> }
        vm.descargarMisDatos(FormatoDeExportacion.JSON) { _, _ -> }
        advanceUntilIdle()

        verify(repositorio, times(1)).descargarMisDatosJson()
    }

    // ------------------------------------------------------------------
    // Borrado de datos (ADR 016)
    // ------------------------------------------------------------------

    private fun solicitud(estado: String, comentario: String? = null) = SolicitudBorradoDTO(
        id = 3L, usuarioId = 10L, nombre = "Ana", email = "ana@test", estado = estado,
        creadaEn = "2026-09-17T08:00:00Z", comentarioResolucion = comentario
    )

    @Test
    fun `sin ninguna solicitud previa (204) no hay nada que enseñar ni error`() = runTest {
        whenever(repositorio.getMiSolicitudBorrado()).thenReturn(Response.success<SolicitudBorradoDTO>(204, null))
        val vm = viewModel()

        vm.cargarSolicitudBorrado()
        advanceUntilIdle()

        assertNull(vm.uiState.value.solicitudBorrado)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `pedir el borrado abre la explicacion, y solo al confirmar se envia`() = runTest {
        whenever(repositorio.solicitarBorrado(any())).thenReturn(Response.success(solicitud("PENDIENTE")))
        val vm = viewModel()

        vm.pedirBorrado()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmandoBorrado)
        verify(repositorio, never()).solicitarBorrado(any())

        vm.confirmarBorrado("Me voy")
        advanceUntilIdle()

        verify(repositorio).solicitarBorrado(eq("Me voy"))
        assertFalse(vm.uiState.value.confirmandoBorrado)
        assertTrue(vm.uiState.value.solicitudBorrado!!.pendiente)
    }

    /*
     * Si el servidor dice que no (por ejemplo, ya hay una pendiente), el
     * diálogo se queda abierto con el error: cerrarlo perdería lo escrito.
     */
    @Test
    fun `si el servidor rechaza la solicitud el dialogo sigue abierto con el error`() = runTest {
        whenever(repositorio.solicitarBorrado(any())).thenReturn(
            Response.error(409, """{"detail":"Ya tienes una solicitud de borrado pendiente."}"""
                .toResponseBody("application/problem+json".toMediaType()))
        )
        val vm = viewModel()

        vm.pedirBorrado()
        vm.confirmarBorrado("")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.confirmandoBorrado)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.enviandoBorrado)
    }

    @Test
    fun `retirar la solicitud refleja el estado que devuelve el servidor`() = runTest {
        whenever(repositorio.cancelarBorrado()).thenReturn(Response.success(solicitud("CANCELADA")))
        val vm = viewModel()

        vm.retirarBorrado()
        advanceUntilIdle()

        assertEquals("CANCELADA", vm.uiState.value.solicitudBorrado!!.estado)
        assertFalse(vm.uiState.value.solicitudBorrado!!.pendiente)
    }
}
