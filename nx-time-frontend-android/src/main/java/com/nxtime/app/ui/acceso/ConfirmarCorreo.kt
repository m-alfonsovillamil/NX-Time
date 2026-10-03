package com.nxtime.app.ui.acceso

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BotonPrincipal
import com.nxtime.app.ui.components.CampoTexto
import com.nxtime.app.ui.util.MensajeUi
import com.nxtime.app.ui.util.resolver

/**
 * El código que llegó al registrar la empresa (V37 del backend, ADR 034). Lo
 * enseñan el registro, justo después de registrar, y el login, cuando el
 * servidor responde 403 a quien tiene la contraseña buena pero no ha
 * confirmado su correo todavía (el login ya le ha mandado otro código).
 */
@Composable
fun ConfirmarCorreoFormulario(
    email: String,
    codigo: String,
    onCodigoCambia: (String) -> Unit,
    onConfirmar: () -> Unit,
    cargando: Boolean,
    error: MensajeUi?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.confirmar_correo_titulo),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.confirmar_correo_explicacion, email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        error?.let { BannerError(mensaje = it.resolver()) }
        CampoTexto(
            valor = codigo,
            onCambia = onCodigoCambia,
            etiqueta = stringResource(R.string.recuperar_codigo),
            tipoTeclado = KeyboardType.Number
        )
        BotonPrincipal(
            texto = stringResource(R.string.confirmar_correo_entrar),
            onClick = onConfirmar,
            cargando = cargando
        )
        Text(
            text = stringResource(R.string.confirmar_correo_otro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** Seis cifras, sin los espacios que se arrastran al pegarlo desde el correo; o null si no lo es. */
fun codigoLimpio(codigo: String): String? = codigo.filterNot(Char::isWhitespace).takeIf { it.matches(Regex("\\d{6}")) }
