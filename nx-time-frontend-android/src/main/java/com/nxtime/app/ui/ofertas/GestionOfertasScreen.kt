package com.nxtime.app.ui.ofertas

import android.content.ActivityNotFoundException
import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.nxtime.app.ui.informes.MIME_PDF
import com.nxtime.app.ui.informes.compartirInforme
import com.nxtime.app.ui.informes.guardarEnCache
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import kotlinx.coroutines.launch
import okhttp3.ResponseBody

/**
 * Publicar vacantes y valorar candidaturas (Fase H).
 *
 * Lo que la pantalla tiene que dejar claro es que **crear no es
 * publicar**: una oferta nace en borrador y sale a la plantilla cuando
 * alguien lo decide, con su propio botón. Publicar avisa a toda la
 * empresa de una vez, así que no puede ser el efecto colateral de
 * guardar un formulario.
 */
@Composable
fun GestionOfertasScreen(
    onVolver: () -> Unit,
    viewModel: GestionOfertasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var creando by remember { mutableStateOf(false) }

    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val textoSinVisor = stringResource(R.string.empresa_sin_visor)

    /**
     * El CV se escribe aquí y no en el ViewModel porque el Context es de
     * la pantalla; es el mismo reparto que en el perfil. Va a la caché y
     * se cede con el FileProvider: un currículum no se deja en la carpeta
     * de descargas, a la vista de cualquier app.
     */
    fun abrirCv(cuerpo: ResponseBody, nombre: String) {
        alcance.launch {
            val fichero = guardarEnCache(contexto, cuerpo, nombre, subcarpeta = "adjuntos")
            try {
                compartirInforme(contexto, fichero, MIME_PDF)
            } catch (_: ActivityNotFoundException) {
                // Un emulador limpio no trae visor de PDF: el fichero ya
                // está bajado, así que se avisa en vez de tirar la app.
                Toast.makeText(contexto, textoSinVisor, Toast.LENGTH_LONG).show()
            }
        }
    }

    PantallaConBarra(
        titulo = stringResource(R.string.gestion_ofertas_titulo),
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

                item {
                    Button(
                        onClick = { creando = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.oferta_nueva))
                    }
                }

                item { HorizontalDivider() }

                if (estado.ofertas.isEmpty() && !estado.cargando) {
                    item { SeccionVacia(stringResource(R.string.gestion_ofertas_vacio)) }
                }
                items(estado.ofertas, key = { it.id }) { oferta ->
                    TarjetaOfertaGestion(
                        oferta = oferta,
                        enviando = estado.enviando,
                        onPublicar = {
                            viewModel.cambiarEstado(oferta.id, EstadoOferta.ABIERTA)
                        },
                        onCerrar = {
                            viewModel.cambiarEstado(oferta.id, EstadoOferta.CERRADA)
                        },
                        onVerCandidaturas = { viewModel.abrirCandidaturas(oferta) }
                    )
                }
            }
        }
    }

    if (creando) {
        DialogoNuevaOferta(
            enviando = estado.enviando,
            onCrear = { titulo, descripcion, puesto ->
                viewModel.crear(titulo, descripcion, puesto, null)
                creando = false
            },
            onCancelar = { creando = false }
        )
    }

    estado.seleccionada?.let { oferta ->
        DialogoCandidaturas(
            oferta = oferta,
            candidaturas = estado.candidaturas,
            enviando = estado.enviando,
            cvDescargandose = estado.cvDescargandose,
            onValorar = viewModel::valorar,
            onVerCv = { candidatura -> viewModel.descargarCv(candidatura, ::abrirCv) },
            onCerrar = viewModel::cerrarCandidaturas
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

@Composable
private fun TarjetaOfertaGestion(
    oferta: OfertaDTO,
    enviando: Boolean,
    onPublicar: () -> Unit,
    onCerrar: () -> Unit,
    onVerCandidaturas: () -> Unit
) {
    val estado = EstadoOferta.de(oferta.estado)

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
                    text = oferta.titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                estado?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(it.etiqueta)) },
                        colors = if (it == EstadoOferta.ABIERTA) {
                            AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.oferta_candidaturas_cuenta, oferta.candidaturas ?: 0L),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            Row {
                if (estado == EstadoOferta.BORRADOR) {
                    TextButton(onClick = onPublicar, enabled = !enviando) {
                        Text(stringResource(R.string.oferta_publicar))
                    }
                }
                if (estado == EstadoOferta.ABIERTA) {
                    TextButton(onClick = onCerrar, enabled = !enviando) {
                        Text(stringResource(R.string.oferta_cerrar))
                    }
                }
                // Las candidaturas se miran también en una oferta
                // cerrada: cerrarla no borra a quien optó.
                if (estado != EstadoOferta.BORRADOR) {
                    TextButton(onClick = onVerCandidaturas) {
                        Text(stringResource(R.string.oferta_ver_candidaturas))
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogoNuevaOferta(
    enviando: Boolean,
    onCrear: (String, String, String?) -> Unit,
    onCancelar: () -> Unit
) {
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var puesto by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.oferta_nueva)) },
        text = {
            Column {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text(stringResource(R.string.oferta_titulo_campo)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = puesto,
                    onValueChange = { puesto = it },
                    label = { Text(stringResource(R.string.oferta_puesto_campo)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text(stringResource(R.string.oferta_descripcion_campo)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    // Se dice ANTES de guardar: si no, quien la crea se
                    // queda esperando a que la vea la plantilla.
                    text = stringResource(R.string.oferta_nace_en_borrador),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCrear(titulo, descripcion, puesto.takeIf { it.isNotBlank() }) },
                enabled = !enviando && titulo.isNotBlank() && descripcion.isNotBlank()
            ) {
                Text(stringResource(R.string.oferta_guardar_borrador))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(stringResource(R.string.cancelar))
            }
        }
    )
}

@Composable
private fun DialogoCandidaturas(
    oferta: OfertaDTO,
    candidaturas: List<CandidaturaDTO>,
    enviando: Boolean,
    cvDescargandose: Long?,
    onValorar: (Long, EstadoCandidatura, String?) -> Unit,
    onVerCv: (CandidaturaDTO) -> Unit,
    onCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(oferta.titulo) },
        text = {
            Column {
                if (candidaturas.isEmpty()) {
                    Text(
                        text = stringResource(R.string.oferta_sin_candidaturas),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                candidaturas.forEach { candidatura ->
                    FilaCandidatura(candidatura, enviando, cvDescargandose, onValorar, onVerCv)
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCerrar) {
                Text(stringResource(R.string.denuncia_cerrar_dialogo))
            }
        }
    )
}

@Composable
private fun FilaCandidatura(
    candidatura: CandidaturaDTO,
    enviando: Boolean,
    cvDescargandose: Long?,
    onValorar: (Long, EstadoCandidatura, String?) -> Unit,
    onVerCv: (CandidaturaDTO) -> Unit
) {
    var comentario by remember(candidatura.id) { mutableStateOf("") }
    val estado = EstadoCandidatura.de(candidatura.estado)

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = candidatura.candidato,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            estado?.let { Text(stringResource(it.etiqueta), style = MaterialTheme.typography.labelMedium) }
        }

        Text(
            text = stringResource(
                R.string.candidatura_presentada_el,
                DateFormats.fechaLarga(candidatura.creadoEn)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // El CV congelado, con su nombre: es el que se presentó, no el
        // que esa persona tenga hoy en su perfil.
        Text(
            text = stringResource(R.string.candidatura_cv_adjunto, candidatura.cvNombre),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Y se puede abrir desde aquí: decidir sobre alguien sin leer lo
        // que presentó obligaba a pedírselo por otro sitio.
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onVerCv(candidatura) },
                enabled = cvDescargandose == null
            ) {
                Text(stringResource(R.string.candidatura_ver_cv))
            }
            if (cvDescargandose == candidatura.id) {
                Text(
                    text = stringResource(R.string.candidatura_cv_abriendo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        candidatura.carta?.let { carta ->
            Spacer(Modifier.height(4.dp))
            Text(text = carta, style = MaterialTheme.typography.bodyMedium)
        }

        // Los botones solo mientras se pueda decidir. Sobre la propia
        // candidatura 'puedoValorar' llega false desde el servidor: un
        // GESTOR puede optar a una vacante, y entonces la decide otro.
        if (candidatura.puedoValorar && estado?.esFinal == false) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = comentario,
                onValueChange = { comentario = it },
                label = { Text(stringResource(R.string.candidatura_comentario)) },
                supportingText = {
                    Text(stringResource(R.string.candidatura_comentario_ayuda))
                },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                if (estado == EstadoCandidatura.RECIBIDA) {
                    TextButton(
                        onClick = {
                            onValorar(candidatura.id, EstadoCandidatura.EN_PROCESO, comentario)
                        },
                        enabled = !enviando
                    ) {
                        Text(stringResource(R.string.candidatura_a_proceso))
                    }
                }
                TextButton(
                    onClick = {
                        onValorar(candidatura.id, EstadoCandidatura.SELECCIONADA, comentario)
                    },
                    enabled = !enviando
                ) {
                    Text(stringResource(R.string.candidatura_seleccionar))
                }
                TextButton(
                    // Descartar exige comentario: lo pide el servidor, y
                    // aquí se apaga el botón para no mandar una petición
                    // que se sabe rechazada.
                    onClick = {
                        onValorar(candidatura.id, EstadoCandidatura.DESCARTADA, comentario)
                    },
                    enabled = !enviando && comentario.isNotBlank()
                ) {
                    Text(stringResource(R.string.candidatura_descartar))
                }
            }
        }

        candidatura.comentario?.let { texto ->
            Spacer(Modifier.height(4.dp))
            Text(text = texto, style = MaterialTheme.typography.bodySmall)
        }
    }
}
