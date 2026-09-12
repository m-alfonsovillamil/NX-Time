package com.nxtime.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nxtime.app.ui.ajustes.EstadoDeLaHuella
import com.nxtime.app.ui.ajustes.PantallaBloqueada
import com.nxtime.app.ui.ajustes.estadoDeLaHuella
import com.nxtime.app.ui.ajustes.pedirHuella
import com.nxtime.app.ui.components.AvisoServidorDespertando
import com.nxtime.app.ui.navegacion.NxTimeNavHost
import com.nxtime.app.data.session.Tema
import com.nxtime.app.ui.theme.NxTimeTheme
import androidx.compose.foundation.isSystemInDarkTheme
import com.nxtime.app.ui.util.enEspanol

/**
 * La única Activity de la aplicación.
 *
 * Antes había trece, una por pantalla, cada una inflando su layout y
 * observando su LiveData. Ahora esta solo monta el tema y el grafo de
 * navegación; las pantallas son funciones `@Composable`.
 *
 * Hereda de `FragmentActivity` y **no** de `AppCompatActivity`: sin
 * layouts XML ni menús de la barra de acción, lo único que aportaba
 * AppCompat era peso.
 *
 * Era `ComponentActivity` hasta el paso 6: `BiometricPrompt` exige una
 * `FragmentActivity`. No es una vuelta atrás — `FragmentActivity` vive en
 * androidx.fragment, que ya arrastra androidx.biometric, y **extiende
 * ComponentActivity**, así que `setContent` y el resto siguen igual.
 */
class MainActivity : FragmentActivity() {

    /**
     * Se fija el idioma antes de que exista nada de interfaz: todo lo que
     * pinta Compose -- los textos propios y los de los componentes de
     * Material, como el calendario de "Solicitar ausencia" -- se resuelve
     * contra la `Configuration` de este contexto. Ver [enEspanol].
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.enEspanol())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        /*
         * Edge-to-edge: la app pinta bajo la barra de estado y la de
         * navegación, y son los `Scaffold` los que apartan el contenido
         * con los insets. Faltaba, y con `targetSdk 36` no es opcional:
         * desde Android 15 el sistema lo aplica igualmente, así que sin
         * declararlo el resultado quedaba a merced del valor por defecto
         * en vez de ser una decisión de la app. Va ANTES de
         * `super.onCreate`, como pide la documentación.
         */
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        /*
         * Se lee el token una sola vez, aquí, para decidir por dónde
         * empieza la aplicación. Es la misma comprobación que hacía la
         * MainActivity anterior antes de inflar nada, y por el mismo
         * motivo: quien ya tiene sesión no debe ver pasar la pantalla de
         * login.
         */
        val aplicacion = application as NxTimeApplication
        val sessionManager = aplicacion.sessionManager
        val sesionIniciada = sessionManager.fetchAuthToken() != null

        setContent {
            /*
             * El tema se decide aquí, encima de todo el árbol: cambiarlo
             * desde Ajustes tiene que repintar la aplicación entera sin
             * reiniciarla, y eso solo funciona si el StateFlow se lee por
             * encima de NxTimeTheme.
             */
            val tema by aplicacion.ajustes.tema.collectAsStateWithLifecycle()
            val oscuro = when (tema) {
                Tema.SISTEMA -> isSystemInDarkTheme()
                Tema.CLARO -> false
                Tema.OSCURO -> true
            }

            NxTimeTheme(darkTheme = oscuro) {
                /*
                 * La huella es una PUERTA AL ARRANCAR, no un candado sobre
                 * el token: quien lo lee es el Authenticator de OkHttp, en
                 * un hilo de fondo y sin Activity, así que allí no se puede
                 * pedir biometría (y haría falta cada 15 min).
                 *
                 * Solo se pide si hay sesión guardada, el ajuste está
                 * activo y el móvil puede: si alguien borró sus huellas, la
                 * aplicación no puede quedarse cerrada para siempre.
                 */
                var desbloqueada by remember {
                    mutableStateOf(
                        !(sesionIniciada &&
                                aplicacion.ajustes.huella.value &&
                                estadoDeLaHuella(this@MainActivity) == EstadoDeLaHuella.DISPONIBLE)
                    )
                }

                if (!desbloqueada) {
                    PantallaBloqueada(
                        onPedirHuella = {
                            pedirHuella(
                                activity = this@MainActivity,
                                titulo = getString(R.string.huella_titulo),
                                subtitulo = getString(R.string.huella_subtitulo),
                                textoCancelar = getString(R.string.cancelar),
                                alAcertar = { desbloqueada = true },
                                // Cancelar no entra ni cierra nada: se
                                // queda en la pantalla, que ofrece
                                // reintentar o tirar de contraseña.
                                alRendirse = {}
                            )
                        },
                        onEntrarConContrasena = {
                            // La salida de emergencia: se cierra la sesión
                            // local y se vuelve a empezar por el login, que
                            // es lo único que no depende del sensor.
                            sessionManager.clearAuthData()
                            recreate()
                        }
                    )
                    return@NxTimeTheme
                }

                val despertando by aplicacion.arranqueEnFrio.despertando
                    .collectAsStateWithLifecycle()

                Box(Modifier.fillMaxSize()) {
                    NxTimeNavHost(
                        sesionIniciada = sesionIniciada,
                        sessionManager = sessionManager
                    )
                    /*
                     * Por encima del grafo y no dentro de una pantalla: la
                     * primera petición del día puede salir de cualquiera.
                     * Tapa la barra superior mientras dura, y es a propósito:
                     * mientras el servidor no responde, nada de ahí funciona.
                     */
                    AnimatedVisibility(
                        visible = despertando,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        AvisoServidorDespertando()
                    }
                }
            }
        }
    }
}
