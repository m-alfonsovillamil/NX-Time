package com.nxtime.app.ui.fichar

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.PausaAnadidaDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BotonPrincipal
import com.nxtime.app.ui.components.ColumnaFormulario
import com.nxtime.app.ui.components.DialogoDeHora
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.SelectorDeHora
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Añadir una pausa que no se fichó en su momento (ADR 015).
 *
 * Se llega desde dos sitios con el mismo botón: la pantalla de fichar, con
 * la jornada abierta, y el historial, con una ya cerrada. **Qué pasa al
 * guardar lo decide el servidor**, y la pantalla lo anuncia antes de enviar
 * y lo confirma después con lo que él respondió.
 */
@Composable
fun AnadirPausaScreen(
    entradaIso: String?,
    salidaIso: String?,
    onHecho: () -> Unit,
    onVolver: () -> Unit,
    viewModel: AnadirPausaViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(entradaIso, salidaIso) {
        viewModel.precargar(entradaIso, salidaIso)
    }

    val contexto = LocalContext.current
    LaunchedEffect(estado.hecho) {
        if (estado.hecho) {
            Toast.makeText(
                contexto,
                if (estado.aplicada) R.string.pausa_aplicada else R.string.pausa_pedida,
                Toast.LENGTH_LONG
            ).show()
            onHecho()
        }
    }

    var eligiendo by remember { mutableStateOf<CampoDePausa?>(null) }

    PantallaConBarra(
        titulo = stringResource(R.string.pausa_titulo),
        onVolver = onVolver
    ) { modifier ->
        ColumnaFormulario(modifier) {
            estado.error?.let { mensaje ->
                BannerError(mensaje = mensaje.resolver(), onReintentar = viewModel::descartarError)
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = DateFormats.fechaCorta(estado.fecha),
                style = MaterialTheme.typography.titleMedium
            )
            /*
             * Lo que va a pasar, dicho antes de pulsar. Una pausa sobre un día
             * pasado NO cambia nada hasta que alguien la apruebe, y descubrirlo
             * después, viendo que el historial sigue igual, parece un fallo.
             */
            Text(
                text = stringResource(
                    if (estado.vaDirecta) R.string.pausa_explicacion_directa
                    else R.string.pausa_explicacion_aprobacion
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            if (estado.pausas.isNotEmpty()) {
                PausasYaAnadidas(
                    pausas = estado.pausas,
                    puedeDeshacer = estado.jornadaAbierta,
                    onDeshacer = viewModel::deshacer
                )
            }

            SelectorDeHora(
                etiqueta = stringResource(R.string.pausa_inicio),
                hora = estado.horaInicio,
                minuto = estado.minutoInicio,
                onPulsa = { eligiendo = CampoDePausa.INICIO }
            )
            SelectorDeHora(
                etiqueta = stringResource(R.string.pausa_fin),
                hora = estado.horaFin,
                minuto = estado.minutoFin,
                onPulsa = { eligiendo = CampoDePausa.FIN }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.pausa_fin_otro_dia),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = estado.finEsOtroDia, onCheckedChange = viewModel::cambiarFinEsOtroDia)
            }

            OutlinedTextField(
                value = estado.motivo,
                onValueChange = viewModel::cambiarMotivo,
                label = { Text(stringResource(R.string.pausa_motivo)) },
                supportingText = { Text(stringResource(R.string.pausa_motivo_ayuda)) },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            BotonPrincipal(
                texto = stringResource(
                    if (estado.vaDirecta) R.string.pausa_guardar_directa
                    else R.string.pausa_guardar_aprobacion
                ),
                onClick = viewModel::guardar,
                cargando = estado.enviando
            )
        }
    }

    eligiendo?.let { campo ->
        val (hora, minuto) = when (campo) {
            CampoDePausa.INICIO -> estado.horaInicio to estado.minutoInicio
            CampoDePausa.FIN -> estado.horaFin to estado.minutoFin
        }
        DialogoDeHora(
            horaInicial = hora,
            minutoInicial = minuto,
            onConfirma = { h, m ->
                when (campo) {
                    CampoDePausa.INICIO -> viewModel.cambiarInicio(h, m)
                    CampoDePausa.FIN -> viewModel.cambiarFin(h, m)
                }
                eligiendo = null
            },
            onCancela = { eligiendo = null }
        )
    }
}

private enum class CampoDePausa { INICIO, FIN }

/**
 * Las pausas que ya se añadieron a esta jornada, bien a la vista: es la única
 * defensa contra añadir dos veces la misma comida.
 */
@Composable
private fun PausasYaAnadidas(
    pausas: List<PausaAnadidaDTO>,
    puedeDeshacer: Boolean,
    onDeshacer: (Long) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.pausa_ya_anadidas),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        pausas.forEach { pausa ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.pausa_rango,
                            DateFormats.hora(pausa.inicio),
                            DateFormats.hora(pausa.fin),
                            DateFormats.minutos(pausa.minutos)
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = pausa.motivo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (puedeDeshacer) {
                    TextButton(onClick = { onDeshacer(pausa.id) }) {
                        Text(stringResource(R.string.pausa_deshacer))
                    }
                }
            }
        }
    }
}
