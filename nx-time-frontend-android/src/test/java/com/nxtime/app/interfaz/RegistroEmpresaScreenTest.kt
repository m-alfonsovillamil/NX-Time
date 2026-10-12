package com.nxtime.app.interfaz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.nxtime.app.R
import com.nxtime.app.data.dto.PeticionRegistroSso
import com.nxtime.app.data.dto.ProveedorSsoDTO
import com.nxtime.app.data.dto.RegistroPendienteSsoDTO
import com.nxtime.app.data.dto.RespuestaAutenticacion
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.sso.AccesoSso
import com.nxtime.app.data.sso.GuardaDelVerificador
import com.nxtime.app.ui.acceso.LoginScreen
import com.nxtime.app.ui.acceso.LoginViewModel
import com.nxtime.app.ui.acceso.RegistroEmpresaScreen
import com.nxtime.app.ui.acceso.RegistroEmpresaViewModel
import com.nxtime.app.ui.theme.NxTimeTheme
import org.junit.Assert.assertEquals
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
import org.robolectric.Shadows.shadowOf
import retrofit2.Response

/**
 * Registrar una empresa con una cuenta de Google o de Microsoft (ADR 044),
 * con la pantalla pintada de verdad.
 *
 * `RegistroConSsoTest` prueba qué decide el ViewModel. Esto, lo que ve y toca
 * la persona: que el botón sale al navegador, y que al volver el formulario
 * dice con qué cuenta va y deja de pedir correo y contraseña.
 */
@RunWith(RobolectricTestRunner::class)
class RegistroEmpresaScreenTest {

    @get:Rule
    val pantalla = createComposeRule()

    private val repositorio: AuthRepository = mock()
    private var registros = 0
    private var idasAlRegistro = 0

    private val guarda = object : GuardaDelVerificador {
        override var verificador: String? = null
        override var paraVincular: Boolean = false
        override var paraRegistrar: Boolean = false
    }
    private val sso = AccesoSso(guarda)

    private val google = ProveedorSsoDTO("google", "Google", "https://api.nxtime-web.com/auth/sso/google/iniciar")
    private val sesion = RespuestaAutenticacion(token = "jwt", refreshToken = "refresh", nombre = "Eva", rol = "ADMIN")

    private fun texto(id: Int, vararg argumentos: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *argumentos)

    private fun montar() {
        // Fuera de la composición: dentro, cada recomposición crearía otro.
        val viewModel = RegistroEmpresaViewModel(repositorio, sso)
        pantalla.setContent {
            NxTimeTheme {
                RegistroEmpresaScreen(onRegistrado = { registros++ }, onVolver = {}, viewModel = viewModel)
            }
        }
    }

    private fun conGoogle() {
        repositorio.stub { onBlocking { getProveedoresSso() } doReturn Response.success(listOf(google)) }
    }

