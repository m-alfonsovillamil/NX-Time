package com.nxtime.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * El avatar de la barra superior: un círculo con las iniciales.
 *
 * Las iniciales llegan calculadas del servidor
 * ([com.nxtime.app.data.dto.PerfilDTO.iniciales]) y no se derivan aquí:
 * la regla tiene un caso raro — sin apellidos son las DOS primeras
 * letras del nombre, no una sola — y repartida por cada cliente acaba
 * implementada distinta en cada sitio.
 *
 * En la fase B2 este mismo hueco enseñará la foto cuando la haya, y las
 * iniciales pasan a ser el respaldo.
 */
@Composable
fun Avatar(
    iniciales: String,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(36.dp)
            .clickable(onClick = onClick)
            // La descripción va en el Surface entero y no en el Text
            // para que el lector de pantalla anuncie "Mi perfil" y no
            // deletree las dos letras.
            .semantics { contentDescription = descripcion },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = iniciales,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
