package com.nxtime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nxtime.app.ui.navegacion.NxTimeNavHost
import com.nxtime.app.ui.theme.NxTimeTheme

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Se lee el token una sola vez, aquí, para decidir por dónde
         * empieza la aplicación. Es la misma comprobación que hacía la
         * MainActivity anterior antes de inflar nada, y por el mismo
         * motivo: quien ya tiene sesión no debe ver pasar la pantalla de
         * login.
         */
        val sesionIniciada =
            (application as NxTimeApplication).sessionManager.fetchAuthToken() != null

        setContent {
            NxTimeTheme {
                NxTimeNavHost(sesionIniciada = sesionIniciada)
            }
        }
    }
}
