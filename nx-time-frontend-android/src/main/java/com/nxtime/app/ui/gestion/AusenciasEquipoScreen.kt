package com.nxtime.app.ui.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.TarjetaAusencia
import com.nxtime.app.ui.util.resolver

/**
 * Las ausencias del equipo.
 *
 * @param resueltas false = las pendientes de responder, con sus botones
 *   de aprobar y rechazar; true = el histórico de las ya resueltas, que
 *   es solo de lectura.
 */
@Composable
fun AusenciasEquipoScreen(
    resueltas: Boolean,
    onVolver: () -> Unit,
    viewModel: AusenciasEquipoViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var peticionARechazar by remember { mutableStateOf<RespuestaAusencia?>(null) }

    LaunchedEffect(resueltas) {
        viewModel.mostrar(resueltas)
    }

    PantallaConBarra(
        titulo = stringResource(
            if (resueltas) R.string.resueltas_titulo else R.string.pendientes_titulo
        ),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.peticiones.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
        when {
            // Un error de carga deja la pantalla sin nada que enseñar,
            // así que ocupa toda la pantalla y ofrece reintentar. Un
            // error al resolver una petición, en cambio, va como aviso
            // encima de la lista, que sigue siendo válida.
            estado.error != null && estado.peticiones.isEmpty() -> EstadoErrorPantalla(
                mensaje = estado.error!!.resolver(),
                onReintentar = viewModel::cargar
            )

            estado.peticiones.isEmpty() -> EstadoVacio(
                titulo = stringResource(
                    if (resueltas) R.string.resueltas_vacio_titulo
                    else R.string.pendientes_vacio_titulo
                ),
                texto = stringResource(
                    if (resueltas) R.string.resueltas_vacio_texto
                    else R.string.pendientes_vacio_texto
                )
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                estado.error?.let { mensaje ->
                    BannerError(
                        mensaje = mensaje.resolver(),
                        onReintentar = viewModel::descartarError
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(estado.peticiones, key = { it.id }) { peticion ->
                        TarjetaAusencia(
                            peticion = peticion,
                            mostrarEmpleado = true
                        ) {
                            // Los botones solo aparecen donde sirven de
                            // algo: una petición ya resuelta no se
                            // vuelve a aprobar.
                            if (peticion.estado == EstadoAusencia.PENDIENTE) {
                                BotonesResolucion(
                                    habilitados = estado.resolviendo == null,
                                    onAprobar = { viewModel.aprobar(peticion.id) },
                                    onRechazar = { peticionARechazar = peticion }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    peticionARechazar?.let { peticion ->
        DialogoRechazo(
            onCancelar = { peticionARechazar = null },
            onConfirmar = { motivo ->
                viewModel.rechazar(peticion.id, motivo)
                peticionARechazar = null
            }
        )
    }
}

@Composable
private fun BotonesResolucion(
    habilitados: Boolean,
    onAprobar: () -> Unit,
    onRechazar: () -> Unit
) {
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onRechazar,
            enabled = habilitados,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.pendientes_rechazar))
        }
        Spacer(Modifier.size(8.dp))
        Button(
            onClick = onAprobar,
            enabled = habilitados,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.pendientes_aprobar))
        }
    }
}

/**
 * Pide el motivo del rechazo.
 *
 * El backend lo exige, así que el botón de confirmar está apagado
 * mientras el campo esté vacío: es mejor que dejar pulsar y responder
 * con un error, que es lo que hacía el diálogo anterior (aceptaba el
 * clic y luego enseñaba un Toast).
 */
@Composable
private fun DialogoRechazo(
    onCancelar: () -> Unit,
    onConfirmar: (String) -> Unit
) {
    var motivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.pendientes_rechazar)) },
        text = {
            Column {
                Text(stringResource(R.string.pendientes_motivo_obligatorio))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text(stringResource(R.string.pendientes_comentario)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(motivo) },
                enabled = motivo.isNotBlank()
            ) {
                Text(stringResource(R.string.pendientes_rechazar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(stringResource(R.string.cancelar))
            }
        }
    )
}
