package com.nxtime.app.ui.historial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.R
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.Avatar
import com.nxtime.app.ui.components.CampanaDeAvisos
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.horaDeSalida
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Composable
fun HistorialScreen(
    onPedirCorreccion: (Registro) -> Unit,
    onAnadirPausa: (Registro) -> Unit,
    onRepartir: (Registro) -> Unit,
    contadorAvisos: Int,
    onIrAvisos: () -> Unit,
    iniciales: String,
    onIrPerfil: () -> Unit,
    viewModel: HistorialViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    /*
     * Recarga al volver a la pantalla, no solo al entrar: tras añadir una
     * pausa desde aquí, la jornada ha cambiado en el servidor y esta pantalla
     * seguiría enseñando la de antes. `ON_RESUME` y no `LaunchedEffect(Unit)`
     * por lo mismo que en HistorialEquipoScreen: este último solo se dispara
     * la primera vez.
     */
    val propietario = LocalLifecycleOwner.current
    DisposableEffect(propietario) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) viewModel.cargar()
        }
        propietario.lifecycle.addObserver(observador)
        onDispose { propietario.lifecycle.removeObserver(observador) }
    }

    // Sin flecha de volver: es un destino de la barra de navegación.
    PantallaConBarra(
        titulo = stringResource(R.string.historial_titulo),
        acciones = {
            CampanaDeAvisos(contadorAvisos, onIrAvisos)
            Avatar(
                iniciales = iniciales,
                descripcion = stringResource(R.string.perfil_abrir),
                onClick = onIrPerfil,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    ) { modifier ->
        // Los filtros van FUERA de la lista: dentro, desaparecerían cada vez
        // que se cambia de periodo y la lista vuelve a enseñar el esqueleto.
        Column(modifier = modifier) {
            SelectorDePeriodo(
                periodo = estado.periodo,
                rango = estado.rango,
                segundosNetos = estado.segundosNetos,
                mostrarTotal = !estado.cargando && estado.error == null,
                onCambiar = viewModel::cambiarPeriodo
            )
            ListaConRecarga(
                cargando = estado.cargando,
                hayContenido = estado.registros.isNotEmpty(),
                onRecargar = viewModel::cargar,
                modifier = Modifier.weight(1f)
            ) {
                when {
                    estado.error != null -> EstadoErrorPantalla(
                        mensaje = estado.error!!.resolver(),
                        onReintentar = viewModel::cargar
                    )

                    // Un periodo sin fichajes no es "aún no has fichado nunca".
                    estado.registros.isEmpty() && estado.periodo != PeriodoHistorial.Recientes -> EstadoVacio(
                        titulo = stringResource(R.string.historial_periodo_vacio_titulo),
                        texto = stringResource(R.string.historial_periodo_vacio_texto)
                    )

                    estado.registros.isEmpty() -> EstadoVacio(
                        titulo = stringResource(R.string.historial_vacio_titulo),
                        texto = stringResource(R.string.historial_vacio_texto)
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(estado.registros, key = { it.id }) { registro ->
                            TarjetaJornada(
                                registro,
                                onPedirCorreccion = { onPedirCorreccion(registro) },
                                onAnadirPausa = { onAnadirPausa(registro) },
                                onRepartir = { onRepartir(registro) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chips de periodo y, con un periodo elegido, las fechas y el total neto.
 *
 * "Recientes" es el de siempre (los últimos 200) y no lleva total: sumar
 * 200 jornadas sueltas no responde a ninguna pregunta que alguien se haga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorDePeriodo(
    periodo: PeriodoHistorial,
    rango: Pair<LocalDate, LocalDate>?,
    segundosNetos: Long,
    mostrarTotal: Boolean,
    onCambiar: (PeriodoHistorial) -> Unit
) {
    var eligiendoFechas by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(
                PeriodoHistorial.Recientes to R.string.historial_periodo_recientes,
                PeriodoHistorial.EstaSemana to R.string.historial_periodo_semana,
                PeriodoHistorial.EsteMes to R.string.historial_periodo_mes,
                PeriodoHistorial.MesAnterior to R.string.historial_periodo_mes_anterior
            ).forEach { (opcion, texto) ->
                FilterChip(
                    selected = periodo == opcion,
                    onClick = { onCambiar(opcion) },
                    label = { Text(stringResource(texto), maxLines = 1) }
                )
            }
            FilterChip(
                selected = periodo is PeriodoHistorial.Elegido,
                onClick = { eligiendoFechas = true },
                label = { Text(stringResource(R.string.historial_periodo_elegir), maxLines = 1) }
            )
        }
        if (rango != null && mostrarTotal) {
            Text(
                text = stringResource(
                    R.string.historial_periodo_total,
                    DateFormats.fechaCorta(rango.first),
                    DateFormats.fechaCorta(rango.second),
                    DateFormats.minutos(segundosNetos / 60)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (eligiendoFechas) {
        DialogoDeFechas(
            inicial = (periodo as? PeriodoHistorial.Elegido)?.let { it.desde to it.hasta },
            onElegir = { desde, hasta ->
                eligiendoFechas = false
                onCambiar(PeriodoHistorial.Elegido(desde, hasta))
            },
            onCancelar = { eligiendoFechas = false }
        )
    }
}

/**
 * Rango de fechas con el `DateRangePicker` de Material 3.
 *
 * Trabaja en milisegundos UTC, así que se convierte por UTC en los dos
 * sentidos (ver `CampoFecha`): hacerlo con la zona del móvil mueve el día
 * elegido a un lado u otro de la medianoche.
 *
 * El tope de un año es del servidor; aquí solo se avisa antes de pedirlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoDeFechas(
    inicial: Pair<LocalDate, LocalDate>?,
    onElegir: (LocalDate, LocalDate) -> Unit,
    onCancelar: () -> Unit
) {
    val estado = rememberDateRangePickerState(
        initialSelectedStartDateMillis = inicial?.first?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = inicial?.second?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    val desde = estado.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val hasta = estado.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val demasiadoLargo = desde != null && hasta != null && ChronoUnit.DAYS.between(desde, hasta) + 1 > 366

    DatePickerDialog(
        onDismissRequest = onCancelar,
        confirmButton = {
            TextButton(
                enabled = desde != null && hasta != null && !demasiadoLargo,
                onClick = { if (desde != null && hasta != null) onElegir(desde, hasta) }
            ) { Text(stringResource(R.string.aceptar)) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) }
        }
    ) {
        Column {
            DateRangePicker(
                state = estado,
                showModeToggle = false,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (demasiadoLargo) {
                Text(
                    text = stringResource(R.string.historial_periodo_max_un_anio),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

/**
 * Una jornada del historial.
 *
 * El total es el tiempo **neto**: la duración entre entrada y salida
 * menos las pausas acumuladas. Es la misma cuenta que hacía la lista
 * anterior, y la que corresponde a lo que se factura como trabajado.
 */
@Composable
private fun TarjetaJornada(
    registro: Registro,
    onPedirCorreccion: () -> Unit,
    onAnadirPausa: () -> Unit,
    onRepartir: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = DateFormats.fechaLarga(registro.horaEntrada),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Dato(
                    etiqueta = stringResource(R.string.historial_entrada),
                    valor = DateFormats.hora(registro.horaEntrada),
                    modifier = Modifier.weight(1f)
                )
                Dato(
                    etiqueta = stringResource(R.string.historial_salida),
                    // Dice "(+1 d)" si la jornada cruzó la medianoche:
                    // ver `horaDeSalida`.
                    valor = horaDeSalida(registro.horaEntrada, registro.horaSalida),
                    modifier = Modifier.weight(1f)
                )
                Dato(
                    etiqueta = stringResource(R.string.historial_total),
                    valor = DateFormats.duracionNeta(
                        registro.horaEntrada,
                        registro.horaSalida,
                        registro.segundosPausaAcumulados
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Solo se enseña la pausa si la hubo: una línea "Pausa: 0h 00m"
            // en cada jornada solo añade ruido.
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
             * Pedir que corrijan TU propio fichaje (Fase E).
             *
             * Hasta ahora un empleado no podia hacerlo: solo podia
             * esperar a que alguien de RRHH lo corrigiera por su cuenta.
             *
             * Solo sobre jornadas ya cerradas: una activa se cierra
             * fichando, no corrigiendo, y el backend la rechaza.
             */
            if (registro.horaSalida != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    // Una pausa olvidada no es "corregir las horas": tiene su
                    // propio botón, y el servidor decide si se aplica o se
                    // pide según el día (ADR 015).
                    // Repartir por proyecto no es corregir horas: mover horas
                    // entre proyectos no cambia cuánto se trabajó (ADR 017).
                    TextButton(onClick = onRepartir) {
                        Text(stringResource(R.string.reparto_boton))
                    }
                    TextButton(onClick = onAnadirPausa) {
                        Text(stringResource(R.string.pausa_boton))
                    }
                    TextButton(onClick = onPedirCorreccion) {
                        Text(stringResource(R.string.correcciones_pedir))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
