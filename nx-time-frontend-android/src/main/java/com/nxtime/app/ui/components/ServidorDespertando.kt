package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nxtime.app.R

/**
 * Aviso de que el servidor está despertando (piloto, 09/2026).
 *
 * Lo enciende `ArranqueEnFrio` cuando una petición lleva más de unos
 * segundos esperando a un servidor que podía estar dormido. Va encima de
 * TODAS las pantallas y no dentro de una, porque la primera petición del
 * día puede salir de cualquiera: del login, de "Mi jornada" o del refresco
 * del token.
 *
 * No es un error y por eso no usa [BannerError]: la petición sigue en
 * marcha y va a salir bien; lo que hay que evitar es que se cierre la app
 * pensando que se ha colgado. Es una tarjeta blanca flotante, como las del
 * resto de la línea visual, y no un color de la paleta sobre el degradado.
 *
 * `liveRegion` hace que un lector de pantalla lo anuncie al aparecer: la
 * rueda sola no le dice nada a quien no la ve.
 */
@Composable
fun AvisoServidorDespertando(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.servidor_despertando_titulo),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.servidor_despertando_texto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
