package com.nxtime.app.ui.auditoria

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.AccionAuditoria
import com.nxtime.app.data.dto.AuditoriaFichajeDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.EstadoCargando
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * La traza de auditoría de un fichaje.
 *
 * Se pinta como una línea temporal vertical y no como una lista suelta
 * porque lo que importa aquí es el ORDEN: qué pasó, quién lo hizo y en
 * qué momento. Es el registro que se enseña en una inspección.
 */
@Composable
fun AuditoriaScreen(
    onVolver: () -> Unit,
    viewModel: AuditoriaViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(
        titulo = stringResource(R.string.auditoria_titulo),
        onVolver = onVolver
    ) { modifier ->
        when {
            estado.cargando -> EstadoCargando(modifier)

            estado.error != null -> EstadoErrorPantalla(
                mensaje = estado.error!!.resolver(),
                onReintentar = viewModel::cargar,
                modifier = modifier
            )

            estado.entradas.isEmpty() -> EstadoVacio(
                titulo = stringResource(R.string.auditoria_vacio_titulo),
                texto = stringResource(R.string.auditoria_vacio_texto),
                modifier = modifier
            )

            else -> LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(estado.entradas, key = { _, e -> e.id }) { indice, entrada ->
                    PasoDeAuditoria(
                        entrada = entrada,
                        esUltimo = indice == estado.entradas.lastIndex
                    )
                }
            }
        }
    }
}

/**
 * Un paso de la línea temporal: el punto y la línea a la izquierda, la
 * tarjeta con el cambio a la derecha.
 */
@Composable
private fun PasoDeAuditoria(entrada: AuditoriaFichajeDTO, esUltimo: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = colorDeAccion(entrada.accion),
                modifier = Modifier.size(12.dp)
            ) {}
            if (!esUltimo) {
                // El hilo que une un paso con el siguiente. El último no
                // lo lleva: una línea que muere en el aire sugiere que
                // falta algo por cargar.
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(96.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth().height(96.dp)
                    ) {}
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = textoDeAccion(entrada.accion),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = DateFormats.fechaYHora(entrada.fechaHora),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                CambiosDeHora(entrada)

                entrada.motivo?.takeIf { it.isNotBlank() }?.let { motivo ->
                    Spacer(Modifier.height(4.dp))
                    Text(text = motivo, style = MaterialTheme.typography.bodyMedium)
                }

                entrada.modificadoPor?.nombre?.let { quien ->
                    Text(
                        text = stringResource(R.string.auditoria_por, quien),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // La IP se enseña porque forma parte de la traza que exige
                // el RD-ley 8/2019: identifica desde dónde se tocó el
                // registro, no solo quién.
                entrada.ip?.takeIf { it.isNotBlank() }?.let { ip ->
                    Text(
                        text = stringResource(R.string.auditoria_desde_ip, ip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Qué cambió, en horas legibles.
 *
 * Solo se pinta la línea cuando el valor de verdad cambió: repetir
 * "Entrada: 09:00 h → 09:00 h" en cada paso llenaría la pantalla de
 * ruido y escondería el cambio que sí importa.
 */
@Composable
private fun CambiosDeHora(entrada: AuditoriaFichajeDTO) {
    val antes = leerInstantanea(entrada.valorAnterior)
    val despues = leerInstantanea(entrada.valorNuevo) ?: return

    if (antes?.horaEntrada != despues.horaEntrada) {
        LineaDeCambio(
            anterior = antes?.horaEntrada,
            nuevo = despues.horaEntrada,
            cambio = R.string.auditoria_entrada_cambio,
            fijado = R.string.auditoria_entrada_fijada
        )
    }
    if (antes?.horaSalida != despues.horaSalida) {
        LineaDeCambio(
            anterior = antes?.horaSalida,
            nuevo = despues.horaSalida,
            cambio = R.string.auditoria_salida_cambio,
            fijado = R.string.auditoria_salida_fijada
        )
    }
}

/**
 * "Salida: 17:27 h → 18:27 h" cuando había un valor antes; solo
 * "Salida: 22:52 h" cuando se fija por primera vez.
 *
 * La distinción importa: al cerrar una jornada que estaba abierta, la
 * instantánea anterior tiene `horaSalida` a null, y pintar la flecha
 * daba **"Salida: -- → 22:52 h"**, que obliga a descifrar qué significa
 * ese "--" en un registro de cumplimiento normativo.
 *
 * Si el valor nuevo tampoco existe no se pinta nada: una línea que dijera
 * "Salida: --" no informa de nada.
 */
@Composable
private fun LineaDeCambio(
    anterior: String?,
    nuevo: String?,
    @StringRes cambio: Int,
    @StringRes fijado: Int
) {
    if (nuevo == null) return
    Text(
        text = if (anterior == null) {
            stringResource(fijado, DateFormats.hora(nuevo))
        } else {
            stringResource(cambio, DateFormats.hora(anterior), DateFormats.hora(nuevo))
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun textoDeAccion(accion: String): String =
    when (AccionAuditoria.de(accion)) {
        AccionAuditoria.CREACION -> stringResource(R.string.auditoria_accion_creacion)
        AccionAuditoria.MODIFICACION -> stringResource(R.string.auditoria_accion_modificacion)
        AccionAuditoria.CORRECCION -> stringResource(R.string.auditoria_accion_correccion)
        AccionAuditoria.ANULACION -> stringResource(R.string.auditoria_accion_anulacion)
        // Una acción que esta versión no conoce se enseña en crudo: en un
        // registro de cumplimiento, callar una línea es peor que pintarla
        // fea.
        null -> accion
    }

@Composable
private fun colorDeAccion(accion: String) = when (AccionAuditoria.de(accion)) {
    AccionAuditoria.CORRECCION, AccionAuditoria.ANULACION -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}
