package com.nxtime.app.ui.denuncias

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.nxtime.app.data.dto.DenunciaCreadaDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.SeccionVacia
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.resolver

/**
 * El canal de denuncias visto por quien denuncia (Fase G).
 *
 * Tres cosas en una pantalla: presentar, seguir con un código y las que
 * presentaste identificándote.
 *
 * **Lo que esta pantalla tiene que dejar claro por encima de todo es qué
 * significa marcar "anónima".** No es una casilla de preferencias: si se
 * marca, el sistema no guarda quién eres —tampoco a posteriori—, y a
 * cambio la única forma de volver a tu expediente es el código que se
 * entrega una vez. Decirlo después, cuando ya no se puede recuperar, es
 * llegar tarde; por eso el texto está junto al interruptor y el diálogo
 * del código obliga a confirmar.
 */
@Composable
fun DenunciasScreen(
    onVolver: () -> Unit,
    viewModel: DenunciasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(
        titulo = stringResource(R.string.denuncias_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = true,
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

                item { TarjetaQueEsElCanal() }

                item {
                    FormularioDenuncia(
                        enviando = estado.enviando,
                        onPresentar = viewModel::presentar
                    )
                }

                item { HorizontalDivider() }

                item {
                    BuscarPorCodigo(
                        cargando = estado.cargando,
                        onBuscar = viewModel::buscarPorCodigo
                    )
                }

                item { HorizontalDivider() }

                item {
                    Text(
                        text = stringResource(R.string.denuncias_mias),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (estado.mias.isEmpty() && !estado.cargando) {
                    // SeccionVacia y no EstadoVacio: el segundo ocupa la
                    // pantalla y hace scroll propio, y dentro de un item{}
                    // de LazyColumn revienta la app.
                    item { SeccionVacia(stringResource(R.string.denuncias_mias_vacio)) }
                }
                items(estado.mias, key = { it.id }) { resumen ->
                    FilaDenuncia(resumen = resumen, onAbrir = { viewModel.abrirMia(resumen.id) })
                }
            }
        }
    }

    estado.recienCreada?.let { creada ->
        DialogoCodigo(creada = creada, onConfirma = viewModel::codigoGuardado)
    }

    estado.expediente?.let { expediente ->
        DialogoExpediente(
            expediente = expediente,
            enviando = estado.enviando,
            onResponder = viewModel::responder,
            onCerrar = viewModel::cerrarExpediente
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

/** Qué es esto y qué obliga la ley, en tres líneas. */
@Composable
private fun TarjetaQueEsElCanal() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.denuncias_que_es_titulo),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.denuncias_que_es_texto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioDenuncia(
    enviando: Boolean,
    onPresentar: (CategoriaDenuncia, String, Boolean) -> Unit
) {
    var categoria by remember { mutableStateOf(CategoriaDenuncia.ACOSO) }
    var desplegado by remember { mutableStateOf(false) }
    var descripcion by remember { mutableStateOf("") }
    // Sin valor "por defecto" que decida por el usuario: arranca en
    // false porque el interruptor tiene que arrancar en algún sitio, y
    // el texto de al lado explica las dos opciones antes de enviar.
    var anonima by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.denuncias_presentar),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = desplegado,
                onExpandedChange = { desplegado = it }
            ) {
                OutlinedTextField(
                    value = stringResource(categoria.etiqueta),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.denuncia_categoria)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = desplegado)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = desplegado,
                    onDismissRequest = { desplegado = false }
                ) {
                    CategoriaDenuncia.entries.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(stringResource(opcion.etiqueta)) },
                            onClick = {
                                categoria = opcion
                                desplegado = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text(stringResource(R.string.denuncia_descripcion)) },
                supportingText = { Text(stringResource(R.string.denuncia_descripcion_ayuda)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.denuncia_anonima),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        // El texto CAMBIA con el interruptor: lo que hay
                        // que entender no es "qué es el anonimato" sino
                        // qué va a pasar con lo que estoy a punto de
                        // enviar, y eso son dos frases distintas.
                        text = stringResource(
                            if (anonima) R.string.denuncia_anonima_si
                            else R.string.denuncia_anonima_no
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = anonima, onCheckedChange = { anonima = it })
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onPresentar(categoria, descripcion, anonima)
                    descripcion = ""
                },
                enabled = !enviando && descripcion.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.denuncia_enviar))
            }
        }
    }
}

@Composable
private fun BuscarPorCodigo(cargando: Boolean, onBuscar: (String) -> Unit) {
    var codigo by remember { mutableStateOf("") }

    Column {
        Text(
            text = stringResource(R.string.denuncias_seguir),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.denuncias_seguir_ayuda),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it },
            label = { Text(stringResource(R.string.denuncia_codigo)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onBuscar(codigo) },
            enabled = !cargando && codigo.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.denuncia_buscar))
        }
    }
}

/**
 * El código, la única vez que se puede ver.
 *
 * No tiene botón de cancelar ni se cierra tocando fuera
 * (`onDismissRequest` vacío): descartarlo sin querer, con el pulgar en
 * el borde de la pantalla, dejaría a alguien sin acceso a su expediente
 * para siempre. El único camino es el botón que confirma que ya lo ha
 * guardado.
 */
@Composable
private fun DialogoCodigo(creada: DenunciaCreadaDTO, onConfirma: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.denuncia_codigo_titulo)) },
        text = {
            Column {
                Text(
                    text = creada.codigoSeguimiento,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    // El aviso lo escribe el SERVIDOR: si dependiera de
                    // que cada cliente se acuerde de enseñarlo, el
                    // primero que lo olvide deja a alguien fuera de su
                    // propio expediente.
                    text = creada.avisoImportante,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirma) {
                Text(stringResource(R.string.denuncia_codigo_guardado))
            }
        }
    )
}

/** El expediente y su conversación, con la caja para contestar. */
@Composable
private fun DialogoExpediente(
    expediente: com.nxtime.app.data.dto.DenunciaDTO,
    enviando: Boolean,
    onResponder: (String) -> Unit,
    onCerrar: () -> Unit
) {
    var respuesta by remember(expediente.id) { mutableStateOf("") }
    val estado = EstadoDenuncia.de(expediente.estado)

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
                AvisoDePlazo(
                    diasHastaAcuse = expediente.diasHastaAcuse,
                    diasHastaRespuesta = expediente.diasHastaRespuesta
                )

                Spacer(Modifier.height(8.dp))
                Text(text = expediente.descripcion, style = MaterialTheme.typography.bodyMedium)

                expediente.conclusion?.let { conclusion ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.denuncia_conclusion),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(text = conclusion, style = MaterialTheme.typography.bodyMedium)
                }

                if (expediente.mensajes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    expediente.mensajes.forEach { mensaje ->
                        Spacer(Modifier.height(8.dp))
                        MensajeDeDenuncia(mensaje)
                    }
                }

                // Un expediente cerrado no admite mensajes: el servidor
                // los rechaza con 409, así que ofrecer la caja seria
                // ofrecer algo que va a fallar.
                if (estado?.estaAbierta == true) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = respuesta,
                        onValueChange = { respuesta = it },
                        label = { Text(stringResource(R.string.denuncia_responder)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (estado?.estaAbierta == true) {
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
