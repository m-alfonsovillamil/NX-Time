package com.nxtime.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nxtime.app.ui.theme.elevacionDeTarjeta

/**
 * Una lista de destinos o de acciones dentro de una tarjeta, como los ajustes
 * del propio Android: icono, texto y flecha.
 *
 * Nace de la revisión de Mi perfil y Ajustes (11/2026). Eran siete botones
 * perfilados seguidos, y en Material 3 el botón perfilado lleva el texto y el
 * borde en gris (`onSurfaceVariant`): parecían desactivados, todos iguales, y
 * «Cerrar sesión» no se distinguía de «Cambiar contraseña».
 */
@Composable
fun TarjetaDeFilas(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    contenido: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            if (titulo != null) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
            }
            contenido()
        }
    }
}

/**
 * Una fila de [TarjetaDeFilas]. Con [peligrosa], en el color de error y sin
 * flecha: no lleva a otra pantalla, hace algo que no se deshace solo.
 *
 * El icono va en `primary` y no en gris: sobre la tarjeta blanca da 4,9:1, y
 * es lo que hace que la fila se lea como algo que se puede tocar.
 */
@Composable
fun FilaDeTarjeta(
    icono: ImageVector,
    texto: String,
    onClick: () -> Unit,
    detalle: String? = null,
    peligrosa: Boolean = false,
    conFlecha: Boolean = !peligrosa,
    habilitada: Boolean = true,
    separadorArriba: Boolean = false
) {
    if (separadorArriba) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
    val color = if (peligrosa) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val alfa = if (habilitada) 1f else 0.38f
    ListItem(
        modifier = Modifier.clickable(enabled = habilitada, role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icono, contentDescription = null, tint = color.copy(alpha = alfa)) },
        headlineContent = {
            Text(
                text = texto,
                color = (if (peligrosa) color else MaterialTheme.colorScheme.onSurface).copy(alpha = alfa),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = detalle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = if (conFlecha) {
            {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            null
        }
    )
}
