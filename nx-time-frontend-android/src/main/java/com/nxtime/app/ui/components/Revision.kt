package com.nxtime.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nxtime.app.ui.theme.elevacionDeTarjeta

/**
 * La tarjeta de lo que espera una decisión de quien gestiona (horas extra,
 * correcciones, incidencias): todas con la misma forma, para que se lean
 * igual de un vistazo.
 *
 * ```
 * Nombre de la persona                 [Estado]
 * Qué es y cuándo
 * +3h 15m  por encima de 40h 00m
 * «El motivo, si lo hay»
 * [  No computan  ][  Se pagan  ][ Con descanso ]
 * ```
 *
 * Antes cada una era una frase larga que se partía en dos líneas, con las
 * cifras dentro de la frase y unos enlaces sueltos a la derecha. Con veinte
 * pendientes, lo que se busca primero es el nombre y la cifra: van arriba y
 * destacados, y los botones ocupan todo el ancho, como en las ausencias.
 *
 * @param cifra lo que hay que juzgar, en grande ("+3h 15m"). Opcional.
 * @param cifraDetalle el listón contra el que se mide la cifra, al lado.
 * @param contenido lo demás (comparaciones, motivo, quién lo revisó).
 * @param acciones los botones; cada uno con [BotonDeRevision], que reparte
 *   el ancho a partes iguales.
 */
@Composable
fun TarjetaDeRevision(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    estado: (@Composable () -> Unit)? = null,
    cifra: String? = null,
    cifraDetalle: String? = null,
    contenido: @Composable ColumnScope.() -> Unit = {},
    acciones: (@Composable RowScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                estado?.let {
                    Spacer(Modifier.width(12.dp))
                    it()
                }
            }
            // El subtítulo va debajo y con todo el ancho, no al lado de la
            // insignia: así "Semana del 16/11/2026 al 22/11/2026" cabe en
            // una línea en vez de partirse en dos.
            subtitulo?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            cifra?.let {
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.alignByBaseline()
                    )
                    cifraDetalle?.let { detalle ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = detalle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
            }
            contenido()
            acciones?.let {
                Spacer(Modifier.height(16.dp))
                // Misma altura para todos aunque uno parta el texto en dos
                // líneas (letra grande en Ajustes del móvil).
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = it
                )
            }
        }
    }
}

/** Cómo pesa cada botón de una tarjeta de revisión. */
enum class PesoDeBoton {
    /** La decisión habitual: relleno, en el color principal. */
    PRINCIPAL,

    /** Otra decisión válida, al mismo nivel pero sin competir con la principal. */
    TONAL,

    /** Rechazar, disputar, no computar: con borde y en rojo si [negativo]. */
    CONTORNO
}

/**
 * Un botón de [TarjetaDeRevision]: reparte el ancho con los demás a partes
 * iguales y, si el texto no cabe, lo parte en dos líneas antes que cortarlo.
 */
@Composable
fun RowScope.BotonDeRevision(
    texto: String,
    onClick: () -> Unit,
    peso: PesoDeBoton = PesoDeBoton.PRINCIPAL,
    negativo: Boolean = false,
    enabled: Boolean = true
) {
    val modificador = Modifier.weight(1f).fillMaxHeight().widthIn(min = 0.dp)
    val relleno = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    val etiqueta: @Composable RowScope.() -> Unit = {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
    when (peso) {
        PesoDeBoton.PRINCIPAL -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modificador,
            contentPadding = relleno,
            content = etiqueta
        )
        PesoDeBoton.TONAL -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modificador,
            contentPadding = relleno,
            content = etiqueta
        )
        PesoDeBoton.CONTORNO -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modificador,
            contentPadding = relleno,
            colors = if (negativo) {
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            // El borde también en rojo, como en las ausencias pendientes: el
            // gris por defecto lo dejaba a medias.
            border = if (negativo) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.38f))
            } else {
                ButtonDefaults.outlinedButtonBorder(enabled)
            },
            content = etiqueta
        )
    }
}

/**
 * Lo que alguien escribió (un motivo, una justificación), en un recuadro
 * suave: se distingue de los datos de la tarjeta sin competir con ellos.
 */
@Composable
fun NotaDeRevision(texto: String, modifier: Modifier = Modifier, etiqueta: String? = null) {
    Column(
        modifier = modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        etiqueta?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Una fila "Entrada  08:54 → 08:39": lo de antes tachado y lo propuesto en
 * negrita, para comparar sin leer una frase. Si no cambia, el valor una vez.
 */
@Composable
fun FilaDeCambio(etiqueta: String, antes: String, despues: String, modifier: Modifier = Modifier) {
    if (antes == despues) {
        FilaDeDato(etiqueta = etiqueta, valor = despues, modifier = modifier)
        return
    }
    Row(modifier = modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ANCHO_ETIQUETA)
        )
        run {
            Text(
                text = antes,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough
            )
            Text(
                text = "  →  ",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = despues, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Una fila "Pausa  10:00 h – 10:30 h": etiqueta a la izquierda, valor al lado. */
@Composable
fun FilaDeDato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ANCHO_ETIQUETA)
        )
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Ancho de la columna de etiquetas: "Modalidad" cabe y los valores quedan alineados. */
private val ANCHO_ETIQUETA = 92.dp
