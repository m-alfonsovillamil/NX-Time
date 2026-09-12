package com.nxtime.app.ui.ajustes

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nxtime.app.R

/**
 * La huella, que en esta aplicación es una **puerta al arrancar** y no un
 * candado sobre el token.
 *
 * 🚨 La diferencia importa: quien lee el refresh token es el
 * `Authenticator` de OkHttp, en un hilo de fondo, sin Activity y con
 * `runBlocking`. Ahí no se puede enseñar un diálogo de huella, y haría
 * falta **cada vez que caduca el access token** (15 min), también con la
 * app en segundo plano. Así que lo que se protege es *entrar*, no *cada
 * renovación*.
 */
enum class EstadoDeLaHuella {
    /** Hay sensor y al menos una huella registrada. */
    DISPONIBLE,

    /** Hay sensor, pero el móvil no tiene ninguna huella dada de alta. */
    SIN_REGISTRAR,

    /** Ni hay sensor, ni lo hay utilizable. */
    NO_HAY
}

fun estadoDeLaHuella(context: Context): EstadoDeLaHuella =
    when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
        BiometricManager.BIOMETRIC_SUCCESS -> EstadoDeLaHuella.DISPONIBLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> EstadoDeLaHuella.SIN_REGISTRAR
        else -> EstadoDeLaHuella.NO_HAY
    }

/**
 * Pide la huella.
 *
 * [alRendirse] cubre tanto el error como la cancelación, y no es un caso
 * raro: es la salida de emergencia. Si alguien cambia de móvil, se borra
 * las huellas o el sensor falla, **tiene que poder entrar con su
 * contraseña**; una biometría que deje a una persona fuera de su propio
 * registro horario sería peor que no tenerla.
 */
fun pedirHuella(
    activity: FragmentActivity,
    titulo: String,
    subtitulo: String,
    textoCancelar: String,
    alAcertar: () -> Unit,
    alRendirse: () -> Unit
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                alAcertar()
            }

            // Un fallo suelto (dedo mal puesto) NO se trata aquí: el propio
            // diálogo lo dice y deja reintentar. Solo se sale cuando el
            // sistema da por terminado el intento.
            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                alRendirse()
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setNegativeButtonText(textoCancelar)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    )
}

/**
 * Lo que se ve mientras la aplicación está bloqueada por la huella.
 *
 * El diálogo se pide **solo una vez** al entrar (`LaunchedEffect(Unit)`):
 * relanzarlo en cada recomposición encadenaría diálogos si alguien
 * cancela.
 */
@Composable
fun PantallaBloqueada(
    onPedirHuella: () -> Unit,
    onEntrarConContrasena: () -> Unit
) {
    LaunchedEffect(Unit) { onPedirHuella() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.huella_bloqueada),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onPedirHuella) {
            Text(stringResource(R.string.huella_reintentar))
        }
        // La salida de emergencia, siempre visible: cierra la sesión local
        // y devuelve al login, que es lo único que no depende del sensor.
        TextButton(onClick = onEntrarConContrasena) {
            Text(stringResource(R.string.huella_usar_contrasena))
        }
    }
}
