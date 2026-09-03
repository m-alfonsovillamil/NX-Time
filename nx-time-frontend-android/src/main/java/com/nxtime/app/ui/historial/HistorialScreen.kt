package com.nxtime.app.ui.historial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.EstadoCargando
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

@Composable
fun HistorialScreen(
    viewModel: HistorialViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    // Sin flecha de volver: es un destino de la barra de navegación.
    PantallaConBarra(
        titulo = stringResource(R.string.historial_titulo)
    ) { modifier ->
        when {
            estado.cargando -> EstadoCargando(modifier)

            estado.error != null -> EstadoErrorPantalla(
                mensaje = estado.error!!.resolver(),
                onReintentar = viewModel::cargar,
                modifier = modifier
            )

            estado.registros.isEmpty() -> EstadoVacio(
                titulo = stringResource(R.string.historial_vacio_titulo),
                texto = stringResource(R.string.historial_vacio_texto),
                modifier = modifier
            )

            else -> LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.registros, key = { it.id }) { registro ->
                    TarjetaJornada(registro)
                }
            }
        }
    }
}

/**
 * Una jornada del historial.
 *
 * El total es el tiempo **neto**: la duración entre entrada y salida
 * menos las pausas acumuladas. Es la misma cuenta que hacía la lista
 * anterior, y la que corresponde a lo que se factura como trabajado.
 */
@Composable
private fun TarjetaJornada(registro: Registro) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = DateFormats.fechaLarga(registro.horaEntrada),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Dato(
                    etiqueta = stringResource(R.string.historial_entrada),
                    valor = DateFormats.hora(registro.horaEntrada),
                    modifier = Modifier.weight(1f)
                )
                Dato(
                    etiqueta = stringResource(R.string.historial_salida),
                    valor = if (registro.horaSalida == null) {
                        stringResource(R.string.historial_en_curso)
                    } else {
                        DateFormats.hora(registro.horaSalida)
                    },
                    modifier = Modifier.weight(1f)
                )
                Dato(
                    etiqueta = stringResource(R.string.historial_total),
                    valor = DateFormats.duracionNeta(
                        registro.horaEntrada,
                        registro.horaSalida,
                        registro.segundosPausaAcumulados
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Solo se enseña la pausa si la hubo: una línea "Pausa: 0h 00m"
            // en cada jornada solo añade ruido.
            if (registro.minutosPausaAcumulados > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.historial_pausa,
                        DateFormats.minutos(registro.minutosPausaAcumulados)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
