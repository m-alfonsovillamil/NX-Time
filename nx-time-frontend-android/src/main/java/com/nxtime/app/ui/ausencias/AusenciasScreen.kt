package com.nxtime.app.ui.ausencias

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.Avatar
import com.nxtime.app.ui.components.CampanaDeAvisos
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.TarjetaAusencia
import com.nxtime.app.ui.util.etiqueta
import com.nxtime.app.ui.util.resolver

@Composable
fun AusenciasScreen(
    onIrSolicitud: () -> Unit,
    contadorAvisos: Int,
    onIrAvisos: () -> Unit,
    iniciales: String,
    onIrPerfil: () -> Unit,
    viewModel: AusenciasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    // Sin flecha de volver: es un destino de la barra de navegación.
    PantallaConBarra(
        titulo = stringResource(R.string.ausencias_titulo),
        acciones = {
            CampanaDeAvisos(contadorAvisos, onIrAvisos)
            Avatar(
                iniciales = iniciales,
                descripcion = stringResource(R.string.perfil_abrir),
                onClick = onIrPerfil,
                modifier = Modifier.padding(end = 12.dp)
            )
        },
        accionFlotante = {
            FloatingActionButton(onClick = onIrSolicitud) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.nav_solicitar)
                )
            }
        }
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.peticiones.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            when {
                estado.error != null -> EstadoErrorPantalla(
                    mensaje = estado.error!!.resolver(),
                    onReintentar = viewModel::cargar
                )

                // "Aún no has pedido ninguna" y "ninguna cumple el filtro" son
                // dos vacíos distintos: el segundo necesita una salida.
                estado.peticiones.isEmpty() -> EstadoVacio(
                    titulo = stringResource(R.string.ausencias_vacio_titulo),
                    texto = stringResource(R.string.ausencias_vacio_texto)
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Filtros(
                            peticiones = estado.peticiones,
                            filtro = estado.filtro,
                            onCambiar = viewModel::cambiarFiltro,
                            onQuitar = viewModel::quitarFiltros
                        )
                    }
                    val visibles = estado.visibles
                    if (visibles.isEmpty()) {
                        item { SinResultados(onQuitar = viewModel::quitarFiltros) }
                    } else {
                        items(visibles, key = { it.id }) { peticion ->
                            TarjetaAusencia(peticion = peticion)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Estado, año y tipo, en una fila desplazable: con el texto ampliado por
 * accesibilidad no caben, y partirla deja las etiquetas cortadas.
 *
 * Los estados son chips que se encienden y apagan (tocar el activo lo
 * quita). Año y tipo son menús con solo lo que aparece en la lista: ofrecer
 * "Matrimonio" a quien nunca lo ha pedido llevaría siempre a una lista vacía.
 */
@Composable
private fun Filtros(
    peticiones: List<RespuestaAusencia>,
    filtro: FiltroAusencias,
    onCambiar: (FiltroAusencias) -> Unit,
    onQuitar: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        listOf(
            EstadoAusencia.PENDIENTE to R.string.ausencias_filtro_pendientes,
            EstadoAusencia.APROBADA to R.string.ausencias_filtro_aprobadas,
            EstadoAusencia.RECHAZADA to R.string.ausencias_filtro_rechazadas
        ).forEach { (valor, texto) ->
            FilterChip(
                selected = filtro.estado == valor,
                onClick = { onCambiar(filtro.copy(estado = if (filtro.estado == valor) null else valor)) },
                label = { Text(stringResource(texto), maxLines = 1) }
            )
        }

        ChipConMenu(
            texto = filtro.anio?.toString() ?: stringResource(R.string.ausencias_filtro_anio),
            seleccionado = filtro.anio != null,
            opciones = FiltroAusencias.aniosDe(peticiones).map { it.toString() to it },
            textoTodos = stringResource(R.string.ausencias_filtro_todos_los_anios),
            onElegir = { onCambiar(filtro.copy(anio = it)) }
        )

        ChipConMenu(
            texto = filtro.tipo?.let { stringResource(it.etiqueta) } ?: stringResource(R.string.ausencias_filtro_tipo),
            seleccionado = filtro.tipo != null,
            opciones = FiltroAusencias.tiposDe(peticiones).map { stringResource(it.etiqueta) to it },
            textoTodos = stringResource(R.string.ausencias_filtro_todos_los_tipos),
            onElegir = { onCambiar(filtro.copy(tipo = it)) }
        )

        if (filtro.activo) {
            TextButton(onClick = onQuitar) { Text(stringResource(R.string.ausencias_filtro_quitar)) }
        }
    }
}

@Composable
private fun <T> ChipConMenu(
    texto: String,
    seleccionado: Boolean,
    opciones: List<Pair<String, T>>,
    textoTodos: String,
    onElegir: (T?) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = seleccionado,
            onClick = { abierto = true },
            label = { Text(texto, maxLines = 1) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text(textoTodos) },
                onClick = {
                    onElegir(null)
                    abierto = false
                }
            )
            opciones.forEach { (etiqueta, valor) ->
                DropdownMenuItem(
                    text = { Text(etiqueta) },
                    onClick = {
                        onElegir(valor)
                        abierto = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SinResultados(onQuitar: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.ausencias_filtro_sin_resultados),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onQuitar) { Text(stringResource(R.string.ausencias_filtro_quitar)) }
    }
}
