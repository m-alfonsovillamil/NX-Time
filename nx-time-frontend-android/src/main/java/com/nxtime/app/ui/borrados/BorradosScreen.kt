package com.nxtime.app.ui.borrados

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.nxtime.app.R
import com.nxtime.app.data.dto.SolicitudBorradoDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Solicitudes de borrado de datos pendientes (ADR 016). Solo RRHH y ADMIN.
 *
 * Ejecutar es lo único irreversible de toda la aplicación, y la pantalla lo
 * trata así: el botón se apaga mientras haya bloqueos, y aun sin ellos pide
 * una confirmación que dice exactamente qué se borra y qué se queda.
 */
@Composable
fun BorradosScreen(
    onVolver: () -> Unit,
    viewModel: BorradosViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var aEjecutar by remember { mutableStateOf<SolicitudBorradoDTO?>(null) }
    var aRechazar by remember { mutableStateOf<SolicitudBorradoDTO?>(null) }
    val contexto = LocalContext.current

    PantallaConBarra(titulo = stringResource(R.string.borrados_titulo), onVolver = onVolver) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.pendientes.isNotEmpty(),
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
                if (estado.pendientes.isEmpty() && !estado.cargando) {
                    item {
                        EstadoVacio(
                            titulo = stringResource(R.string.borrados_vacio_titulo),
                            texto = stringResource(R.string.borrados_vacio_texto)
                        )
                    }
                }
                items(estado.pendientes, key = { it.id }) { solicitud ->
                    TarjetaBorrado(
                        solicitud = solicitud,
                        enviando = estado.enviando,
                        onEjecutar = { aEjecutar = solicitud },
                        onRechazar = { aRechazar = solicitud }
                    )
                }
            }
        }
    }

    aEjecutar?.let { solicitud ->
        DialogoEjecutar(
            nombre = solicitud.nombre,
            onConfirma = {
                viewModel.ejecutar(solicitud.id)
                aEjecutar = null
            },
            onCancela = { aEjecutar = null }
        )
    }

    aRechazar?.let { solicitud ->
        DialogoRechazar(
            onConfirma = { comentario ->
                viewModel.rechazar(solicitud.id, comentario)
                aRechazar = null
            },
            onCancela = { aRechazar = null }
        )
    }

    estado.aviso?.let { aviso ->
        val texto = aviso.resolver()
        LaunchedEffect(aviso) {
            Toast.makeText(contexto, texto, Toast.LENGTH_SHORT).show()
            viewModel.avisoMostrado()
        }
    }
}

@Composable
private fun TarjetaBorrado(
    solicitud: SolicitudBorradoDTO,
    enviando: Boolean,
    onEjecutar: () -> Unit,
    onRechazar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(solicitud.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                text = solicitud.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.borrados_pedida_el, DateFormats.fechaLarga(solicitud.creadaEn)),
                style = MaterialTheme.typography.bodyMedium
            )
            solicitud.motivo?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.borrados_motivo, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Lo que impide ejecutar, tal cual lo dice el servidor. En rojo:
            // es lo que hay que resolver antes, no información de contexto.
            if (solicitud.bloqueos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.borrados_bloqueos),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                solicitud.bloqueos.forEach { bloqueo ->
                    Text(
                        text = "• $bloqueo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRechazar, enabled = !enviando) {
                    Text(stringResource(R.string.borrados_rechazar))
                }
                TextButton(
                    onClick = onEjecutar,
                    enabled = !enviando && solicitud.bloqueos.isEmpty()
                ) {
                    Text(
                        text = stringResource(R.string.borrados_ejecutar),
                        fontWeight = FontWeight.Bold,
                        color = if (!enviando && solicitud.bloqueos.isEmpty()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }
    }
}

/**
 * La confirmación dice qué se borra y qué no **antes** de pulsar, con el
 * nombre de la persona delante: equivocarse de tarjeta aquí no tiene arreglo.
 */
@Composable
private fun DialogoEjecutar(nombre: String, onConfirma: () -> Unit, onCancela: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(stringResource(R.string.borrados_ejecutar_titulo, nombre)) },
        text = { Text(stringResource(R.string.borrados_ejecutar_detalle)) },
        confirmButton = {
            TextButton(onClick = onConfirma) {
                Text(
                    stringResource(R.string.borrados_ejecutar_confirmar),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}

@Composable
private fun DialogoRechazar(onConfirma: (String) -> Unit, onCancela: () -> Unit) {
    var comentario by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(stringResource(R.string.borrados_rechazar)) },
        text = {
            Column {
                Text(stringResource(R.string.borrados_rechazar_ayuda), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = comentario,
                    onValueChange = { comentario = it },
                    label = { Text(stringResource(R.string.borrados_comentario)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = comentario.isNotBlank(), onClick = { onConfirma(comentario) }) {
                Text(stringResource(R.string.borrados_rechazar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}
