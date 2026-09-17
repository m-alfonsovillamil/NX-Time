package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import java.util.Locale

/*
 * El campo de hora y su diálogo.
 *
 * Vivían como privados en CorregirFichajeScreen. Salieron aquí cuando la
 * pantalla de añadir una pausa (ADR 015) necesitó exactamente lo mismo:
 * dos copias del mismo TimePicker acaban discrepando en algo —el formato de
 * 24 h, el icono— y nadie sabe cuál es la buena.
 */

/** Un campo de solo lectura que enseña "HH:mm" y abre el diálogo al pulsar el icono. */
@Composable
fun SelectorDeHora(
    etiqueta: String,
    hora: Int,
    minuto: Int,
    onPulsa: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = String.format(Locale.forLanguageTag("es-ES"), "%02d:%02d", hora, minuto),
        onValueChange = {},
        readOnly = true,
        label = { Text(etiqueta) },
        trailingIcon = {
            // El campo entero abre el diálogo, pero el icono lo anuncia:
            // un campo de solo lectura sin pista visual parece roto.
            TextButton(onClick = onPulsa) {
                Icon(Icons.Default.Schedule, contentDescription = etiqueta)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoDeHora(
    horaInicial: Int,
    minutoInicial: Int,
    onConfirma: (Int, Int) -> Unit,
    onCancela: () -> Unit
) {
    val estado = rememberTimePickerState(
        initialHour = horaInicial,
        initialMinute = minutoInicial,
        // Formato de 24 horas: es el que usa el resto de la app y el que
        // se espera en un registro horario español.
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onCancela,
        confirmButton = {
            TextButton(onClick = { onConfirma(estado.hour, estado.minute) }) {
                Text(stringResource(R.string.aceptar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancela) {
                Text(stringResource(R.string.cancelar))
            }
        },
        text = {
            Column { TimePicker(state = estado) }
        }
    )
}
