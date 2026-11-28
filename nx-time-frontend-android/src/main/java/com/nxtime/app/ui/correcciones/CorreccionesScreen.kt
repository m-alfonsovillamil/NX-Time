package com.nxtime.app.ui.correcciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.nxtime.app.ui.components.finDeLista
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.CorreccionDTO
import com.nxtime.app.data.dto.Modalidad
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BotonDeRevision
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.FilaDeCambio
import com.nxtime.app.ui.components.FilaDeDato
import com.nxtime.app.ui.components.Insignia
import com.nxtime.app.ui.components.NotaDeRevision
import com.nxtime.app.ui.components.PesoDeBoton
import com.nxtime.app.ui.components.TarjetaDeRevision
import com.nxtime.app.ui.components.TonoDeInsignia
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
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
                    finDeLista(estado.paginasMias, viewModel::cargarMasMias)
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
private fun textoDe(modalidad: Modalidad?): String = when (modalidad) {
    Modalidad.PRESENCIAL -> stringResource(R.string.modalidad_presencial)
    Modalidad.A_DISTANCIA -> stringResource(R.string.modalidad_a_distancia)
    null -> "—"
}

@Composable
private fun TarjetaCorreccion(
    correccion: CorreccionDTO,
    onAprobar: (() -> Unit)? = null,
    onRechazar: (() -> Unit)? = null,
    onDisputar: (() -> Unit)? = null
) {
    // El día del fichaje: el de la entrada actual, o la propuesta si el
    // fichaje no tenía (una jornada que se pide añadir entera).
    val dia = DateFormats.fechaLarga(correccion.horaEntradaActual ?: correccion.horaEntradaPropuesta)
    val estado = EstadoCorreccion.de(correccion.estado)
    // Los botones dependen SOLO de lo que diga el servidor.
    val disputa = correccion.puedoDisputar && onDisputar != null
    val resuelve = correccion.puedoResolver && onRechazar != null && onAprobar != null

    TarjetaDeRevision(
        titulo = correccion.empleado.nombre,
        subtitulo = if (correccion.solicitante.nombre == correccion.empleado.nombre) {
            stringResource(R.string.correcciones_fichaje_del, dia)
        } else {
            stringResource(R.string.correcciones_fichaje_del_propone, dia, correccion.solicitante.nombre)
        },
        estado = estado?.let {
            {
                Insignia(
                    texto = stringResource(it.etiqueta),
                    tono = when (it) {
                        EstadoCorreccion.APROBADA -> TonoDeInsignia.EXITO
                        EstadoCorreccion.RECHAZADA -> TonoDeInsignia.ERROR
                        EstadoCorreccion.PENDIENTE, EstadoCorreccion.EN_DISPUTA -> TonoDeInsignia.AVISO
                    }
                )
            }
        },
        contenido = {
            Spacer(Modifier.height(6.dp))
            // Las horas de antes y las propuestas, juntas y en columna: quien
            // decide necesita comparar, y mandarle al historial a buscarlo
            // convierte una decisión de dos segundos en una navegación.
            /*
             * Una solicitud que SOLO añade una pausa (ADR 015) lleva las mismas
             * horas propuestas que actuales, así que pintarla como las demás
             * diría "08:00 → 08:00": una corrección que no cambia nada, que es
             * justo lo que parece al aprobarla a ciegas. Las filas de horas
             * solo salen si las horas cambian, y la pausa, si la hay.
             */
            val cambiaHoras = correccion.horaEntradaActual != correccion.horaEntradaPropuesta ||
                    correccion.horaSalidaActual != correccion.horaSalidaPropuesta
            if (cambiaHoras) {
                FilaDeCambio(
                    etiqueta = stringResource(R.string.correcciones_fila_entrada),
                    antes = DateFormats.hora(correccion.horaEntradaActual),
                    despues = DateFormats.hora(correccion.horaEntradaPropuesta)
                )
                FilaDeCambio(
                    etiqueta = stringResource(R.string.correcciones_fila_salida),
                    antes = DateFormats.hora(correccion.horaSalidaActual),
                    despues = DateFormats.hora(correccion.horaSalidaPropuesta)
                )
            }
            if (correccion.pausaInicioPropuesta != null && correccion.pausaFinPropuesta != null) {
                FilaDeDato(
                    etiqueta = stringResource(R.string.correcciones_fila_pausa),
                    valor = stringResource(
                        R.string.correcciones_pausa_valor,
                        DateFormats.hora(correccion.pausaInicioPropuesta),
                        DateFormats.hora(correccion.pausaFinPropuesta)
                    )
                )
            }
            if (correccion.modalidadPropuesta != null && correccion.modalidadPropuesta != correccion.modalidadActual) {
                FilaDeCambio(
                    etiqueta = stringResource(R.string.correcciones_fila_modalidad),
                    antes = textoDe(correccion.modalidadActual),
                    despues = textoDe(correccion.modalidadPropuesta)
                )
            }
            if (correccion.repartoPropuesto.isNotEmpty()) {
                FilaDeDato(
                    etiqueta = stringResource(R.string.correcciones_fila_proyectos),
                    valor = correccion.repartoPropuesto.joinToString(", ") {
                        "${it.codigo} " + DateFormats.minutos(it.minutos)
                    }
                )
            }

            NotaDeRevision(texto = correccion.motivo, etiqueta = stringResource(R.string.correcciones_motivo))

            correccion.motivoDisputa?.let {
                NotaDeRevision(texto = it, etiqueta = stringResource(R.string.correcciones_no_acepta_etiqueta))
            }

            correccion.comentarioResolucion?.let {
                Text(
                    text = stringResource(R.string.correcciones_comentario, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        acciones = if (disputa || resuelve) {
            {
                if (disputa) {
                    BotonDeRevision(
                        texto = stringResource(R.string.correcciones_disputar),
                        onClick = onDisputar!!,
                        peso = PesoDeBoton.CONTORNO,
                        negativo = true
                    )
                }
                if (resuelve) {
                    BotonDeRevision(
                        texto = stringResource(R.string.correcciones_rechazar),
                        onClick = onRechazar!!,
                        peso = PesoDeBoton.CONTORNO,
                        negativo = true
                    )
                    BotonDeRevision(
                        texto = stringResource(R.string.correcciones_aprobar),
                        onClick = onAprobar!!
                    )
                }
            }
        } else {
            null
        }
    )
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
