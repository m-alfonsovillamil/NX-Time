package com.nxtime.app.ui.proyectos

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.ProyectoDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Proyectos de la empresa (Fase D).
 *
 * Dos vistas en la misma pantalla: la lista y, al tocar uno, su detalle
 * con quién ha pasado por él y las horas del mes. Volver es "atrás", sin
 * entrada nueva en el grafo de navegación: el detalle no tiene URL
 * propia ni hace falta que la tenga.
 *
 * @param puedeGestionar si se ofrecen el alta, el cierre y las
 *   asignaciones (`proyecto:gestionar`). Sin ella la pantalla queda en
 *   solo lectura.
 *
 * **Hoy esa rama no se alcanza**, y conviene saberlo en vez de
 * descubrirlo: a esta pantalla se entra desde el panel de gestión, que ya
 * exige ser GESTOR, y `proyecto:gestionar` empieza justo ahí. El
 * parámetro existe porque el backend distingue las dos authorities y
 * porque el día que estos proyectos se enseñen a la plantilla —la lista
 * no tiene nada confidencial— la pantalla ya está preparada. Lo que un
 * empleado SÍ ve hoy es su proyecto actual, en su perfil.
 */
@Composable
fun ProyectosScreen(
    onVolver: () -> Unit,
    puedeGestionar: Boolean,
    viewModel: ProyectosViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var creando by remember { mutableStateOf(false) }

    val detalle = estado.detalle
    if (detalle != null) {
        DetalleProyectoScreen(
            detalle = detalle,
            asignables = estado.asignables,
            puedeGestionar = puedeGestionar,
            error = estado.error,
            onVolver = viewModel::cerrarDetalle,
            onDescartarError = viewModel::descartarError,
            onAsignar = { usuarioId, desde ->
                viewModel.asignar(detalle.proyecto.id, usuarioId, desde)
            },
            onFinalizar = viewModel::finalizarAsignacion,
            onCambiarEstado = { activo -> viewModel.cambiarEstado(detalle.proyecto.id, activo) }
        )
        return
    }

    PantallaConBarra(
        titulo = stringResource(R.string.proyectos_titulo),
        onVolver = onVolver,
        accionFlotante = {
            if (puedeGestionar) {
                FloatingActionButton(onClick = { creando = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.proyectos_nuevo))
                }
            }
        }
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.proyectos.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            when {
                estado.error != null && estado.proyectos.isEmpty() -> BannerError(
                    mensaje = estado.error!!.resolver(),
                    onReintentar = viewModel::cargar
                )

                estado.proyectos.isEmpty() -> EstadoVacio(
                    titulo = stringResource(R.string.proyectos_vacio_titulo),
                    texto = stringResource(R.string.proyectos_vacio_texto)
                )

                else -> LazyColumn(
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
                    items(estado.proyectos, key = { it.id }) { proyecto ->
                        TarjetaProyecto(proyecto = proyecto, onAbrir = { viewModel.abrir(proyecto.id) })
                    }
                }
            }
        }
    }

    if (creando) {
        DialogoNuevoProyecto(
            onConfirma = { codigo, nombre, descripcion, desde ->
                viewModel.crear(codigo, nombre, descripcion, desde)
                creando = false
            },
            onCancela = { creando = false }
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

@Composable
private fun TarjetaProyecto(proyecto: ProyectoDTO, onAbrir: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAbrir),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = proyecto.codigo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Un proyecto cerrado se marca, no se esconde: sus horas
                // pasadas siguen contando y hay que poder consultarlas.
                if (!proyecto.activo) {
                    AssistChip(
                        onClick = onAbrir,
                        label = { Text(stringResource(R.string.proyectos_cerrado_etiqueta)) }
                    )
                }
            }
            Text(text = proyecto.nombre, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = rangoDeFechas(proyecto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pluralStringResource(
                    R.plurals.proyectos_asignados, proyecto.asignados.toInt(), proyecto.asignados.toInt()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "Desde el 01/01/2026" o "Del 01/01/2026 al 30/06/2026". */
@Composable
internal fun rangoDeFechas(proyecto: ProyectoDTO): String =
    if (proyecto.fechaFin == null) {
        stringResource(R.string.proyectos_desde, DateFormats.fechaCorta(proyecto.fechaInicio))
    } else {
        stringResource(
            R.string.proyectos_rango,
            DateFormats.fechaCorta(proyecto.fechaInicio),
            DateFormats.fechaCorta(proyecto.fechaFin)
        )
    }
