package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Un texto con su interruptor, donde **toda la fila** es el interruptor.
 *
 * Con el `Switch` suelto al lado del texto, el lector de pantalla anunciaba
 * «interruptor, desactivado» sin decir de qué, y solo se podía tocar el
 * interruptor (visto con el árbol de accesibilidad en el emulador, plan P9).
 * Así lo anuncia con su texto y se toca en cualquier parte de la fila. Es lo
 * que ya hacía «Medio día» al pedir una ausencia.
 */
@Composable
fun FilaConInterruptor(
    marcado: Boolean,
    onCambia: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contenido: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 48 dp: lo mínimo para algo que se toca. El Switch sin onCheckedChange
            // ya no lo reserva él.
            .heightIn(min = 48.dp)
            .toggleable(value = marcado, enabled = enabled, role = Role.Switch, onValueChange = onCambia),
        verticalAlignment = Alignment.CenterVertically
    ) {
        contenido()
        // Sin onCheckedChange: el toque lo recoge la fila.
        Switch(checked = marcado, onCheckedChange = null, enabled = enabled)
    }
}
