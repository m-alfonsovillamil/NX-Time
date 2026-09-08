package com.nxtime.app.ui.ofertas

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
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.CandidaturaDTO
import com.nxtime.app.data.dto.OfertaDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.SeccionVacia
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * El tablón de vacantes internas (Fase H).
 *
 * Arriba las publicadas; debajo, las que he presentado y en qué han
 * quedado. Son dos listas en una pantalla por lo mismo que en horas
 * extra y correcciones: son las dos caras de lo mismo, y separarlas
 * obligaría a ir y volver para responder "¿me presenté a esta?".
 *
 * Cada oferta enseña **por qué** no se puede optar cuando no se puede:
 * "plazo terminado" y "ya te has presentado" no son el mismo botón
 * apagado, y un botón gris sin explicación es lo que hace que la gente
 * crea que la aplicación no funciona.
 */
@Composable
fun OfertasScreen(
    onVolver: () -> Unit,
    viewModel: OfertasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(
        titulo = stringResource(R.string.ofertas_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.ofertas.isNotEmpty() || estado.misCandidaturas.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
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

                item {
                    Text(
                        text = stringResource(R.string.ofertas_abiertas),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (estado.ofertas.isEmpty() && !estado.cargando) {
                    // SeccionVacia y no EstadoVacio: el segundo hace
                    // scroll propio y dentro de un item{} revienta.
                    item { SeccionVacia(stringResource(R.string.ofertas_vacio)) }
                }
                items(estado.ofertas, key = { "oferta-" + it.id }) { oferta ->
                    TarjetaOferta(oferta = oferta, onAbrir = { viewModel.abrir(oferta) })
                }

                item { HorizontalDivider() }
                item {
                    Text(
                        text = stringResource(R.string.ofertas_mis_candidaturas),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (estado.misCandidaturas.isEmpty() && !estado.cargando) {
                    item { SeccionVacia(stringResource(R.string.candidaturas_vacio)) }
                }
                items(estado.misCandidaturas, key = { "cand-" + it.id }) { candidatura ->
                    TarjetaMiCandidatura(candidatura)
                }
            }
        }
    }

    estado.seleccionada?.let { oferta ->
        DialogoOferta(
            oferta = oferta,
            enviando = estado.enviando,
            onPresentarse = { carta -> viewModel.presentarse(oferta.id, carta) },
            onCerrar = viewModel::cerrar
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

@Composable
private fun TarjetaOferta(oferta: OfertaDTO, onAbrir: () -> Unit) {
    Card(
        onClick = onAbrir,
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = oferta.titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                if (oferta.yaMePresente) {
                    AssistChip(
                        onClick = onAbrir,
                        label = { Text(stringResource(R.string.oferta_ya_presentado)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.tertiary
                        )
                    )
                }
            }

            oferta.departamento?.let { departamento ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = departamento,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = textoDePlazo(oferta),
                style = MaterialTheme.typography.bodySmall,
                color = if (oferta.plazoVencido) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** "Hasta el 30/06/2026", "Plazo terminado" o "Sin fecha de cierre". */
@Composable
private fun textoDePlazo(oferta: OfertaDTO): String = when {
    oferta.plazoVencido -> stringResource(R.string.oferta_plazo_terminado)
    oferta.fechaCierre != null -> stringResource(
        R.string.oferta_plazo_hasta,
        DateFormats.fechaCorta(oferta.fechaCierre)
    )
    else -> stringResource(R.string.oferta_sin_plazo)
}

@Composable
private fun TarjetaMiCandidatura(candidatura: CandidaturaDTO) {
    val estado = EstadoCandidatura.de(candidatura.estado)

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
                    text = candidatura.ofertaTitulo,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                estado?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(it.etiqueta)) },
                        colors = when (it) {
                            EstadoCandidatura.SELECCIONADA -> AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.tertiary
                            )
                            EstadoCandidatura.DESCARTADA -> AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> AssistChipDefaults.assistChipColors()
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.candidatura_presentada_el,
                    DateFormats.fechaLarga(candidatura.creadoEn)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // El comentario del descarte se enseña ENTERO y no recortado:
            // es lo único que quien no sigue adelante puede leer sobre
            // por qué, y esconderlo tras un "ver más" sería mezquino.
            candidatura.comentario?.let { comentario ->
                Spacer(Modifier.height(8.dp))
                Text(text = comentario, style = MaterialTheme.typography.bodyMedium)
            }

            // El CV que se congeló al presentarse, con su nombre: si la
            // persona ha subido otro después, este sigue siendo el que
            // se está valorando, y verlo evita la duda.
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.candidatura_cv_adjunto, candidatura.cvNombre),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DialogoOferta(
    oferta: OfertaDTO,
    enviando: Boolean,
    onPresentarse: (String?) -> Unit,
    onCerrar: () -> Unit
) {
    var carta by remember(oferta.id) { mutableStateOf("") }
    val puedeOptar = oferta.admiteCandidaturas && !oferta.yaMePresente

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(oferta.titulo) },
        text = {
            Column {
                oferta.puesto?.let {
                    Text(text = it, style = MaterialTheme.typography.labelLarge)
                }
                oferta.departamento?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(text = oferta.descripcion, style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = textoDePlazo(oferta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (puedeOptar) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = carta,
                        onValueChange = { carta = it },
                        label = { Text(stringResource(R.string.candidatura_carta)) },
                        supportingText = {
                            // Que el CV lo pone el servidor hay que
                            // decirlo: si no, la gente busca dónde
                            // adjuntarlo y no lo encuentra.
                            Text(stringResource(R.string.candidatura_cv_automatico))
                        },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Por qué NO se puede optar. Un botón gris sin
                    // explicación se lee como una aplicación rota.
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            if (oferta.yaMePresente) R.string.oferta_ya_presentado_detalle
                            else R.string.oferta_no_admite
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (puedeOptar) {
                TextButton(
                    onClick = { onPresentarse(carta.takeIf { it.isNotBlank() }) },
                    enabled = !enviando
                ) {
                    Text(stringResource(R.string.candidatura_presentar))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) {
                Text(stringResource(R.string.denuncia_cerrar_dialogo))
            }
        }
    )
}
