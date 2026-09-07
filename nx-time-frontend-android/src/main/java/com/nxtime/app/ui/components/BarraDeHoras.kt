package com.nxtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nxtime.app.ui.util.DateFormats

/**
 * Una barra de horas con la media marcada encima.
 *
 * Sin esa marca, la gráfica no decía nada: eran barras planas sin escala
 * ni referencia, así que "9h 30m" quedaba tan suelto como el número que
 * ya estaba escrito al lado. Con la línea de la media se lee de un
 * vistazo lo único que se busca aquí -- **quién se sale de lo normal**,
 * hacia arriba o hacia abajo.
 *
 * Se dibuja con `Box` y anchuras proporcionales en vez de traer una
 * librería de gráficas: son cuatro o cinco filas y un solo eje, y una
 * dependencia entera para esto no se paga sola.
 *
 * <b>Vivía dentro de `PanelEmpresaScreen` como función privada</b> y se
 * movió aquí en la Fase D, cuando las horas por proyecto necesitaron
 * exactamente la misma barra: copiarla habría dejado dos gráficas que se
 * parecen hasta que alguien arregla una sola.
 *
 * @param etiqueta el nombre de la fila (una persona, un proyecto...).
 * @param proporcion cuánto llena la barra, de 0 a 1.
 * @param proporcionMedia dónde cae la media, de 0 a 1.
 * @param porEncimaDeLaMedia si la cifra se resalta en el color de gestión.
 */
@Composable
fun BarraDeHoras(
    etiqueta: String,
    minutos: Long,
    proporcion: Float,
    proporcionMedia: Float,
    porEncimaDeLaMedia: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.bodyMedium,
                // Un nombre de proyecto puede ser largo; recortarlo es
                // mejor que empujar la cifra fuera de la pantalla.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = DateFormats.minutos(minutos),
                style = MaterialTheme.typography.bodyMedium,
                // Quien está por encima de la media se marca en el color
                // de gestión; el resto queda en gris.
                color = if (porEncimaDeLaMedia) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            // El carril de fondo da la escala completa: sin él, todas las
            // barras parecerían llenas y no compararían nada.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(proporcion.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
            )
            /*
             * La marca de la media, encima de todo y sobresaliendo de la
             * barra por arriba y por abajo para que se lea como una
             * referencia y no como un trozo de la propia barra.
             *
             * Va en dos capas -- un filo del color de la tarjeta y un
             * nucleo gris dentro -- porque tiene que verse sobre dos
             * fondos distintos: el carril vacio, que es gris claro, y el
             * relleno indigo de quien esta por encima de la media, que es
             * justo la fila donde la marca mas importa. Con una sola capa
             * gris, la marca de Javier Lopez desaparecia dentro de su
             * propia barra.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth(proporcionMedia.coerceIn(0f, 1f))
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }
}
