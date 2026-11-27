package com.nxtime.app.ui.components

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * El botón flotante de una lista ("Solicitar ausencia", "Nuevo proyecto"…).
 *
 * Dos cosas que el de Material no hace solo, comprobadas en el emulador
 * (revisión del 28/11/2026):
 * - **El texto, para TalkBack.** El árbol de accesibilidad lo daba como un
 *   botón sin nombre (NAF): se veía "Solicitar ausencia" pero no se leía.
 *   Se le pone la etiqueta a mano.
 * - **En arena tostada**, como los demás botones secundarios de la paleta
 *   (ADR 057). El color por defecto, `primaryContainer`, es un azul claro
 *   que sobre el fondo arena parecía de otra app, y el mismo "Solicitar
 *   ausencia" de Mi jornada ya iba en arena.
 */
@Composable
fun BotonFlotante(
    texto: String,
    icono: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = texto },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = { Icon(icono, contentDescription = null) },
        text = { Text(texto) }
    )
}
