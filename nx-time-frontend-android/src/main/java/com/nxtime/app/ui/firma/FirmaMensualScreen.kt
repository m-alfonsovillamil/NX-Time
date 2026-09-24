package com.nxtime.app.ui.firma

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.nxtime.app.data.dto.MesParaFirmarDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.SeccionVacia
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import java.time.YearMonth

/**
 * Firmar mi registro (Fase B3, ADR 025).
 *
 * Una tarjeta por mes terminado. Lo que la pantalla tiene que dejar claro es
 * qué se firma y qué no es: es aceptar que el registro es correcto, no una
 * firma electrónica cualificada, y una corrección posterior la deja sin
 * efecto. Por eso firmar pasa siempre por una confirmación que lo dice con el
 * resumen del mes delante.
 */
@Composable
fun FirmaMensualScreen(
    onVolver: () -> Unit,
    viewModel: FirmaMensualViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var aFirmar by remember { mutableStateOf<MesParaFirmarDTO?>(null) }

    PantallaConBarra(
        titulo = stringResource(R.string.firma_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.cargado,
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                estado.error?.let { mensaje ->
                    item { BannerError(mensaje = mensaje.resolver(), onReintentar = viewModel::descartarError) }
                }
                item {
                    Text(
                        text = stringResource(R.string.firma_explicacion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (estado.meses.isEmpty() && estado.cargado && !estado.cargando) {
                    item { SeccionVacia(stringResource(R.string.firma_vacio)) }
                }
                items(estado.meses, key = { "${it.anio}-${it.mes}" }) { mes ->
                    TarjetaMes(
                        mes = mes,
                        firmando = estado.firmando == mes,
                        onFirmar = { aFirmar = mes }
                    )
                }
            }
        }
    }

    aFirmar?.let { mes ->
        val nombre = nombreDelMes(mes)
        AlertDialog(
            onDismissRequest = { aFirmar = null },
            title = { Text(stringResource(R.string.firma_confirmar_titulo, nombre)) },
            text = {
                Text(stringResource(R.string.firma_confirmar_texto, nombre, mes.jornadas, horasDelMes(mes)))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.firmar(mes)
                    aFirmar = null
                }) { Text(stringResource(R.string.firma_firmar)) }
            },
            dismissButton = {
                TextButton(onClick = { aFirmar = null }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

@Composable
private fun TarjetaMes(mes: MesParaFirmarDTO, firmando: Boolean, onFirmar: () -> Unit) {
    val firma = mes.firma
    val vigente = firma?.estado == "VIGENTE"
    val invalidada = firma?.estado == "INVALIDADA"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nombreDelMes(mes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(
                                when {
                                    vigente -> R.string.firma_estado_firmado
                                    invalidada -> R.string.firma_estado_invalidada
                                    else -> R.string.firma_estado_pendiente
                                }
                            )
                        )
                    },
                    colors = if (vigente) {
                        AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.tertiary)
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )
            }

            Text(
                text = stringResource(R.string.firma_jornadas, mes.jornadas, horasDelMes(mes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (vigente && firma != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.firma_firmado_el, DateFormats.fechaYHora(firma.firmadaEn), firma.hash.take(12)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                firma.visadaPor?.let {
                    Text(
                        text = stringResource(R.string.firma_visado, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (invalidada && firma != null) {
                firma.motivoInvalidacion?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.firma_invalidada_porque, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // El porqué no se puede firmar, redactado por el servidor.
            mes.bloqueo?.let {
                Spacer(Modifier.height(4.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }

            if (mes.puedeFirmar) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onFirmar, enabled = !firmando) {
                        Text(stringResource(R.string.firma_firmar))
                    }
                }
            }
        }
    }
}

private fun nombreDelMes(mes: MesParaFirmarDTO): String = DateFormats.mesYAnio(YearMonth.of(mes.anio, mes.mes))

/** Lo trabajado en el mes, neto de pausas: lo que se acepta al firmar. */
private fun horasDelMes(mes: MesParaFirmarDTO): String = DateFormats.minutos(mes.segundosNetos / 60)
