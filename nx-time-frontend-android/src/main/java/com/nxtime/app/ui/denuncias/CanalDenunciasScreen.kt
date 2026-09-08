package com.nxtime.app.ui.denuncias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.DenunciaDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.resolver

/**
 * La bandeja del canal de denuncias (Fase G). Solo ADMIN.
 *
 * El orden lo pone el servidor: abiertas primero y, dentro, las **más
 * antiguas arriba**. No es estético — los plazos del art. 9.2 corren
 * desde que se presentó la denuncia, así que la más vieja sin acusar es
 * la que está más cerca de incumplirse, y una lista por fecha
 * descendente enseñaría justo las que menos urgen.
 *
 * La pantalla no reproduce ninguna regla de instrucción: que cerrar
 * exija conclusión o que un expediente cerrado no se reabra lo dice el
 * servidor, y aquí se enseña su respuesta.
 */
@Composable
fun CanalDenunciasScreen(
    onVolver: () -> Unit,
    viewModel: CanalDenunciasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(
        titulo = stringResource(R.string.canal_denuncias_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.bandeja.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            if (estado.bandeja.isEmpty() && !estado.cargando) {
                // Aquí sí EstadoVacio: sustituye a la lista entera, no va
                // dentro de un item{} de LazyColumn.
                EstadoVacio(
                    titulo = stringResource(R.string.canal_denuncias_vacio_titulo),
                    texto = stringResource(R.string.canal_denuncias_vacio_texto)
                )
            } else {
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

                    items(estado.bandeja, key = { it.id }) { resumen ->
                        FilaDenuncia(resumen = resumen, onAbrir = { viewModel.abrir(resumen.id) })
                    }
                }
            }
        }
    }

    estado.expediente?.let { expediente ->
        DialogoInstruccion(
            expediente = expediente,
            enviando = estado.enviando,
            onResponder = viewModel::responder,
            onCambiarEstado = viewModel::cambiarEstado,
            onCerrar = viewModel::cerrarExpediente
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

/**
 * El expediente con lo que puede hacer quien instruye.
 *
 * Los botones de cerrar solo aparecen con la conclusión escrita, porque
 * cerrar sin ella no es un caso que valga la pena mandar al servidor
 * para que lo rechace: la ley obliga a **responder**, no a dar la razón,
 * y archivar una denuncia que no se sostiene también hay que explicarlo.
 */
@Composable
private fun DialogoInstruccion(
    expediente: DenunciaDTO,
    enviando: Boolean,
    onResponder: (String) -> Unit,
    onCambiarEstado: (EstadoDenuncia, String?) -> Unit,
    onCerrar: () -> Unit
) {
    var respuesta by remember(expediente.id) { mutableStateOf("") }
    var conclusion by remember(expediente.id) { mutableStateOf("") }
    val estado = EstadoDenuncia.de(expediente.estado)
    val abierta = estado?.estaAbierta == true

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(expediente.categoriaEtiqueta) },
        text = {
            Column {
                estado?.let {
                    Text(
                        text = stringResource(it.etiqueta),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    // Dos cadenas y no una con un hueco: "La puso %s"
                    // con el hueco vacío es como se acaba enseñando "La
                    // puso ." en la pantalla que menos se puede permitir
                    // parecer descuidada.
                    text = if (expediente.anonima) {
                        stringResource(R.string.denuncia_es_anonima)
                    } else {
                        stringResource(
                            R.string.denuncia_la_puso, expediente.denunciante.orEmpty())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AvisoDePlazo(
                    diasHastaAcuse = expediente.diasHastaAcuse,
                    diasHastaRespuesta = expediente.diasHastaRespuesta
                )

                Spacer(Modifier.height(8.dp))
                Text(text = expediente.descripcion, style = MaterialTheme.typography.bodyMedium)

                expediente.conclusion?.let { texto ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.denuncia_conclusion),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(text = texto, style = MaterialTheme.typography.bodyMedium)
                }

                if (expediente.mensajes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    expediente.mensajes.forEach { mensaje ->
                        Spacer(Modifier.height(8.dp))
                        MensajeDeDenuncia(mensaje)
                    }
                }

                if (abierta) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = respuesta,
                        onValueChange = { respuesta = it },
                        label = { Text(stringResource(R.string.denuncia_responder_instructor)) },
                        supportingText = {
                            Text(stringResource(R.string.denuncia_responder_acusa))
                        },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = conclusion,
                        onValueChange = { conclusion = it },
                        label = { Text(stringResource(R.string.denuncia_conclusion)) },
                        supportingText = {
                            Text(stringResource(R.string.denuncia_conclusion_ayuda))
                        },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    if (estado == EstadoDenuncia.RECIBIDA) {
                        TextButton(
                            onClick = { onCambiarEstado(EstadoDenuncia.EN_INVESTIGACION, null) },
                            enabled = !enviando
                        ) {
                            Text(stringResource(R.string.denuncia_pasar_a_investigacion))
                        }
                    }
                    TextButton(
                        onClick = { onCambiarEstado(EstadoDenuncia.RESUELTA, conclusion) },
                        enabled = !enviando && conclusion.isNotBlank()
                    ) {
                        Text(stringResource(R.string.denuncia_cerrar_resuelta))
                    }
                    TextButton(
                        onClick = { onCambiarEstado(EstadoDenuncia.ARCHIVADA, conclusion) },
                        enabled = !enviando && conclusion.isNotBlank()
                    ) {
                        Text(stringResource(R.string.denuncia_cerrar_archivada))
                    }
                }
            }
        },
        confirmButton = {
            if (abierta) {
                TextButton(
                    onClick = {
                        onResponder(respuesta)
                        respuesta = ""
                    },
                    enabled = !enviando && respuesta.isNotBlank()
                ) {
                    Text(stringResource(R.string.denuncia_enviar_mensaje))
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
