package com.nxtime.app.ui.ausencias

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nxtime.app.R
import com.nxtime.app.data.dto.JustificanteDTO

/**
 * El justificante de una ausencia propia (ADR 062): su nombre si lo hay y, con
 * la empresa activada, adjuntarlo o cambiarlo. Verlo y quitarlo, en la web.
 *
 * @param alElegir los bytes, el nombre y el tipo del fichero elegido; se leen
 *   aquí porque leer un `content://` necesita el Context.
 */
@Composable
fun FilaJustificante(
    justificante: JustificanteDTO?,
    activo: Boolean,
    subiendo: Boolean,
    alElegir: (ByteArray, String, String) -> Unit
) {
    if (justificante == null && !activo) return
    val contexto = LocalContext.current
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) leer(contexto, uri)?.let { (bytes, nombre, mime) -> alElegir(bytes, nombre, mime) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = justificante?.let { stringResource(R.string.justificante_adjuntado, it.nombre) }
                ?: stringResource(R.string.justificante_sin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (activo) {
            TextButton(onClick = { elegir.launch("*/*") }, enabled = !subiendo) {
                Text(
                    stringResource(
                        if (justificante == null) R.string.justificante_adjuntar else R.string.justificante_cambiar
                    )
                )
            }
        }
    }
}

/** Los bytes, el nombre y el tipo del fichero elegido, o null si no se puede leer. */
private fun leer(contexto: Context, uri: Uri): Triple<ByteArray, String, String>? = runCatching {
    val bytes = contexto.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mime = contexto.contentResolver.getType(uri) ?: "application/octet-stream"
    Triple(bytes, nombreDe(contexto, uri), mime)
}.getOrNull()

private fun nombreDe(contexto: Context, uri: Uri): String {
    contexto.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val columna = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columna >= 0 && cursor.moveToFirst()) {
            val nombre = cursor.getString(columna)
            if (!nombre.isNullOrBlank()) return nombre
        }
    }
    return "justificante"
}
