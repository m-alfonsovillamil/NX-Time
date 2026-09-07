package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import com.nxtime.app.ui.util.DateFormats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Campo de fecha que abre el calendario de Material 3.
 *
 * El `DatePicker` trabaja en milisegundos UTC, así que la conversión se
 * hace por UTC en los dos sentidos: interpretar esos milisegundos en la
 * zona local movería la fecha un día para quien esté al oeste de
 * Greenwich. Aquí no se está fijando un instante, sino un día de
 * calendario.
 *
 * <b>Vivía dentro de `SolicitudScreen` como función privada</b> y se
 * movió aquí en la Fase D, cuando los diálogos de proyectos necesitaron
 * el mismo campo tres veces. La conversión por UTC es justo el detalle
 * que se copia mal si se duplica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(
    etiqueta: String,
    fecha: LocalDate?,
    onElige: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
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
