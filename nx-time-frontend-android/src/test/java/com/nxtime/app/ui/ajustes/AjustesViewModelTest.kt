package com.nxtime.app.ui.ajustes

import com.nxtime.app.ReglaDispatcherPrincipal
import com.nxtime.app.data.dto.PerfilDTO
import com.nxtime.app.data.dto.PeticionLogin
import com.nxtime.app.data.dto.RespuestaAutenticacion
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

    private fun viewModel(
        tema: Tema = Tema.SISTEMA,
        informes: Boolean = true,
        huella: Boolean = false
    ): AjustesViewModel {
        whenever(ajustes.tema).thenReturn(MutableStateFlow(tema))
        whenever(ajustes.informesDeErrores).thenReturn(MutableStateFlow(informes))
        // El ViewModel lee las tres al construirse: si alguna se queda sin
        // simular, revienta antes del primer assert.
        whenever(ajustes.huella).thenReturn(MutableStateFlow(huella))
        return AjustesViewModel(repositorio, ajustes)
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
        whenever(repositorio.getMiPerfil()).thenReturn(Response.success(perfil()))
        whenever(repositorio.login(any())).thenReturn(Response.success(mock<RespuestaAutenticacion>()))
        val vm = viewModel()

        vm.cambiarHuella(true)
        vm.confirmarHuellaCon("demo1234")
        advanceUntilIdle()

        // El correo sale del perfil: SessionManager guarda nombre y rol,
        // pero no el correo.
        verify(repositorio).login(eq(PeticionLogin("marta@techcorp.demo", "demo1234")))
        verify(ajustes).cambiarHuella(true)
        assertTrue(vm.uiState.value.huella)
        assertFalse(vm.uiState.value.confirmandoHuella)
    }

    @Test
    fun `con la contrasena equivocada no se activa y se explica`() = runTest {
        whenever(repositorio.getMiPerfil()).thenReturn(Response.success(perfil()))
        whenever(repositorio.login(any())).thenReturn(
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
}
