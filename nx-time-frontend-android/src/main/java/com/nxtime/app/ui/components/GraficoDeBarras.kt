package com.nxtime.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Una barra del gráfico.
 *
 * @param etiqueta lo que va debajo ("L", "15"); vacío para no rotular esa barra.
 * @param esperados la marca de referencia (la jornada esperada ese día); 0 = sin marca.
 * @param noLaborable festivo o ausencia: la barra se pinta en otro color, porque
 *     trabajar ese día es justo lo que conviene ver de un vistazo.
 * @param descripcion la frase que lee TalkBack para esta barra.
 */
data class BarraDeHoras(
    val etiqueta: String,
    val minutos: Long,
    val esperados: Long,
    val noLaborable: Boolean,
    val descripcion: String
)

/**
 * Gráfico de barras de minutos por día, dibujado con `Canvas`.
 *
 * Sin librería a propósito, como el resto de barras del proyecto: son dos
 * gráficos sencillos, y una dependencia de gráficos pesa más que ellos.
 *
 * La escala se ajusta al mayor entre lo trabajado y lo esperado, con un
 * mínimo de una hora: con solo diez minutos fichados, una escala ajustada a
 * diez minutos dibujaría una barra a tope que parece una jornada entera.
 */
@Composable
fun GraficoDeBarras(
    barras: List<BarraDeHoras>,
    modifier: Modifier = Modifier,
    altura: Dp = 160.dp
) {
    if (barras.isEmpty()) return
    val colorBarra = MaterialTheme.colorScheme.primary
    val colorNoLaborable = MaterialTheme.colorScheme.tertiary
    val colorReferencia = MaterialTheme.colorScheme.onSurfaceVariant
    val colorFondo = MaterialTheme.colorScheme.surfaceContainerHighest
    val maximo = maxOf(barras.maxOf { maxOf(it.minutos, it.esperados) }, 60L).toFloat()
    val descripcion = barras.joinToString(". ") { it.descripcion }

    Column(modifier = modifier.semantics { contentDescription = descripcion }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(altura)
        ) {
            val hueco = size.width / barras.size
            val ancho = hueco * 0.62f
            val radio = CornerRadius(ancho / 4, ancho / 4)
            barras.forEachIndexed { i, barra ->
                val x = hueco * i + (hueco - ancho) / 2
                // Carril de fondo: sin él, un día a cero no se distingue de
                // un hueco entre barras.
                drawRoundRect(colorFondo, Offset(x, 0f), Size(ancho, size.height), radio)
                val alto = size.height * (barra.minutos / maximo)
                if (alto > 0f) {
                    drawRoundRect(
                        color = if (barra.noLaborable) colorNoLaborable else colorBarra,
                        topLeft = Offset(x, size.height - alto),
                        size = Size(ancho, alto),
                        cornerRadius = radio
                    )
                }
                if (barra.esperados > 0) {
                    val y = size.height * (1 - barra.esperados / maximo)
                    drawLine(
                        color = colorReferencia,
                        start = Offset(hueco * i + hueco * 0.08f, y),
                        end = Offset(hueco * (i + 1) - hueco * 0.08f, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            barras.forEach { barra ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = barra.etiqueta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
