package com.nxtime.app.ui.privacidad

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.BuildConfig
import com.nxtime.app.R
import com.nxtime.app.data.dto.AvisoDePrivacidadDTO
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.acceso.abrirEnElNavegador
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * El aviso de privacidad de la primera entrada (plan J, fase J5; ADR 047), el
 * mismo que en la web: quién es la responsable de los datos de cada persona
 * (su empresa), qué papel tiene NX Time y a quién dirigirse. Sale una vez, en
 * la web o aquí, lo que se vea antes.
 *
 * Como el de avisos, **no tiene `init { cargar() }`**: se construye desde
 * `NxTimeNavHost`, que también existe en el login, y es él quien decide
 * cuándo pedirlo.
 */
class AvisoDePrivacidadViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _aviso = MutableStateFlow<AvisoDePrivacidadDTO?>(null)

    /** El aviso que hay que enseñar, o null si no hay que enseñar ninguno. */
    val aviso: StateFlow<AvisoDePrivacidadDTO?> = _aviso.asStateFlow()

    fun cargar() {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getAvisoDePrivacidad()
                val cuerpo = respuesta.body()
                _aviso.value = if (respuesta.isSuccessful && cuerpo != null && !cuerpo.visto) cuerpo else null
            } catch (_: Exception) {
                // Sin conexión no se enseña: saldrá la próxima vez que entre.
                // Un error aquí, encima de la pantalla de fichar, molestaría
                // más de lo que informa.
            }
        }
    }

    /**
     * Se quita en el acto, sin esperar al servidor: si la marca no llegara,
     * el aviso volvería a salir la próxima vez, que es lo que tiene que pasar.
     */
    fun entendido() {
        _aviso.value = null
        viewModelScope.launch {
            try {
                authRepository.marcarAvisoDePrivacidadVisto()
            } catch (_: Exception) {
                // Ver el KDoc.
            }
        }
    }

    fun limpiar() {
        _aviso.value = null
    }
}

@Composable
fun DialogoDeAvisoDePrivacidad(aviso: AvisoDePrivacidadDTO, onEntendido: () -> Unit) {
    val contexto = LocalContext.current
    val contacto = aviso.contacto
    AlertDialog(
        // Tocar fuera no lo da por visto: solo «Entendido».
        onDismissRequest = {},
        title = { Text(stringResource(R.string.privacidad_titulo)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.privacidad_texto, aviso.empresa.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        contacto == null -> stringResource(R.string.privacidad_sin_contacto)
                        contacto.startsWith("https://") -> stringResource(R.string.privacidad_contacto_enlace, contacto)
                        else -> stringResource(R.string.privacidad_contacto_correo, contacto)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onEntendido) { Text(stringResource(R.string.privacidad_entendido)) }
        },
        dismissButton = {
            TextButton(onClick = { abrirEnElNavegador(contexto, BuildConfig.WEB_URL + "legal/privacidad") }) {
                Text(stringResource(R.string.privacidad_mas_informacion))
            }
        }
    )
}
