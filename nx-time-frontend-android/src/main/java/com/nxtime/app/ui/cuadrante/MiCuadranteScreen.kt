package com.nxtime.app.ui.cuadrante

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.DiaTeoricoDTO
import com.nxtime.app.data.dto.OrigenDelDia
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.SeccionVacia
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Mi cuadrante: a qué hora me toca cada día de las dos próximas semanas
 * (Fase B1).
 *
 * Solo lectura. Cada día dice su horario, o por qué no se trabaja: el festivo,
 * las vacaciones o el día libre pactado. Lo que la app NO hace es decidir esa
 * precedencia —un festivo manda sobre el cuadrante—: la aplica el servidor y
 * aquí se enseña tal cual llega.
 */
@Composable
fun MiCuadranteScreen(
    onVolver: () -> Unit,
    onIrIncidencias: () -> Unit,
    viewModel: MiCuadranteViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(
        titulo = stringResource(R.string.cuadrante_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.dias.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                estado.error?.let { mensaje ->
                    item {
                        BannerError(mensaje = mensaje.resolver(), onReintentar = viewModel::descartarError)
                    }
                }
                // Siempre, también sin cuadrante hoy: las incidencias de un
                // cuadrante que ya se cerró siguen siendo tuyas.
                item {
                    TextButton(onClick = onIrIncidencias) {
                        Text(stringResource(R.string.cuadrante_ver_incidencias))
                    }
                }
                if (estado.dias.none { it.tieneCuadrante() }) {
                    // Sin cuadrante no hay nada que enseñar día a día: la
                    // jornada contratada es por semana, y repartirla entre
                    // los días sería inventarse un horario. Catorce filas de
                    // "sin cuadrante" serían ruido; basta con decirlo una vez.
                    if (!estado.cargando && estado.error == null) {
                        item { SeccionVacia(stringResource(R.string.cuadrante_vacio)) }
                    }
                } else {
                    items(estado.dias, key = { it.fecha }) { dia -> TarjetaDia(dia) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaDia(dia: DiaTeoricoDTO) {
    val fecha = DateFormats.fechaIso(dia.fecha)
    val trabaja = dia.tramos.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (fecha != null) {
                        DateFormats.nombreDelDia(fecha.dayOfWeek) + " " + DateFormats.fechaCorta(fecha)
                    } else {
                        dia.fecha
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = descripcion(dia),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trabaja) {
                Text(
                    text = DateFormats.minutos(dia.minutos.toLong()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Qué se dice de un día: sus tramos si trabaja, o por qué no.
 *
 * Un turno de noche lleva "(+1)" detrás de la hora de fin: "22:00–06:00" a
 * secas se lee como un horario al revés.
 */
@Composable
private fun descripcion(dia: DiaTeoricoDTO): String {
    if (dia.tramos.isNotEmpty()) {
        val horario = dia.tramos.joinToString(" · ") { tramo ->
            tramo.horaInicio + "–" + tramo.horaFin + if (tramo.cruzaMedianoche) " (+1)" else ""
        }
        return dia.motivo?.let { "$horario · $it" } ?: horario
    }
    return when (OrigenDelDia.de(dia.origen)) {
        OrigenDelDia.NO_LABORABLE -> dia.motivo ?: stringResource(R.string.cuadrante_no_laborable)
        OrigenDelDia.EXCEPCION -> dia.motivo ?: stringResource(R.string.cuadrante_libre)
        OrigenDelDia.CUADRANTE -> stringResource(R.string.cuadrante_libre)
        // Un origen que esta versión no conoce, o sin cuadrante ese día.
        OrigenDelDia.SIN_CUADRANTE, null -> stringResource(R.string.cuadrante_sin_cuadrante)
    }
}

private fun DiaTeoricoDTO.tieneCuadrante(): Boolean =
    OrigenDelDia.de(origen) == OrigenDelDia.CUADRANTE || OrigenDelDia.de(origen) == OrigenDelDia.EXCEPCION
