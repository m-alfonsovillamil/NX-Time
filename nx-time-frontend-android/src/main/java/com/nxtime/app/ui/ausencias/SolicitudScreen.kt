package com.nxtime.app.ui.ausencias

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BotonPrincipal
import com.nxtime.app.ui.components.ColumnaFormulario
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.etiqueta
import com.nxtime.app.ui.util.resolver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun SolicitudScreen(
    onEnviada: () -> Unit,
    onVolver: () -> Unit,
    viewModel: SolicitudViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(estado.enviada) {
        if (estado.enviada) onEnviada()
    }

    PantallaConBarra(
        titulo = stringResource(R.string.solicitud_titulo),
        onVolver = onVolver
    ) { modifier ->
        ColumnaFormulario(modifier = modifier) {

            estado.error?.let { BannerError(mensaje = it.resolver()) }

            SelectorTipo(
                seleccionado = estado.tipo,
                onSelecciona = viewModel::onTipoCambia
            )

            CampoFecha(
                etiqueta = stringResource(R.string.solicitud_desde),
                fecha = estado.fechaInicio,
                onElige = viewModel::onFechaInicioCambia
            )
            CampoFecha(
                etiqueta = stringResource(R.string.solicitud_hasta),
                fecha = estado.fechaFin,
                onElige = viewModel::onFechaFinCambia
            )

            OutlinedTextField(
                value = estado.motivo,
                onValueChange = viewModel::onMotivoCambia,
                label = { Text(stringResource(R.string.solicitud_motivo)) },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            BotonPrincipal(
                texto = stringResource(R.string.solicitud_enviar),
                onClick = viewModel::enviar,
                cargando = estado.cargando
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Elige el tipo de ausencia.
 *
 * Sustituye al `AutoCompleteTextView` con `ArrayAdapter` que mostraba
 * los nombres del enum en crudo ("FALLECIMIENTO_FAMILIAR"). El valor
 * que viaja al backend sigue siendo el enum; lo que cambia es lo que
 * lee la persona.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorTipo(
    seleccionado: TipoAusencia,
    onSelecciona: (TipoAusencia) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = abierto,
        onExpandedChange = { abierto = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        OutlinedTextField(
            value = stringResource(seleccionado.etiqueta),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.solicitud_tipo)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            TipoAusencia.entries.forEach { tipo ->
                DropdownMenuItem(
                    text = { Text(stringResource(tipo.etiqueta)) },
                    onClick = {
                        onSelecciona(tipo)
                        abierto = false
                    }
                )
            }
        }
    }
}

/**
 * Campo de fecha que abre el calendario de Material 3.
 *
 * El `DatePicker` trabaja en milisegundos UTC, así que la conversión se
 * hace por UTC en los dos sentidos: interpretar esos milisegundos en la
 * zona local movería la fecha un día para quien esté al oeste de
 * Greenwich. Aquí no se está fijando un instante, sino un día de
 * calendario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoFecha(
    etiqueta: String,
    fecha: LocalDate?,
    onElige: (LocalDate) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = fecha?.let { DateFormats.fechaCorta(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(etiqueta) },
        trailingIcon = {
            // El icono, y no el campo, es lo que abre el calendario:
            // un OutlinedTextField de solo lectura no recibe clics de
            // forma fiable en todas las versiones de Compose.
            TextButton(onClick = { abierto = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = etiqueta)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )

    if (abierto) {
        val estadoCalendario = rememberDatePickerState(
            initialSelectedDateMillis = fecha
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        estadoCalendario.selectedDateMillis?.let { millis ->
                            onElige(
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                            )
                        }
                        abierto = false
                    }
                ) { Text(stringResource(R.string.guardar)) }
            },
            dismissButton = {
                TextButton(onClick = { abierto = false }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        ) {
            DatePicker(state = estadoCalendario)
        }
    }
}
