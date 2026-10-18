package com.nxtime.app.ui.acceso

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nxtime.app.R

/**
 * La casilla obligatoria del registro (ADR 046): aceptar las condiciones del
 * servicio y el contrato de encargo en nombre de la empresa, y declarar la
 * mayoría de edad. Los enlaces abren los textos de la web en el navegador.
 *
 * @param onAbrir la ruta de la web que hay que abrir («legal/condiciones»)
 */
@Composable
fun CasillaDeCondiciones(marcada: Boolean, onCambia: (Boolean) -> Unit, onAbrir: (String) -> Unit) {
    val estilo = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    )
    val texto = buildAnnotatedString {
        append(stringResource(R.string.registro_acepto_antes))
        withLink(LinkAnnotation.Clickable("condiciones", estilo) { onAbrir("legal/condiciones") }) {
            append(stringResource(R.string.registro_acepto_condiciones))
        }
        append(stringResource(R.string.registro_acepto_entre))
        withLink(LinkAnnotation.Clickable("encargo", estilo) { onAbrir("legal/encargo") }) {
            append(stringResource(R.string.registro_acepto_encargo))
        }
        append(stringResource(R.string.registro_acepto_despues))
    }
    val descripcion = texto.text
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Checkbox(
            checked = marcada,
            onCheckedChange = onCambia,
            modifier = Modifier.semantics { contentDescription = descripcion }
        )
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/** Registrada, y esperando a que el operador la apruebe (ADR 046). */
@Composable
fun EmpresaPendiente(mensaje: String, onVolver: () -> Unit) {
    Text(
        text = stringResource(R.string.pendiente_titulo),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 12.dp)
    )
    Text(text = mensaje, style = MaterialTheme.typography.bodyLarge)
    Text(
        text = stringResource(R.string.pendiente_siguiente),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp)
    )
    TextButton(onClick = onVolver) { Text(stringResource(R.string.pendiente_volver)) }
}

/** Una pestaña del navegador, como el SSO (ver LoginScreen). Si no hay navegador, no pasa nada. */
fun abrirEnElNavegador(contexto: Context, url: String) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(contexto, url.toUri())
    } catch (e: ActivityNotFoundException) {
        // Sin navegador no se puede leer; la casilla se puede marcar igual.
    }
}
