package com.nxtime.app.ui.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.AusenciaCalendarioDTO
import com.nxtime.app.data.dto.FestivoDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.Avatar
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.CampanaDeAvisos
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * El calendario laboral del mes (Fase C).
 *
 * La rejilla se dibuja a mano con `Row`s y no con `LazyVerticalGrid`: son
 * 42 celdas como mucho, así que no hay nada que reciclar, y una rejilla
 * perezosa dentro de una columna desplazable pide una altura que aquí no
 * existe. Tampoco entra una librería de calendario -- pintar seis filas
 * de siete cajas no compensa una dependencia más.
 *
 * Cada celda enseña **como mucho dos marcas**: un punto si el día es
 * festivo y una franja si hay alguien ausente. Meter un color por ámbito
 * de festivo y otro por tipo de ausencia daría seis colores en una caja
 * de 40dp donde no se distinguen, y encima ninguno sería legible para
 * quien no distinga bien el color. El detalle va escrito debajo, al tocar
 * el día.
 */
@Composable
fun CalendarioScreen(
    contadorAvisos: Int,
    onIrAvisos: () -> Unit,
    iniciales: String,
    onIrPerfil: () -> Unit,
    puedeGestionar: Boolean,
    puedeVerEquipo: Boolean,
    viewModel: CalendarioViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var anadiendoFestivoEn by remember { mutableStateOf<LocalDate?>(null) }

    // Sin flecha de volver: es un destino de la barra de navegación.
    PantallaConBarra(
        titulo = stringResource(R.string.calendario_titulo),
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
        ListaConRecarga(
            cargando = estado.cargando,
            // La rejilla siempre tiene contenido (los días del mes), así
            // que nunca se enseña el esqueleto: lo que corresponde aquí
            // es el indicador de tirar para recargar.
            hayContenido = true,
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CabeceraDeMes(
                        periodo = estado.periodo,
                        onAnterior = viewModel::mesAnterior,
                        onSiguiente = viewModel::mesSiguiente,
                        onHoy = viewModel::irAHoy
                    )
                }

                estado.error?.let { mensaje ->
                    item {
                        BannerError(mensaje = mensaje.resolver(), onReintentar = viewModel::cargar)
                    }
                }

                item {
                    // El color va explícito, como en el resto de tarjetas
                    // de la app: `Card` sin `containerColor` usa
                    // `surfaceContainerLow`, que este tema NO define, y
                    // Material lo rellena con un tono derivado de su
                    // paleta base -- la rejilla salía sobre un rosa que
                    // no está en ninguna parte de la línea visual.
                    Card(
                        elevation = elevacionDeTarjeta(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            FilaDeIniciales()
                            Rejilla(
                                estado = estado,
                                onDia = viewModel::seleccionarDia
                            )
                        }
                    }
                }

                item { Leyenda() }

                /*
                 * El interruptor solo se ofrece a quien puede usarlo, y
                 * eso lo dice el ROL, no la respuesta.
                 *
                 * Condicionarlo a `incluyeEquipo` parecía razonable y era
                 * un callejón sin salida: ese campo solo llega a true
                 * cuando YA has pedido el equipo, así que el interruptor
                 * que hace falta para pedirlo no aparecía nunca. Se ve al
                 * ejecutar, no al leer.
                 */
                if (puedeVerEquipo) {
                    item {
                        InterruptorDeEquipo(
                            marcado = estado.verEquipo,
                            onCambia = { viewModel.alternarEquipo() }
                        )
                    }
                }

                item { HorizontalDivider() }

                detalleDelDia(
                    estado = estado,
                    puedeGestionar = puedeGestionar,
                    onAnadirFestivo = { anadiendoFestivoEn = it },
                    onBorrarFestivo = viewModel::borrarFestivo
                )

                item { NotaDeFestivos() }
            }
        }
    }

    anadiendoFestivoEn?.let { dia ->
        DialogoDeFestivo(
            fecha = dia,
            onConfirma = { descripcion, ambito ->
                viewModel.crearFestivo(dia, descripcion, ambito)
                anadiendoFestivoEn = null
            },
            onCancela = { anadiendoFestivoEn = null }
        )
    }

    // El aviso de "creado"/"borrado" se descarta en cuanto se ha leído,
    // para que girar el móvil no lo saque otra vez.
    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

@Composable
private fun CabeceraDeMes(
    periodo: YearMonth,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onHoy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAnterior) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.calendario_mes_anterior)
            )
        }
        Text(
            text = DateFormats.mesYAnio(periodo),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSiguiente) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.calendario_mes_siguiente)
            )
        }
        TextButton(onClick = onHoy) { Text(stringResource(R.string.calendario_hoy)) }
    }
}

/**
 * L M X J V S D.
 *
 * Empieza en lunes y no en domingo: es la semana española, y es además la
 * que usa el resto de la aplicación para contar "esta semana".
 */
