package com.nxtime.app.ui.proyectos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import com.nxtime.app.data.dto.AsignacionProyectoDTO
import com.nxtime.app.data.dto.DetalleProyectoDTO
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BarraDeHoras
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.MensajeUi
import com.nxtime.app.ui.util.resolver
import java.time.LocalDate

/**
 * Un proyecto abierto: sus datos, quién ha pasado por él y las horas del
 * mes.
 *
 * Las dos listas responden preguntas distintas y por eso van separadas:
 * <b>"quién está o ha estado"</b> (el histórico completo, con su
 * vigencia) y <b>"quién puso horas este mes"</b>. Alguien asignado que no
 * fichó no aparece en la segunda, y eso es correcto.
 */
@Composable
fun DetalleProyectoScreen(
    detalle: DetalleProyectoDTO,
    asignables: List<EmpleadoSimpleDTO>,
    puedeGestionar: Boolean,
    error: MensajeUi?,
    onVolver: () -> Unit,
    onDescartarError: () -> Unit,
    onAsignar: (Long, LocalDate) -> Unit,
    onFinalizar: (Long, LocalDate) -> Unit,
    onCambiarEstado: (Boolean) -> Unit
) {
    var asignando by remember { mutableStateOf(false) }
    var aFinalizar by remember { mutableStateOf<AsignacionProyectoDTO?>(null) }

    val proyecto = detalle.proyecto
    // El tope de las barras es el máximo del mes, no un número redondo:
    // así la barra más larga siempre llena el ancho y las demás se
    // comparan contra ella (mismo criterio que el panel de empresa).
    val tope = (detalle.horas.maxOfOrNull { it.minutos } ?: 0L).coerceAtLeast(1L)
    val media = if (detalle.horas.isEmpty()) 0L else detalle.horas.sumOf { it.minutos } / detalle.horas.size

    PantallaConBarra(titulo = proyecto.codigo, onVolver = onVolver) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let { mensaje ->
                item { BannerError(mensaje = mensaje.resolver(), onReintentar = onDescartarError) }
            }

            item {
                Column {
                    Text(text = proyecto.nombre, style = MaterialTheme.typography.titleLarge)
                    proyecto.descripcion?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rangoDeFechas(proyecto),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (puedeGestionar) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { asignando = true },
                            enabled = asignables.isNotEmpty(),
                            label = { Text(stringResource(R.string.proyectos_asignar)) },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }
                        )
                        AssistChip(
                            onClick = { onCambiarEstado(!proyecto.activo) },
                            label = {
                                Text(
                                    stringResource(
                                        if (proyecto.activo) R.string.proyectos_cerrar
                                        else R.string.proyectos_reabrir
                                    )
                                )
                            }
                        )
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = stringResource(R.string.proyectos_horas_del_mes),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (detalle.horas.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.proyectos_sin_horas),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Card(
                        elevation = elevacionDeTarjeta(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            detalle.horas.forEach { fila ->
                                BarraDeHoras(
                                    etiqueta = fila.nombre,
                                    minutos = fila.minutos,
                                    proporcion = fila.minutos.toFloat() / tope,
                                    proporcionMedia = media.toFloat() / tope,
                                    porEncimaDeLaMedia = fila.minutos > media
                                )
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = stringResource(R.string.proyectos_quien_ha_estado),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(detalle.asignaciones, key = { it.id }) { asignacion ->
                FilaDeAsignacion(
                    asignacion = asignacion,
                    puedeGestionar = puedeGestionar,
                    onFinalizar = { aFinalizar = asignacion }
                )
            }
        }
    }

    if (asignando) {
        DialogoAsignar(
            candidatos = asignables,
            onConfirma = { usuarioId, desde ->
                onAsignar(usuarioId, desde)
                asignando = false
            },
            onCancela = { asignando = false }
        )
    }

    aFinalizar?.let { asignacion ->
        DialogoFinalizar(
            asignacion = asignacion,
            onConfirma = { hasta ->
                onFinalizar(asignacion.id, hasta)
                aFinalizar = null
            },
            onCancela = { aFinalizar = null }
        )
    }
}

@Composable
private fun FilaDeAsignacion(
    asignacion: AsignacionProyectoDTO,
    puedeGestionar: Boolean,
    onFinalizar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = asignacion.usuario, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (asignacion.fechaFin == null) {
                        stringResource(
                            R.string.proyectos_desde,
                            DateFormats.fechaCorta(asignacion.fechaInicio)
                        )
                    } else {
                        stringResource(
                            R.string.proyectos_rango,
                            DateFormats.fechaCorta(asignacion.fechaInicio),
                            DateFormats.fechaCorta(asignacion.fechaFin)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Solo se puede sacar a quien sigue dentro: una asignación ya
            // cerrada no se vuelve a cerrar.
            if (puedeGestionar && asignacion.vigente) {
                TextButton(onClick = onFinalizar) {
                    Text(stringResource(R.string.proyectos_sacar))
                }
            }
        }
    }
}
