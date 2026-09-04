package com.nxtime.app.ui.fichar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import com.nxtime.app.ui.theme.LocalColoresJornada
import com.nxtime.app.ui.util.DateFormats

/**
 * De qué se compone la jornada abierta: trabajo y pausa.
 *
 * Responde a "¿qué llevo hecho hoy?", que era la pregunta que la
 * pantalla no contestaba: había un cronómetro y tres números, pero para
 * saber cuánto habías parado tenías que esperar al historial del día
 * siguiente.
 *
 * **No es una línea temporal.** Sería mejor enseñar *cuándo* fue cada
 * pausa, pero el backend no guarda los intervalos: `TimeEntry` solo
 * tiene `segundosPausaAcumulados` y, si acaso, `inicioPausaActual`.
 * Pintar bloques en posiciones inventadas sería mentir sobre un registro
 * con valor legal, así que esto es una **composición** -- cuánto de cada
 * cosa -- y no un cuándo.
 */
@Composable
fun ComposicionDeLaJornada(
    segundosTrabajados: Long,
    segundosPausa: Long,
    horaEntradaIso: String?,
    modifier: Modifier = Modifier
) {
    /*
     * Nada hasta que haya un minuto que enseñar.
     *
     * La barra reparte por segundos pero las etiquetas van en minutos, y
     * en los primeros segundos de la jornada eso se contradecía en
     * pantalla: 16 s trabajados y 9 s de pausa dibujaban una barra
     * partida a la mitad con las dos etiquetas diciendo "0h 00m". Se
     * espera al primer minuto, que además es cuando el dato empieza a
     * significar algo.
     */
    val total = segundosTrabajados + segundosPausa
    if (total < 60) return

    // La pausa entra en el dibujo con el mismo criterio: una pausa de
    // nueve segundos no merece un bloque de color.
    val pausaVisible = segundosPausa >= 60
    val colores = LocalColoresJornada.current
    val descripcion = stringResource(
        R.string.jornada_composicion_descripcion,
        DateFormats.minutos(segundosTrabajados / 60),
        DateFormats.minutos(segundosPausa / 60)
    )

    Column(modifier = modifier.semantics { contentDescription = descripcion }) {
        /*
         * La hora de entrada, en su propia linea y encima de la barra.
         * Iba al final de la fila de leyendas y ahi se partia en dos
         * ("Desde las 15:15" / "h") en cuanto las leyendas crecian.
         */
        Text(
            text = stringResource(R.string.jornada_desde, DateFormats.hora(horaEntradaIso)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(segundosTrabajados.coerceAtLeast(0).toFloat().coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(colores.trabajando)
            )
            if (pausaVisible) {
                Box(
                    modifier = Modifier
                        .weight(segundosPausa.toFloat())
                        .fillMaxHeight()
                        .background(colores.enPausa)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Leyenda(
                color = colores.trabajando,
                texto = stringResource(
                    R.string.jornada_trabajado,
                    DateFormats.minutos(segundosTrabajados / 60)
                )
            )
            if (pausaVisible) {
                Leyenda(
                    color = colores.enPausa,
                    texto = stringResource(
                        R.string.jornada_pausado,
                        DateFormats.minutos(segundosPausa / 60)
                    )
                )
            }
        }
    }
}

@Composable
private fun Leyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Progreso de la semana contra la jornada esperada del usuario.
 *
 * `minutosJornadaSemanal` sale de `User.horasSemanales`, que existía en
 * la base desde hacía tiempo con 40 h por defecto y **no lo leía nadie**.
 * Es lo que convierte "55h 33m" en un dato que dice algo: si son muchas
 * o pocas depende de la jornada de cada uno, y 40 h no es la de todo el
 * mundo (37,5 es frecuente).
 *
 * Si el objetivo es 0 -- usuario sin jornada configurada -- no se pinta
 * nada: mejor un hueco que un porcentaje inventado.
 *
 * La barra **no se corta al 100 %**. Pasarse de la jornada es
 * información relevante, no un error de dibujo, así que el exceso se
 * pinta en el color de "parado" y el texto lo dice.
 */
@Composable
fun ProgresoSemanal(
    minutosTrabajados: Long,
    minutosObjetivo: Long,
    modifier: Modifier = Modifier
) {
    if (minutosObjetivo <= 0) return

    val colores = LocalColoresJornada.current
    val proporcion = (minutosTrabajados.toFloat() / minutosObjetivo).coerceIn(0f, 1f)
    val excedido = minutosTrabajados > minutosObjetivo

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.jornada_semana_objetivo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    R.string.jornada_semana_progreso,
                    DateFormats.minutos(minutosTrabajados),
                    DateFormats.minutos(minutosObjetivo)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (excedido) {
                    colores.parado
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(proporcion)
                    .fillMaxHeight()
                    .background(
                        if (excedido) colores.parado else MaterialTheme.colorScheme.primary
                    )
            )
        }
    }
}
