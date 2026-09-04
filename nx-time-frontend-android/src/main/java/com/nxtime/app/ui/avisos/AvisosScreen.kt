package com.nxtime.app.ui.avisos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nxtime.app.R
import com.nxtime.app.data.dto.AvisoDTO
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.navegacion.rutaDeAviso
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * La lista de avisos que hay detrás de la campana.
 *
 * El `viewModel` llega **sin valor por defecto**, al revés que en el
 * resto de pantallas: la instancia la crea `NxTimeNavHost` para que la
 * comparta con el contador de la barra, y un `viewModel(factory = ...)`
 * de cortesía aquí crearía una segunda que se desincronizaría en cuanto
 * se marcase algo como leído.
 */
@Composable
fun AvisosScreen(
    onVolver: () -> Unit,
    onNavegar: (String) -> Unit,
    viewModel: AvisosViewModel
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(
        titulo = stringResource(R.string.avisos_titulo),
        onVolver = onVolver,
        acciones = {
            if (estado.noLeidos > 0) {
                TextButton(onClick = viewModel::marcarTodosLeidos) {
                    Text(stringResource(R.string.avisos_marcar_todos))
                }
            }
        }
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.avisos.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            when {
                estado.error != null -> EstadoErrorPantalla(
                    mensaje = estado.error!!.resolver(),
                    onReintentar = viewModel::cargar
                )

                estado.avisos.isEmpty() -> EstadoVacio(
                    titulo = stringResource(R.string.avisos_vacio_titulo),
                    texto = stringResource(R.string.avisos_vacio_texto)
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(estado.avisos, key = { it.id }) { aviso ->
                        TarjetaAviso(
                            aviso = aviso,
                            onClick = {
                                viewModel.marcarLeido(aviso.id)
                                // Si esta versión de la app no conoce el
                                // destino, el aviso se queda leído pero
                                // no lleva a ninguna parte.
                                rutaDeAviso(aviso.rutaDestino)?.let(onNavegar)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaAviso(aviso: AvisoDTO, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Un punto en vez de negrita: la negrita en el título haría
            // que un aviso leído y otro sin leer se distinguieran solo
            // por el grosor de la letra, que es justo lo que peor se ve
            // de reojo.
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (!aviso.leido) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {}
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = aviso.titulo,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = aviso.cuerpo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = DateFormats.fechaYHora(aviso.creadoEn),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
