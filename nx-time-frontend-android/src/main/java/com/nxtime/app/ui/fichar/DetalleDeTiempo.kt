package com.nxtime.app.ui.fichar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import com.nxtime.app.data.dto.HorasDelDiaDTO
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.dto.SaldoVacacionesDTO
import com.nxtime.app.ui.components.BarraDeHoras
import com.nxtime.app.ui.components.GraficoDeBarras
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.etiqueta
import com.nxtime.app.ui.util.resolver
import java.time.LocalDate

/**
 * La hoja que se abre al tocar una tarjeta de la pantalla de inicio.
 *
 * Cada tarjeta responde a la pregunta que lleva implícita: "hoy" → ¿cuánto
 * me queda?; "semana" y "mes" → ¿cómo se reparte y voy bien?; "vacaciones" →
 * ¿cuándo me toca la siguiente?
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleDeTiempoHoja(
    estado: DetalleDeTiempoUiState,
    ficha: FicharUiState,
    hoy: LocalDate,
    onCerrar: () -> Unit
) {
    val tipo = estado.tipo ?: return
    ModalBottomSheet(onDismissRequest = onCerrar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(
                text = stringResource(
                    when (tipo) {
                        TipoDetalle.HOY -> R.string.detalle_hoy_titulo
                        TipoDetalle.SEMANA -> R.string.detalle_semana_titulo
                        TipoDetalle.MES -> R.string.detalle_mes_titulo
                        TipoDetalle.VACACIONES -> R.string.detalle_vacaciones_titulo
                    }
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            when {
                estado.cargando -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                estado.error != null -> Text(
                    text = estado.error.resolver(),
                    color = MaterialTheme.colorScheme.error
                )
                else -> {
                    val enCurso = ficha.segundosEnCurso / 60
                    val dias = DetalleDeTiempoViewModel.conJornadaEnCurso(estado.dias, hoy, enCurso)
                    when (tipo) {
                        TipoDetalle.HOY -> DetalleHoy(dias.firstOrNull(), ficha)
                        TipoDetalle.SEMANA -> DetalleDeDias(dias, porSemana = true)
                        TipoDetalle.MES -> DetalleDeDias(dias, porSemana = false)
                        TipoDetalle.VACACIONES -> DetalleVacaciones(ficha.resumen?.saldoVacaciones, estado.proximasAusencias)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetalleHoy(dia: HorasDelDiaDTO?, ficha: FicharUiState) {
    val registro = ficha.registro
    registro?.horaEntrada?.let {
        Linea(stringResource(R.string.detalle_hoy_entrada), DateFormats.hora(it))
    }
    Linea(stringResource(R.string.detalle_trabajado), DateFormats.minutos(ficha.minutosHoy))
    val pausa = (registro?.segundosPausaAcumulados ?: 0) / 60
    if (pausa > 0) {
        Linea(stringResource(R.string.detalle_hoy_pausas), DateFormats.minutos(pausa))
    }
    if (dia == null) return
    motivoNoLaborable(dia)?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        return
    }
    if (dia.minutosEsperados > 0) {
        Linea(stringResource(R.string.detalle_esperado), DateFormats.minutos(dia.minutosEsperados))
        val diferencia = dia.minutosEsperados - dia.minutosTrabajados
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (diferencia > 0) {
                stringResource(R.string.detalle_hoy_quedan, DateFormats.minutos(diferencia))
            } else {
                stringResource(R.string.detalle_hoy_hecho, DateFormats.minutos(-diferencia))
            },
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * Semana o mes: el gráfico, el total contra lo esperado y, debajo, los días
 * que no eran laborables (con su porqué). En la semana, además, cada día.
 */
@Composable
private fun DetalleDeDias(dias: List<HorasDelDiaDTO>, porSemana: Boolean) {
    if (dias.isEmpty()) return
    val barras = dias.mapIndexed { i, dia ->
        val fecha = LocalDate.parse(dia.fecha)
        BarraDeHoras(
            etiqueta = when {
                porSemana -> DateFormats.inicialDelDia(fecha.dayOfWeek)
                // En el mes, rotular todos los días es ilegible: el 1 y de cinco en cinco.
                i == 0 || fecha.dayOfMonth % 5 == 0 -> fecha.dayOfMonth.toString()
                else -> ""
            },
            minutos = dia.minutosTrabajados,
            esperados = dia.minutosEsperados,
            noLaborable = motivoDeDia(dia) != null,
            descripcion = "${DateFormats.fechaCorta(fecha)}: ${DateFormats.minutos(dia.minutosTrabajados)}"
        )
    }
    GraficoDeBarras(barras = barras, modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(
            R.string.detalle_total_de,
            DateFormats.minutos(dias.sumOf { it.minutosTrabajados }),
            DateFormats.minutos(dias.sumOf { it.minutosEsperados })
        ),
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        text = stringResource(R.string.detalle_leyenda),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val filas = if (porSemana) dias else dias.filter { motivoDeDia(it) != null }
    if (filas.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        filas.forEach { dia ->
            val fecha = LocalDate.parse(dia.fecha)
            val motivo = motivoNoLaborable(dia)
            Linea(
                etiqueta = DateFormats.fechaCorta(fecha) + (motivo?.let { " · $it" } ?: ""),
                valor = DateFormats.minutos(dia.minutosTrabajados)
            )
        }
    }
}

@Composable
private fun DetalleVacaciones(saldo: SaldoVacacionesDTO?, proximas: List<RespuestaAusencia>) {
    saldo?.let {
        Linea(stringResource(R.string.detalle_vacaciones_disponibles), it.diasDisponibles.toString())
        Linea(stringResource(R.string.detalle_vacaciones_consumidos), it.diasConsumidos.toString())
        // Solo si hay algo pendiente: una línea con un cero es ruido, y aquí
        // sirve para explicar por qué quedan menos días de los esperados.
        if (it.diasPendientes > 0) {
            Linea(stringResource(R.string.detalle_vacaciones_pendientes), it.diasPendientes.toString())
        }
        Linea(stringResource(R.string.detalle_vacaciones_totales, it.anio), it.diasTotales.toString())
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.detalle_vacaciones_proximas), style = MaterialTheme.typography.titleSmall)
    if (proximas.isEmpty()) {
        Text(
            text = stringResource(R.string.detalle_vacaciones_ninguna),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    proximas.forEach { ausencia ->
        Linea(
            etiqueta = stringResource(ausencia.tipo.etiqueta),
            valor = stringResource(
                R.string.ausencias_rango,
                DateFormats.fechaCorta(ausencia.fechaInicio),
                DateFormats.fechaCorta(ausencia.fechaFin)
            )
        )
    }
}

@Composable
private fun Linea(etiqueta: String, valor: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

/** El nombre del festivo o de la ausencia; null si el día era normal. */
private fun motivoDeDia(dia: HorasDelDiaDTO): String? = dia.festivo ?: dia.ausencia

@Composable
private fun motivoNoLaborable(dia: HorasDelDiaDTO): String? = when {
    dia.festivo != null -> stringResource(R.string.detalle_festivo, dia.festivo)
    dia.ausencia != null -> stringResource(R.string.detalle_ausencia, dia.ausencia)
    else -> null
}
