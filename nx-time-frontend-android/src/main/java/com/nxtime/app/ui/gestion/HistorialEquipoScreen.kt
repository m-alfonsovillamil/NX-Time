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
import androidx.compose.material3.MenuAnchorType
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
import com.nxtime.app.ui.components.EstadoCargando
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

@Composable
fun HistorialEquipoScreen(
    onVolver: () -> Unit,
    viewModel: HistorialEquipoViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(
        titulo = stringResource(R.string.equipo_titulo),
        onVolver = onVolver
    ) { modifier ->
        when {
            estado.cargando -> EstadoCargando(modifier)

            estado.error != null -> EstadoErrorPantalla(
                mensaje = estado.error!!.resolver(),
                onReintentar = viewModel::cargar,
                modifier = modifier
            )

            else -> Column(modifier = modifier.fillMaxSize()) {

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
                            TarjetaJornadaEquipo(registro)
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
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
private fun TarjetaJornadaEquipo(registro: RegistroEquipoDTO) {
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
                        registro.minutosPausaAcumulados
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
