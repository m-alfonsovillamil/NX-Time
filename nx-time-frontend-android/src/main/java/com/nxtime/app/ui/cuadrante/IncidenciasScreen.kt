package com.nxtime.app.ui.cuadrante

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.nxtime.app.ui.components.finDeLista
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.IncidenciaDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.SeccionVacia
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Incidencias de cuadrante (Fase B2): las mías y, si me toca, las del equipo.
 *
 * Lo primero que dice la pantalla es que **una incidencia no descuenta nada**.
 * Es un aviso de que lo fichado no cuadra con el horario, no una sanción: el
 * metro se averió, el médico se alargó. Por eso cada tarjeta enseña la hora
 * prevista junto a la real —"25 min tarde" sin "respecto a las 9:00" no se
 * puede explicar ni juzgar—, y por eso explicar es lo primero que se ofrece.
 */
@Composable
fun IncidenciasScreen(
    onVolver: () -> Unit,
    viewModel: IncidenciasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var aJustificar by remember { mutableStateOf<IncidenciaDTO?>(null) }
    var aRechazar by remember { mutableStateOf<IncidenciaDTO?>(null) }

    PantallaConBarra(
        titulo = stringResource(R.string.incidencias_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.mias.isNotEmpty() || estado.delEquipo.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                estado.error?.let { mensaje ->
                    item {
                        BannerError(mensaje = mensaje.resolver(), onReintentar = viewModel::descartarError)
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.incidencias_explicacion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.incidencias_mias),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (estado.mias.isEmpty() && !estado.cargando) {
                    item { SeccionVacia(stringResource(R.string.incidencias_vacio)) }
                }
                items(estado.mias, key = { "mia-" + it.id }) { incidencia ->
                    TarjetaIncidencia(
                        incidencia = incidencia,
                        conNombre = false,
                        onJustificar = { aJustificar = incidencia }
                    )
                }

                if (estado.puedeRevisar) {
                    item { HorizontalDivider() }
                    item {
                        Text(
                            text = stringResource(R.string.incidencias_del_equipo),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    // Ya sin las propias: las quita el servidor, que es quien
                    // sabe que sobre las tuyas no decides.
                    if (estado.delEquipo.isEmpty() && !estado.cargando) {
                        item { SeccionVacia(stringResource(R.string.incidencias_equipo_vacio)) }
                    }
                    items(estado.delEquipo, key = { "equipo-" + it.id }) { incidencia ->
                        TarjetaIncidencia(
                            incidencia = incidencia,
                            conNombre = true,
                            onAceptar = { viewModel.aceptar(incidencia.id) },
                            onRechazar = { aRechazar = incidencia }
                        )
                    }
                    finDeLista(estado.paginasEquipo, viewModel::cargarMasDelEquipo)
                }
            }
        }
    }

    aJustificar?.let { incidencia ->
        DialogoConTexto(
            titulo = stringResource(R.string.incidencias_justificar),
            ayuda = stringResource(R.string.incidencias_justificar_ayuda),
            textoInicial = incidencia.justificacion.orEmpty(),
            onConfirma = { texto ->
                viewModel.justificar(incidencia.id, texto)
                aJustificar = null
            },
            onCancela = { aJustificar = null }
        )
    }

    aRechazar?.let { incidencia ->
        DialogoConTexto(
            titulo = stringResource(R.string.incidencias_rechazar),
            ayuda = stringResource(R.string.incidencias_rechazar_ayuda),
            textoInicial = "",
            onConfirma = { comentario ->
                viewModel.rechazar(incidencia.id, comentario)
                aRechazar = null
            },
            onCancela = { aRechazar = null }
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

@Composable
private fun TarjetaIncidencia(
    incidencia: IncidenciaDTO,
    conNombre: Boolean,
    onJustificar: (() -> Unit)? = null,
    onAceptar: (() -> Unit)? = null,
    onRechazar: (() -> Unit)? = null
) {
    val tipo = TipoIncidencia.de(incidencia.tipo)
    val estado = EstadoIncidencia.de(incidencia.estado)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cabecera(incidencia, tipo, conNombre),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                estado?.let {
                    AssistChip(onClick = {}, label = { Text(stringResource(it.etiqueta)) })
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(text = detalle(incidencia, tipo), style = MaterialTheme.typography.bodyMedium)

            incidencia.justificacion?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.incidencias_justificacion, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            incidencia.resueltaPor?.let { quien ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = incidencia.comentarioResolucion
                        ?.let { stringResource(R.string.incidencias_resuelta_con_comentario, quien, it) }
                        ?: stringResource(R.string.incidencias_resuelta_por, quien),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (estado?.esperaDecision == true) {
                val botones = onJustificar != null || (onAceptar != null && onRechazar != null)
                if (botones) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        onJustificar?.let {
                            TextButton(onClick = it) {
                                Text(
                                    stringResource(
                                        if (estado == EstadoIncidencia.JUSTIFICADA) R.string.incidencias_cambiar_justificacion
                                        else R.string.incidencias_justificar
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (onAceptar != null && onRechazar != null) {
                            TextButton(onClick = onRechazar) { Text(stringResource(R.string.incidencias_rechazar)) }
                            TextButton(onClick = onAceptar) {
                                Text(stringResource(R.string.incidencias_aceptar), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Lun 05/10 · Retraso", con el nombre delante si la incidencia es de otra persona. */
@Composable
private fun cabecera(incidencia: IncidenciaDTO, tipo: TipoIncidencia?, conNombre: Boolean): String {
    val fecha = DateFormats.fechaIso(incidencia.fecha)
    val dia = if (fecha != null) {
        DateFormats.nombreDelDia(fecha.dayOfWeek) + " " + DateFormats.fechaCorta(fecha)
    } else {
        incidencia.fecha
    }
    val texto = tipo?.let { "$dia · " + stringResource(it.etiqueta) } ?: dia
    return if (conNombre) "${incidencia.usuario} · $texto" else texto
}

/** Lo real contra lo previsto, siempre juntos. */
@Composable
private fun detalle(incidencia: IncidenciaDTO, tipo: TipoIncidencia?): String {
    val minutos = DateFormats.minutos(incidencia.minutos.toLong())
    val real = DateFormats.hora(incidencia.horaReal)
    return when (tipo) {
        TipoIncidencia.RETRASO ->
            stringResource(R.string.incidencias_detalle_retraso, real, incidencia.horaPrevista, minutos)
        TipoIncidencia.SALIDA_ANTICIPADA ->
            stringResource(R.string.incidencias_detalle_salida, real, incidencia.horaPrevista, minutos)
        TipoIncidencia.AUSENCIA ->
            stringResource(R.string.incidencias_detalle_ausencia, minutos, incidencia.horaPrevista)
        null -> stringResource(R.string.incidencias_detalle_otro, incidencia.horaPrevista, minutos)
    }
}

/**
 * Explicar una propia o rechazar una ajena: los dos piden texto y los dos los
 * rechazaría el servidor vacíos, así que el botón no se habilita hasta que lo hay.
 */
@Composable
private fun DialogoConTexto(
    titulo: String,
    ayuda: String,
    textoInicial: String,
    onConfirma: (String) -> Unit,
    onCancela: () -> Unit
) {
    var texto by remember { mutableStateOf(textoInicial) }

    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(titulo) },
        text = {
            Column {
                Text(text = ayuda, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { if (it.length <= MAX_TEXTO) texto = it },
                    label = { Text(stringResource(R.string.incidencias_texto_etiqueta)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirma(texto) }, enabled = texto.isNotBlank()) { Text(titulo) }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}

/** El `@Size(max = 1000)` de JustifyIncidentRequest y ResolveIncidentRequest. */
private const val MAX_TEXTO = 1000
