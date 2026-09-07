package com.nxtime.app.ui.calendario

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.nxtime.app.data.dto.AusenciaCalendarioDTO
import com.nxtime.app.data.dto.FestivoDTO
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.etiqueta
import java.time.LocalDate

/**
 * Lo que hay debajo de la rejilla.
 *
 * Con un día seleccionado enseña ESE día en detalle (y sus acciones); sin
 * selección, el resumen del mes entero. Las dos vistas hacen falta: el
 * mes contesta "¿qué hay este mes?" nada más entrar, y el día contesta
 * "¿y este cuadrito de color qué era?", que es lo que uno se pregunta
 * justo después de tocarlo.
 */
fun LazyListScope.detalleDelDia(
    estado: CalendarioUiState,
    puedeGestionar: Boolean,
    onAnadirFestivo: (LocalDate) -> Unit,
    onBorrarFestivo: (Long) -> Unit
) {
    val dia = estado.diaSeleccionado

    if (dia == null) {
        item {
            Text(
                text = stringResource(R.string.calendario_festivos_del_mes),
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (estado.festivos.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.calendario_sin_festivos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(estado.festivos, key = { it.id }) { festivo ->
                FilaDeFestivo(
                    festivo = festivo,
                    puedeBorrar = puedeGestionar && festivo.editable,
                    onBorrar = { onBorrarFestivo(festivo.id) }
                )
            }
        }
        return
    }

    item {
        Text(
            text = DateFormats.fechaCorta(dia),
            style = MaterialTheme.typography.titleMedium
        )
    }

    val festivo = estado.festivoPorDia[dia]
    if (festivo != null) {
        item {
            FilaDeFestivo(
                festivo = festivo,
                puedeBorrar = puedeGestionar && festivo.editable,
                onBorrar = { onBorrarFestivo(festivo.id) }
            )
        }
    } else if (puedeGestionar) {
        item {
            AssistChip(
                onClick = { onAnadirFestivo(dia) },
                label = { Text(stringResource(R.string.calendario_marcar_festivo)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
        }
    }

    val ausencias = estado.ausenciasPorDia[dia].orEmpty()
    if (ausencias.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.calendario_dia_sin_ausencias),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        items(ausencias, key = { it.id }) { ausencia -> FilaDeAusencia(ausencia) }
    }
}

@Composable
private fun FilaDeFestivo(
    festivo: FestivoDTO,
    puedeBorrar: Boolean,
    onBorrar: () -> Unit
) {
    var confirmando by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        // Explícito por lo mismo que la rejilla: `Card` a secas usa un
        // color que este tema no define y sale rosa.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = festivo.descripcion, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateFormats.fechaCorta(festivo.fecha),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " · " + textoDeAmbito(festivo.ambito),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (puedeBorrar) {
                TextButton(onClick = { confirmando = true }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.calendario_quitar_festivo)
                    )
                }
            }
        }
    }

    // Se confirma antes de borrar porque quitar un festivo cambia los
    // días hábiles de toda la empresa, y con ellos el saldo de vacaciones
    // de cada persona: no es un borrado local que se pueda deshacer
    // volviendo atrás.
    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text(stringResource(R.string.calendario_quitar_festivo)) },
            text = {
                Text(stringResource(R.string.calendario_quitar_festivo_aviso, festivo.descripcion))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    onBorrar()
                }) { Text(stringResource(R.string.calendario_quitar)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmando = false }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun FilaDeAusencia(ausencia: AusenciaCalendarioDTO) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        // Explícito por lo mismo que la rejilla: `Card` a secas usa un
        // color que este tema no define y sale rosa.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (ausencia.propia) {
                    stringResource(R.string.calendario_ausencia_propia)
                } else {
                    ausencia.usuario
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(ausencia.tipo.etiqueta) +
                        " · " + stringResource(ausencia.estado.etiqueta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.calendario_ausencia_rango,
                    DateFormats.fechaCorta(ausencia.fechaInicio),
                    DateFormats.fechaCorta(ausencia.fechaFin)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * El nombre del ámbito, tolerando uno que esta versión no conozca.
 *
 * Un ámbito nuevo en el backend no puede dejar la fila en blanco ni
 * reventar: se dice "Festivo" y se sigue.
 */
@Composable
private fun textoDeAmbito(ambito: String): String =
    AmbitoFestivo.de(ambito)?.let { stringResource(it.etiqueta) }
        ?: stringResource(R.string.calendario_leyenda_festivo)

/**
 * Alta de un festivo sobre un día ya elegido en la rejilla.
 *
 * La fecha no se puede cambiar aquí a propósito: se ha llegado tocando
 * ese día, y ofrecer un segundo selector de fecha permitiría crear el
 * festivo en un día distinto del que se estaba mirando.
 */
@Composable
fun DialogoDeFestivo(
    fecha: LocalDate,
    onConfirma: (String, AmbitoFestivo) -> Unit,
    onCancela: () -> Unit
) {
    var descripcion by remember { mutableStateOf("") }
    var ambito by remember { mutableStateOf(AmbitoFestivo.EMPRESA) }

    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(stringResource(R.string.calendario_nuevo_festivo)) },
        text = {
            Column {
                Text(
                    text = DateFormats.fechaCorta(fecha),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text(stringResource(R.string.calendario_festivo_descripcion)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                // Desplazable en horizontal: los tres chips caben de
                // sobra en un móvil normal, pero con el texto ampliado
                // por accesibilidad la fila se parte y las etiquetas
                // salen cortadas a mitad de palabra.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // NACIONAL no está en la lista: no se ofrece lo que el
                    // servidor va a rechazar (ver AmbitoFestivo).
                    AmbitoFestivo.ELEGIBLES.forEach { opcion ->
                        FilterChip(
                            selected = opcion == ambito,
                            onClick = { ambito = opcion },
                            label = { Text(stringResource(opcion.etiqueta), maxLines = 1) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                // Sin descripción no hay festivo: el backend lo rechaza
                // con un 400 y aquí se ve antes de gastarse la ida y vuelta.
                enabled = descripcion.isNotBlank(),
                onClick = { onConfirma(descripcion, ambito) }
            ) { Text(stringResource(R.string.calendario_anadir)) }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}
