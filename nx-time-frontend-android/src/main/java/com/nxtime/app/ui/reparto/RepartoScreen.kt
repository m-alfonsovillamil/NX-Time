package com.nxtime.app.ui.reparto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.ProyectoParaFichar
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Repartir las horas de una jornada entre proyectos (ADR 017).
 *
 * La pantalla dice **antes de pulsar** qué va a pasar: si el reparto se aplica
 * al momento o lo tiene que aprobar un gestor. Y no deja enviar un reparto que
 * el servidor va a rechazar (menos horas de las trabajadas, todo a cero).
 */
@Composable
fun RepartoScreen(
    onVolver: () -> Unit,
    viewModel: RepartoViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    // Hecho: se vuelve al historial, que es donde se ve el resultado.
    LaunchedEffect(estado.aplicado, estado.pedido) {
        if (estado.aplicado || estado.pedido) onVolver()
    }

    PantallaConBarra(titulo = stringResource(R.string.reparto_titulo), onVolver = onVolver) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            estado.error?.let {
                BannerError(mensaje = it.resolver(), onReintentar = viewModel::descartarError)
                Spacer(Modifier.height(16.dp))
            }
            if (estado.cargando) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }
            val imputaciones = estado.imputaciones ?: return@Column

            Text(
                text = stringResource(R.string.reparto_neto, DateFormats.minutos(estado.netoMinutos)),
                style = MaterialTheme.typography.titleMedium
            )
            if (imputaciones.solicitudPendienteId != null) {
                Text(
                    text = stringResource(R.string.reparto_con_solicitud),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                return@Column
            }
            if (imputaciones.disponibles.isEmpty()) {
                Text(
                    text = stringResource(R.string.reparto_sin_proyectos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            imputaciones.disponibles.forEach { proyecto ->
                FilaDeProyecto(
                    proyecto = proyecto,
                    minutos = estado.minutos[proyecto.id] ?: 0,
                    onCambiar = { viewModel.cambiarMinutos(proyecto.id, it) },
                    onTodo = { viewModel.todoA(proyecto.id) }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.reparto_total,
                    DateFormats.minutos(estado.totalMinutos),
                    DateFormats.minutos(estado.netoMinutos)
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (estado.diferencia < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when {
                    estado.diferencia < 0 -> stringResource(
                        R.string.reparto_faltan, DateFormats.minutos(-estado.diferencia))
                    estado.diferencia > 0 -> stringResource(
                        R.string.reparto_de_mas, DateFormats.minutos(estado.diferencia))
                    estado.necesitaAprobacion -> stringResource(R.string.reparto_lo_aprueba_un_gestor)
                    else -> stringResource(R.string.reparto_se_aplica_ya)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (estado.necesitaAprobacion && estado.diferencia >= 0) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = estado.motivo,
                    onValueChange = viewModel::cambiarMotivo,
                    label = { Text(stringResource(R.string.reparto_motivo)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::enviar,
                enabled = estado.puedeEnviar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (estado.necesitaAprobacion) R.string.reparto_pedir else R.string.reparto_guardar
                    )
                )
            }
        }
    }
}

@Composable
private fun FilaDeProyecto(
    proyecto: ProyectoParaFichar,
    minutos: Long,
    onCambiar: (Long) -> Unit,
    onTodo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(proyecto.codigo, style = MaterialTheme.typography.titleSmall)
            Text(
                text = proyecto.nombre,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    // En minutos y no en "horas,decimal": 7,5 h obliga a
                    // traducir la parte decimal, que es como se cuenta mal.
                    value = if (minutos == 0L) "" else minutos.toString(),
                    onValueChange = { texto -> onCambiar(texto.filter { it.isDigit() }.take(4).toLongOrNull() ?: 0) },
                    label = { Text(stringResource(R.string.reparto_minutos)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = DateFormats.minutos(minutos),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onTodo) { Text(stringResource(R.string.reparto_todo_aqui)) }
            }
        }
    }
}
