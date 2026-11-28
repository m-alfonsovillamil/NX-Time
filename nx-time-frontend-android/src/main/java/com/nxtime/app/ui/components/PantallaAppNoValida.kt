package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nxtime.app.BuildConfig
import com.nxtime.app.R
import com.nxtime.app.data.network.AppNoValida

/**
 * La app ya no se puede usar (ADR 064): una versión vieja o el paquete de antes
 * de un cambio de nombre. Tapa todo y no tiene salida, porque detrás nada
 * funcionaría: la API responde 426 a todo salvo entrar y mandar los fichajes
 * guardados sin conexión, que se siguen enviando solos.
 *
 * @param pendientes los fichajes de este móvil que aún no han llegado: mientras
 *   haya, se avisa de que no se desinstale la app.
 * @param onAbrir abre el enlace (Play o la descarga) fuera de la app.
 */
@Composable
fun PantallaAppNoValida(aviso: AppNoValida, pendientes: Int, onAbrir: (String) -> Unit) {
    // Sin enlace del servidor, una versión vieja aún puede ir a su propia
    // ficha de Play; una app retirada no sabe cuál es la nueva.
    val enlace = aviso.urlDescarga
        ?: if (aviso.retirada) null else "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (aviso.retirada) R.string.app_no_valida_titulo_retirada else R.string.app_no_valida_titulo),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (aviso.retirada) R.string.app_no_valida_texto_retirada else R.string.app_no_valida_texto),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (pendientes > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = pluralStringResource(R.plurals.app_no_valida_pendientes, pendientes, pendientes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        if (enlace != null) {
            Button(onClick = { onAbrir(enlace) }) {
                Text(stringResource(if (aviso.retirada) R.string.app_no_valida_instalar else R.string.app_no_valida_actualizar))
            }
        } else {
            Text(
                text = stringResource(R.string.app_no_valida_sin_enlace),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
