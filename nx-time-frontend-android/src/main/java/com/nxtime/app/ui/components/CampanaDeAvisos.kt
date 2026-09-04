package com.nxtime.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nxtime.app.R

/** A partir de aquí el número deja de caber en el círculo del badge. */
private const val MAXIMO_EN_EL_BADGE = 9

/**
 * La campana de avisos de la barra superior, con su contador.
 *
 * Vive en las cuatro pestañas principales. Tres de ellas la ponen en el
 * hueco `acciones` de [PantallaConBarra]; "Mi jornada" tiene su propia
 * barra y la coloca a mano, delante del menú de tres puntos.
 */
@Composable
fun CampanaDeAvisos(contador: Int, onClick: () -> Unit) {
    val descripcion = if (contador > 0) {
        pluralStringResource(R.plurals.avisos_sin_leer, contador, contador)
    } else {
        stringResource(R.string.avisos_titulo)
    }

    BadgedBox(
        badge = {
            if (contador > 0) {
                Badge {
                    Text(
                        if (contador > MAXIMO_EN_EL_BADGE) {
                            stringResource(R.string.avisos_muchos, MAXIMO_EN_EL_BADGE)
                        } else {
                            contador.toString()
                        }
                    )
                }
            }
        }
    ) {
        IconButton(onClick = onClick) {
            // La descripción la lleva el icono y no el BadgedBox para
            // que el lector de pantalla anuncie el número al enfocar el
            // botón, que es lo que se puede pulsar.
            Icon(Icons.Default.Notifications, contentDescription = descripcion)
        }
    }
}
