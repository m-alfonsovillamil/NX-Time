package com.nxtime.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.LaunchedEffect
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

    // Tocar el campo también abre el calendario, no solo el icono: en el
    // emulador (28/11/2026) tocar el texto solo lo enfocaba y no pasaba
    // nada, que es lo primero que hace cualquiera. Un campo de solo lectura
    // no recibe `clickable` de forma fiable, pero sí avisa de que lo pulsan
    // por su `interactionSource`. Se escucha el soltar, y no un estado
    // "pulsado": en un toque rápido ese estado va y vuelve antes de que la
    // pantalla llegue a leerlo.
    val interaccion = remember { MutableInteractionSource() }
    LaunchedEffect(interaccion) {
        interaccion.interactions.collect { if (it is PressInteraction.Release) abierto = true }
    }

    OutlinedTextField(
        value = fecha?.let { DateFormats.fechaCorta(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(etiqueta) },
        interactionSource = interaccion,
        trailingIcon = {
            // El icono sigue siendo un botón: es lo que encuentra TalkBack.
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