    @Test
    fun `sin proveedores configurados el registro es el de siempre`() {
        repositorio.stub { onBlocking { getProveedoresSso() } doReturn Response.success(emptyList()) }
        montar()
        pantalla.waitForIdle()

        pantalla.onNodeWithText(texto(R.string.login_email)).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.registro_sso_registrar_con, "Google")).assertDoesNotExist()
    }

    @Test
    fun `el boton de un proveedor abre el navegador en su URL, para registrar y con el reto del verificador guardado`() {
        conGoogle()
        montar()
        pantalla.waitForIdle()

        pantalla.onNodeWithText(texto(R.string.registro_sso_registrar_con, "Google")).performScrollTo().performClick()
        pantalla.waitForIdle()

        val abierto = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(
            google.inicio + "?cliente=app&registro=1&reto=" + AccesoSso.retoDe(guarda.verificador!!),
            abierto.dataString
        )
    }

    @Test
    fun `al volver del navegador dice con que cuenta va, no pide correo ni contrasena y registra con el pase`() {
        conGoogle()
        repositorio.stub {
            onBlocking { canjearRegistroSso(any(), any()) } doReturn
                Response.success(RegistroPendienteSsoDTO("google", "Google", "eva@gmail.test", "el-pase"))
            onBlocking { registrarEmpresaConSso(any()) } doReturn Response.success(sesion)
        }
        montar()
        pantalla.waitForIdle()
        pantalla.onNodeWithText(texto(R.string.registro_sso_registrar_con, "Google")).performScrollTo().performClick()
        pantalla.waitForIdle()

        // Lo que hace VueltaDeSsoActivity cuando el navegador vuelve a la app.
        sso.recibir(codigo = null, error = null, alta = "el-codigo")
        pantalla.waitForIdle()

        pantalla.onNodeWithText(texto(R.string.registro_sso_explicacion, "Google", "eva@gmail.test")).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.login_email)).assertDoesNotExist()
        pantalla.onNodeWithText(texto(R.string.login_contrasena)).assertDoesNotExist()
        pantalla.onNodeWithText(texto(R.string.registro_sso_registrar_con, "Google")).assertDoesNotExist()

        pantalla.onNodeWithText(texto(R.string.registro_empresa)).performTextInput("Talleres Eva SL")
        pantalla.onNodeWithText(texto(R.string.registro_nombre)).performTextInput("Eva")
        pantalla.onNodeWithText(texto(R.string.registro_apellidos)).performTextInput("Martín")
        pantalla.onNodeWithText(texto(R.string.registro_crear)).performScrollTo().performClick()
        pantalla.waitForIdle()

        verifyBlocking(repositorio) {
            registrarEmpresaConSso(PeticionRegistroSso("el-pase", "Talleres Eva SL", "Eva", "Martín"))
        }
        verifyBlocking(repositorio) { procesarLoginExitoso(sesion) }
        verifyBlocking(repositorio, never()) { registrarEmpresaGestor(any()) }
        assertEquals(1, registros)
    }

    @Test
    fun `desde el formulario con la cuenta se puede volver al de correo y contrasena`() {
        conGoogle()
        repositorio.stub {
            onBlocking { canjearRegistroSso(any(), any()) } doReturn
                Response.success(RegistroPendienteSsoDTO("google", "Google", "eva@gmail.test", "el-pase"))
        }
        montar()
        pantalla.waitForIdle()
        pantalla.onNodeWithText(texto(R.string.registro_sso_registrar_con, "Google")).performScrollTo().performClick()
        sso.recibir(codigo = null, error = null, alta = "el-codigo")
        pantalla.waitForIdle()

        pantalla.onNodeWithText(texto(R.string.registro_sso_otra_forma)).performScrollTo().performClick()
        pantalla.waitForIdle()

        pantalla.onNodeWithText(texto(R.string.login_email)).assertIsDisplayed()
        pantalla.onNodeWithText(texto(R.string.registro_explicacion)).assertIsDisplayed()
    }

    /**
     * Si el sistema cerró la app mientras la persona estaba en el navegador,
     * al volver arranca en el acceso. La vuelta es de un registro: el acceso
     * no la canjea, lleva al registro.
     */
    @Test
    fun `una vuelta de registro que encuentra el acceso lleva al registro y no intenta entrar`() {
        guarda.verificador = "el-de-antes-de-morir"
        guarda.paraRegistrar = true
        sso.recibir(codigo = null, error = null, alta = "el-codigo")
        val viewModel = LoginViewModel(repositorio, sso)
        pantalla.setContent {
            NxTimeTheme {
                LoginScreen(
                    onAccesoConcedido = {},
                    onIrRegistroEmpresa = { idasAlRegistro++ },
                    onIrRecuperarAcceso = {},
                    viewModel = viewModel
                )
            }
        }
        pantalla.waitForIdle()

        assertEquals(1, idasAlRegistro)
        verifyBlocking(repositorio, never()) { canjearSso(any(), any()) }
        // Sigue pendiente: la gasta el registro, que es quien la atiende.
        assertEquals("el-codigo", sso.vueltaDeRegistro.value?.alta)
    }
}