@Composable
private fun FilaDeIniciales() {
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { dia ->
            Text(
                text = DateFormats.inicialDelDia(dia),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    // La inicial es un rótulo de columna: leída una a una
                    // por un lector de pantalla no dice nada, y cada celda
                    // ya anuncia su fecha completa.
                    .clearAndSetSemantics { }
            )
        }
    }
}

@Composable
private fun Rejilla(estado: CalendarioUiState, onDia: (LocalDate) -> Unit) {
    val primerDia = estado.periodo.atDay(1)
    // getValue() del lunes es 1, así que el hueco antes del día 1 es
    // exactamente el número de días de semana que le preceden.
    val huecoInicial = primerDia.dayOfWeek.value - 1
    val celdas = huecoInicial + estado.periodo.lengthOfMonth()
    val semanas = (celdas + 6) / 7
    val hoy = LocalDate.now(DateFormats.ZONA_ESPANA)

    Column {
        repeat(semanas) { semana ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { columna ->
                    val indice = semana * 7 + columna
                    val diaDelMes = indice - huecoInicial + 1
                    if (diaDelMes < 1 || diaDelMes > estado.periodo.lengthOfMonth()) {
                        // Hueco de otro mes: se deja vacío en vez de
                        // pintar los días vecinos en gris. Un día de otro
                        // mes que se puede tocar y no hace nada confunde
                        // más de lo que orienta.
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val fecha = estado.periodo.atDay(diaDelMes)
                        Celda(
                            fecha = fecha,
                            esHoy = fecha == hoy,
                            seleccionado = fecha == estado.diaSeleccionado,
                            festivo = estado.festivoPorDia[fecha],
                            ausencias = estado.ausenciasPorDia[fecha].orEmpty(),
                            onPulsa = { onDia(fecha) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Celda(
    fecha: LocalDate,
    esHoy: Boolean,
    seleccionado: Boolean,
    festivo: FestivoDTO?,
    ausencias: List<AusenciaCalendarioDTO>,
    onPulsa: () -> Unit,
    modifier: Modifier = Modifier
) {
    val esFinDeSemana =
        fecha.dayOfWeek == DayOfWeek.SATURDAY || fecha.dayOfWeek == DayOfWeek.SUNDAY

    val fondo = when {
        seleccionado -> MaterialTheme.colorScheme.primaryContainer
        ausencias.isNotEmpty() -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val tinta = when {
        seleccionado -> MaterialTheme.colorScheme.onPrimaryContainer
        ausencias.isNotEmpty() -> MaterialTheme.colorScheme.onSecondaryContainer
        // El fin de semana se apaga en vez de colorearse: es el día "sin
        // nada", y bajarle el contraste dice eso sin gastar un color.
        esFinDeSemana -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    val descripcion = descripcionDeCelda(fecha, festivo, ausencias)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fondo)
            .then(
                if (esHoy) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onPulsa)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = fecha.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (esHoy) FontWeight.Bold else FontWeight.Normal,
                color = tinta
            )
            // El punto de festivo va DEBAJO del número y no detrás: un
            // círculo de color bajo el texto le quitaría contraste justo
            // en la celda que más importa leer.
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (festivo != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    )
            )
        }
    }
}

/**
 * Lo que anuncia un lector de pantalla al pasar por una celda.
 *
 * Sin esto solo diría el número ("15"), que es exactamente el dato que ya
 * se ve y ninguno de los que hacen falta: si ese día es festivo o hay
 * alguien fuera se sabía solo por el color.
 */
@Composable
private fun descripcionDeCelda(
    fecha: LocalDate,
    festivo: FestivoDTO?,
    ausencias: List<AusenciaCalendarioDTO>
): String = buildString {
    append(DateFormats.fechaCorta(fecha))
    festivo?.let { append(". ").append(it.descripcion) }
    if (ausencias.isNotEmpty()) {
        append(". ").append(
            if (ausencias.size == 1 && ausencias.first().propia) {
                stringResource(R.string.calendario_ausencia_propia)
            } else {
                pluralStringResource(
                    R.plurals.calendario_ausencias_cuantas,
                    ausencias.size,
                    ausencias.size
                )
            }
        )
    }
}

@Composable
private fun Leyenda() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.calendario_leyenda_festivo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(16.dp))
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.calendario_leyenda_ausencia),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InterruptorDeEquipo(marcado: Boolean, onCambia: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.calendario_ver_equipo),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = marcado, onCheckedChange = onCambia)
    }
}

/**
 * Nota al pie sobre lo que el calendario NO sabe.
 *
 * No es un descargo de responsabilidad de relleno: los festivos
 * nacionales se calculan, pero el traslado de uno que cae en domingo lo
 * decide el BOE cada año y las fiestas autonómicas y locales las fija
 * cada comunidad y cada ayuntamiento. Prometer "todos los festivos"
 * sería falso, y alguien plantearía sus vacaciones contando con ello.
 */
@Composable
private fun NotaDeFestivos() {
    Text(
        text = stringResource(R.string.calendario_nota_festivos),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
