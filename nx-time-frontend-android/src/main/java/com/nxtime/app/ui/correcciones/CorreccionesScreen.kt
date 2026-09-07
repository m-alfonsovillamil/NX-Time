package com.nxtime.app.ui.correcciones

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
import com.nxtime.app.data.dto.CorreccionDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Correcciones de fichaje (Fase E).
 *
 * Arriba lo que espera por ti; abajo lo que has pedido tú. Los botones
 * de cada tarjeta salen de `puedoResolver` y `puedoDisputar`, que
 * calcula el servidor: la app no reimplementa la regla de quién decide
 * qué, porque depende de quién pidió la corrección.
 */
@Composable
fun CorreccionesScreen(
    onVolver: () -> Unit,
    viewModel: CorreccionesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var aRechazar by remember { mutableStateOf<CorreccionDTO?>(null) }
    var aDisputar by remember { mutableStateOf<CorreccionDTO?>(null) }

    PantallaConBarra(
        titulo = stringResource(R.string.correcciones_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.pendientes.isNotEmpty() || estado.mias.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            if (estado.pendientes.isEmpty() && estado.mias.isEmpty() && !estado.cargando) {
                EstadoVacio(
                    titulo = stringResource(R.string.correcciones_vacio_titulo),
                    texto = stringResource(R.string.correcciones_vacio_texto)
                )
                return@ListaConRecarga
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                estado.error?.let { mensaje ->
                    item {
                        BannerError(
                            mensaje = mensaje.resolver(),
                            onReintentar = viewModel::descartarError
                        )
                    }
                }

                if (estado.pendientes.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.correcciones_esperan_por_ti),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(estado.pendientes, key = { it.id }) { correccion ->
                        TarjetaCorreccion(
                            correccion = correccion,
                            onAprobar = { viewModel.aprobar(correccion.id, null) },
                            onRechazar = { aRechazar = correccion },
                            onDisputar = { aDisputar = correccion }
                        )
                    }
                }

                if (estado.mias.isNotEmpty()) {
                    item { HorizontalDivider() }
                    item {
                        Text(
                            text = stringResource(R.string.correcciones_pedidas_por_mi),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(estado.mias, key = { "mia-" + it.id }) { correccion ->
                        TarjetaCorreccion(correccion = correccion)
                    }
                }
            }
        }
    }

    aRechazar?.let { correccion ->
        DialogoConMotivo(
            titulo = stringResource(R.string.correcciones_rechazar),
            ayuda = stringResource(R.string.correcciones_rechazar_ayuda),
            confirmar = stringResource(R.string.correcciones_rechazar),
            onConfirma = { motivo ->
                viewModel.rechazar(correccion.id, motivo)
                aRechazar = null
            },
            onCancela = { aRechazar = null }
        )
    }

    aDisputar?.let { correccion ->
        DialogoConMotivo(
            titulo = stringResource(R.string.correcciones_disputar),
            ayuda = stringResource(R.string.correcciones_disputar_ayuda),
            confirmar = stringResource(R.string.correcciones_disputar),
            onConfirma = { motivo ->
                viewModel.disputar(correccion.id, motivo)
                aDisputar = null
            },
            onCancela = { aDisputar = null }
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

@Composable
private fun TarjetaCorreccion(
    correccion: CorreccionDTO,
    onAprobar: (() -> Unit)? = null,
    onRechazar: (() -> Unit)? = null,
    onDisputar: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (correccion.solicitante.nombre == correccion.empleado.nombre) {
                        stringResource(
                            R.string.correcciones_pide_el_suyo, correccion.solicitante.nombre)
                    } else {
                        stringResource(
                            R.string.correcciones_quien_pide,
                            correccion.solicitante.nombre,
                            correccion.empleado.nombre
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                EstadoCorreccion.de(correccion.estado)?.let { estado ->
                    AssistChip(onClick = {}, label = { Text(stringResource(estado.etiqueta)) })
                }
            }

            Spacer(Modifier.height(8.dp))

            // Las horas de antes y las propuestas, juntas: quien decide
            // necesita comparar, y mandarle al historial a buscarlo
            // convierte una decisión de dos segundos en una navegación.
            Text(
                text = stringResource(
                    R.string.correcciones_de_a,
                    DateFormats.hora(correccion.horaEntradaActual) + " - " +
                            DateFormats.hora(correccion.horaSalidaActual),
                    DateFormats.hora(correccion.horaEntradaPropuesta) + " - " +
                            DateFormats.hora(correccion.horaSalidaPropuesta)
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = correccion.motivo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            correccion.motivoDisputa?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.correcciones_no_acepta, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            correccion.comentarioResolucion?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.correcciones_comentario, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Los botones dependen SOLO de lo que diga el servidor.
            if (correccion.puedoResolver || correccion.puedoDisputar) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (correccion.puedoDisputar && onDisputar != null) {
                        TextButton(onClick = onDisputar) {
                            Text(stringResource(R.string.correcciones_disputar))
                        }
                    }
                    if (correccion.puedoResolver && onRechazar != null) {
                        TextButton(onClick = onRechazar) {
                            Text(stringResource(R.string.correcciones_rechazar))
                        }
                    }
                    if (correccion.puedoResolver && onAprobar != null) {
                        TextButton(onClick = onAprobar) {
                            Text(
                                stringResource(R.string.correcciones_aprobar),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rechazar y disputar piden lo mismo —un motivo obligatorio— así que
 * comparten diálogo. En los dos casos el texto es lo que la otra parte
 * va a leer, y el backend lo exige.
 */
@Composable
private fun DialogoConMotivo(
    titulo: String,
    ayuda: String,
    confirmar: String,
    onConfirma: (String) -> Unit,
    onCancela: () -> Unit
) {
    var motivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(titulo) },
        text = {
            Column {
                Text(text = ayuda, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text(stringResource(R.string.correcciones_motivo)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = motivo.isNotBlank(), onClick = { onConfirma(motivo) }) {
                Text(confirmar)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}
