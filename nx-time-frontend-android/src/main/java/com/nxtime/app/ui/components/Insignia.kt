package com.nxtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxtime.app.ui.theme.LocalColoresJornada

/**
 * Las dos piezas que la app comparte con la web desde octubre de 2026 (ADR
 * 035): la insignia de estado rellena y el icono en su recuadro de color.
 *
 * Están aquí juntas porque son la misma idea: que el estado y la sección se
 * reconozcan por el color y la forma antes de leer nada, y que se reconozcan
 * igual en el móvil que en el navegador.
 */

/** De qué habla una insignia. Los mismos cinco tonos que `Insignia` en la web. */
enum class TonoDeInsignia { NEUTRO, EXITO, AVISO, ERROR, INFO }

/**
 * El estado de algo en una palabra: «Pendiente», «Aprobada», «Firmado».
 *
 * **No es un `AssistChip`.** Lo era en cuatro pantallas, con `onClick = {}`:
 * un chip es un botón, así que TalkBack lo anunciaba como «botón» y al
 * tocarlo hacía la onda de pulsado para no hacer nada. Esto es solo texto con
 * fondo.
 *
 * El texto lleva el color de estado de `ColoresJornada` -- pensado para
 * leerse sobre la superficie -- y el fondo es un 14 % de ese mismo color
 * sobre ella, igual que en la web: no hace falta un par de colores nuevo por
 * tono, y el contraste apenas baja. La informativa es la excepción: el
 * índigo y el teal con ese tinte se quedan por debajo de AA, y va en el
 * contenedor terciario con su texto.
 */
@Composable
fun Insignia(texto: String, tono: TonoDeInsignia = TonoDeInsignia.NEUTRO, modifier: Modifier = Modifier) {
    val jornada = LocalColoresJornada.current
    val esquema = MaterialTheme.colorScheme
    val fondo: Color
    val contenido: Color
    if (tono == TonoDeInsignia.INFO) {
        fondo = esquema.tertiaryContainer
        contenido = esquema.onTertiaryContainer
    } else {
        contenido = when (tono) {
            TonoDeInsignia.EXITO -> jornada.trabajando
            TonoDeInsignia.AVISO -> jornada.enPausa
            TonoDeInsignia.ERROR -> esquema.error
            else -> esquema.onSurfaceVariant
        }
        fondo = contenido.copy(alpha = 0.14f).compositeOver(esquema.surface)
    }

    Surface(color = fondo, contentColor = contenido, shape = CircleShape, modifier = modifier) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Un icono en su recuadro de color.
 *
 * En la zona de gestión, índigo (`tertiaryContainer`): es la señal de que se
 * ha entrado en ella, y antes la daba solo el tinte del icono suelto. Fuera
 * de ella, el teal de la marca.
 */
@Composable
fun IconoEnRecuadro(
    icono: ImageVector,
    modifier: Modifier = Modifier,
    deGestion: Boolean = false,
    lado: Dp = 40.dp,
    redondo: Boolean = false
) {
    val esquema = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(lado)
            .background(
                color = if (deGestion) esquema.tertiaryContainer else esquema.primaryContainer,
                shape = if (redondo) CircleShape else MaterialTheme.shapes.small
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = if (deGestion) esquema.onTertiaryContainer else esquema.onPrimaryContainer,
            modifier = Modifier.size(lado * 0.55f)
        )
    }
}
