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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.RegistroEquipoDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.ListaConRecarga
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * @param puedeCorregir si se ofrece corregir un fichaje. Lo decide
 *   `Permisos` a partir del rol: `fichaje:corregir` la tienen RRHH y
 *   ADMIN, no un GESTOR. Ofrecérselo a quien no la tiene sería repetir el
 *   defecto del botón "Crear gestor", que siempre acababa en 403.
 * @param puedeAuditar ídem con `fichaje:auditoria`.
 */
@Composable
fun HistorialEquipoScreen(
    onVolver: () -> Unit,
    puedeCorregir: Boolean = false,
    puedeAuditar: Boolean = false,
    onCorregir: (RegistroEquipoDTO) -> Unit = {},
    onVerAuditoria: (RegistroEquipoDTO) -> Unit = {},
    viewModel: HistorialEquipoViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    /*
     * Recargar al volver a la pantalla, no solo al crearla.
     *
     * Sin esto, tras corregir un fichaje se volvía aquí y la lista
     * seguía enseñando el registro ANTIGUO -- el que la corrección
     * acababa de anular. Y no era solo cosmético: pulsar "Ver auditoría"
     * en esa tarjeta preguntaba por un id que el historial ya no debería
     * contener.
     *
     * `ON_RESUME` y no `LaunchedEffect(Unit)`: este último solo se
     * dispara al entrar por primera vez, que es justo el caso que ya
     * cubría el `init` del ViewModel.
     */
    val propietario = LocalLifecycleOwner.current
    DisposableEffect(propietario) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) viewModel.cargar()
        }
        propietario.lifecycle.addObserver(observador)
        onDispose { propietario.lifecycle.removeObserver(observador) }
    }

    PantallaConBarra(
        titulo = stringResource(R.string.equipo_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.registrosVisibles.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
        when {
            estado.error != null -> EstadoErrorPantalla(
                mensaje = estado.error!!.resolver(),
                onReintentar = viewModel::cargar
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {

                if (estado.empleados.isNotEmpty()) {
                    FiltroEmpleado(
                        empleados = estado.empleados,
                        seleccionado = estado.empleadoFiltrado,
                        onSelecciona = viewModel::onFiltroCambia
                    )
                }

                val visibles = estado.registrosVisibles
                when {
                    // Se distingue "el equipo no ha fichado nunca" de
                    // "este empleado no ha fichado": con un solo texto,
                    // el gestor no sabría si el filtro está haciendo algo.
                    visibles.isEmpty() && estado.empleadoFiltrado != null -> EstadoVacio(
                        titulo = stringResource(
                            R.string.equipo_vacio_filtro_titulo,
                            estado.empleadoFiltrado!!.nombre
                        ),
                        texto = stringResource(R.string.equipo_vacio_filtro_texto)
                    )

                    visibles.isEmpty() -> EstadoVacio(
                        titulo = stringResource(R.string.equipo_vacio_titulo),
                        texto = stringResource(R.string.equipo_vacio_texto)
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibles, key = { it.id }) { registro ->
                            TarjetaJornadaEquipo(
                                registro = registro,
                                puedeCorregir = puedeCorregir,
                                puedeAuditar = puedeAuditar,
                                onCorregir = { onCorregir(registro) },
                                onVerAuditoria = { onVerAuditoria(registro) }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltroEmpleado(
    empleados: List<EmpleadoSimpleDTO>,
    seleccionado: EmpleadoSimpleDTO?,
    onSelecciona: (EmpleadoSimpleDTO?) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    val textoTodos = stringResource(R.string.equipo_filtro_todos)

    ExposedDropdownMenuBox(
        expanded = abierto,
        onExpandedChange = { abierto = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = seleccionado?.nombre ?: textoTodos,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.equipo_filtro)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text(textoTodos) },
                onClick = { onSelecciona(null); abierto = false }
            )
            empleados.forEach { empleado ->
                DropdownMenuItem(
                    text = { Text(empleado.nombre) },
                    onClick = { onSelecciona(empleado); abierto = false }
                )
            }
        }
    }
}

@Composable
private fun TarjetaJornadaEquipo(
    registro: RegistroEquipoDTO,
    puedeCorregir: Boolean,
    puedeAuditar: Boolean,
    onCorregir: () -> Unit,
    onVerAuditoria: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = registro.usuario.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = DateFormats.fechaCorta(registro.fecha),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DatoEquipo(
                    etiqueta = stringResource(R.string.historial_entrada),
                    valor = DateFormats.hora(registro.horaEntrada),
                    modifier = Modifier.weight(1f)
                )
                DatoEquipo(
                    etiqueta = stringResource(R.string.historial_salida),
                    valor = if (registro.horaSalida == null) {
                        stringResource(R.string.historial_en_curso)
                    } else {
                        DateFormats.hora(registro.horaSalida)
                    },
                    modifier = Modifier.weight(1f)
                )
                DatoEquipo(
                    etiqueta = stringResource(R.string.historial_total),
                    valor = DateFormats.duracionNeta(
                        registro.horaEntrada,
                        registro.horaSalida,
                        registro.segundosPausaAcumulados
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (registro.minutosPausaAcumulados > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.historial_pausa,
                        DateFormats.minutos(registro.minutosPausaAcumulados)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            /*
             * Acciones de cumplimiento normativo, solo para quien tiene
             * la authority. Corregir además solo tiene sentido sobre una
             * jornada CERRADA: el backend responde 409 sobre una abierta,
             * así que el botón ni se ofrece.
             */
            val jornadaCerrada = registro.horaSalida != null
            if ((puedeCorregir && jornadaCerrada) || puedeAuditar) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (puedeAuditar) {
                        TextButton(onClick = onVerAuditoria) {
                            Text(stringResource(R.string.auditoria_accion))
                        }
                    }
                    if (puedeCorregir && jornadaCerrada) {
                        TextButton(onClick = onCorregir) {
                            Text(stringResource(R.string.correccion_accion))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatoEquipo(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}
