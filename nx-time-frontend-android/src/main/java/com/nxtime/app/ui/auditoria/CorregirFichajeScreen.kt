package com.nxtime.app.ui.auditoria

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
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
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BotonPrincipal
import com.nxtime.app.ui.components.ColumnaFormulario
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import java.util.Locale

/**
 * Corregir un fichaje ya cerrado (RRHH/ADMIN).
 *
 * Solo se editan las dos horas y el motivo: la fecha viene fija del
 * fichaje original. Corregir una jornada para moverla a otro día no es
 * una corrección, es inventarse un registro, y el backend tampoco lo
 * admitiría.
 */
@Composable
fun CorregirFichajeScreen(
    nombreEmpleado: String,
    entradaIso: String?,
    salidaIso: String?,
    onCorregido: () -> Unit,
    onVolver: () -> Unit,
    viewModel: CorregirFichajeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(entradaIso, salidaIso) {
        viewModel.precargar(entradaIso, salidaIso)
    }

    LaunchedEffect(estado.corregido) {
        if (estado.corregido) onCorregido()
    }

    var eligiendo by remember { mutableStateOf<Campo?>(null) }

    PantallaConBarra(
        titulo = stringResource(R.string.correccion_titulo),
        onVolver = onVolver
    ) { modifier ->
        ColumnaFormulario(modifier) {
            estado.error?.let { mensaje ->
                BannerError(
                    mensaje = mensaje.resolver(),
                    onReintentar = viewModel::descartarError
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = stringResource(R.string.correccion_empleado, nombreEmpleado),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = DateFormats.fechaCorta(estado.fecha),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            SelectorDeHora(
                etiqueta = stringResource(R.string.correccion_entrada),
                hora = estado.horaEntrada,
                minuto = estado.minutoEntrada,
                onPulsa = { eligiendo = Campo.ENTRADA }
            )
            SelectorDeHora(
                etiqueta = stringResource(R.string.correccion_salida),
                hora = estado.horaSalida,
                minuto = estado.minutoSalida,
                onPulsa = { eligiendo = Campo.SALIDA }
            )

            OutlinedTextField(
                value = estado.motivo,
                onValueChange = viewModel::cambiarMotivo,
                label = { Text(stringResource(R.string.correccion_motivo)) },
                supportingText = { Text(stringResource(R.string.correccion_motivo_ayuda)) },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            BotonPrincipal(
                texto = stringResource(R.string.correccion_guardar),
                onClick = viewModel::guardar,
                cargando = estado.enviando
            )
        }
    }

    eligiendo?.let { campo ->
        val (hora, minuto) = when (campo) {
            Campo.ENTRADA -> estado.horaEntrada to estado.minutoEntrada
            Campo.SALIDA -> estado.horaSalida to estado.minutoSalida
        }
        DialogoDeHora(
            horaInicial = hora,
            minutoInicial = minuto,
            onConfirma = { h, m ->
                when (campo) {
                    Campo.ENTRADA -> viewModel.cambiarEntrada(h, m)
                    Campo.SALIDA -> viewModel.cambiarSalida(h, m)
                }
                eligiendo = null
            },
            onCancela = { eligiendo = null }
        )
    }
}

private enum class Campo { ENTRADA, SALIDA }

@Composable
private fun SelectorDeHora(
    etiqueta: String,
    hora: Int,
    minuto: Int,
    onPulsa: () -> Unit
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoDeHora(
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
