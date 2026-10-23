package com.nxtime.app.ui.acceso

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * El código del segundo factor, después de la contraseña (ADR 051 del
 * backend). Vale el de 6 cifras de la aplicación de autenticación o uno de los
 * códigos de recuperación: el servidor los distingue por su forma. Activarlo
 * se hace en la web; la app solo lo pide al entrar.
 */
@Composable
fun SegundoPasoFormulario(
    codigo: String,
    onCodigoCambia: (String) -> Unit,
    onVerificar: () -> Unit,
    onVolver: () -> Unit,
    cargando: Boolean,
    error: MensajeUi?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.segundo_paso_titulo),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.segundo_paso_explicacion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        error?.let { BannerError(mensaje = it.resolver()) }
        // Teclado de texto y no numérico: un código de recuperación lleva letras.
        CampoTexto(
            valor = codigo,
            onCambia = onCodigoCambia,
            etiqueta = stringResource(R.string.segundo_paso_codigo),
            tipoTeclado = KeyboardType.Ascii
        )
        BotonPrincipal(
            texto = stringResource(R.string.segundo_paso_verificar),
            onClick = onVerificar,
            cargando = cargando
        )
        Text(
            text = stringResource(R.string.segundo_paso_recuperacion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
        TextButton(onClick = onVolver, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.segundo_paso_volver))
        }
    }
}
