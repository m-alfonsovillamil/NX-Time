package com.nxtime.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nxtime.app.ui.navegacion.NxTimeNavHost
import com.nxtime.app.ui.theme.NxTimeTheme
import com.nxtime.app.ui.util.enEspanol

/**
 * La única Activity de la aplicación.
 *
 * Antes había trece, una por pantalla, cada una inflando su layout y
 * observando su LiveData. Ahora esta solo monta el tema y el grafo de
 * navegación; las pantallas son funciones `@Composable`.
 *
 * Hereda de `ComponentActivity` y ya no de `AppCompatActivity`: sin
 * layouts XML ni menús de la barra de acción, lo único que aportaba
 * AppCompat era peso.
 */
class MainActivity : ComponentActivity() {

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
        val sessionManager = (application as NxTimeApplication).sessionManager
        val sesionIniciada = sessionManager.fetchAuthToken() != null

        setContent {
            NxTimeTheme {
                NxTimeNavHost(
                    sesionIniciada = sesionIniciada,
                    sessionManager = sessionManager
                )
            }
        }
    }
}
