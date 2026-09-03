package com.nxtime.app.ui.fichar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.theme.LocalColoresJornada
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import kotlinx.coroutines.delay


/**
 * Pantalla principal.
 *
 * Conserva la mejor idea del diseño anterior -- el botón circular grande
 * como acción central -- y le quita lo que sobraba. Al historial, las
 * ausencias y el panel de gestión se llega ahora por la barra de
 * navegación, así que aquí desaparecen el muro de botones del pie y las
 * entradas del menú que llevaban a ellos: el menú se queda solo con lo
 * que es de la cuenta (contraseña y salir), que no es un destino sino
 * una acción.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicharScreen(
    onIrSolicitud: () -> Unit,
    onIrContrasena: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: FicharViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var menuAbierto by remember { mutableStateOf(false) }

    /*
     * El latido del cronómetro. Vive aquí y no en el ViewModel para que
     * se pare solo cuando la pantalla desaparece: contar segundos con la
     * app en segundo plano no sirve de nada, porque el valor se deriva
     * de `horaEntrada` y se recalcula bien al volver.
     *
     * La clave del efecto es el estado de la jornada, así que el bucle
     * arranca al fichar y se detiene al pausar o terminar.
     */
    if (estado.estado == EstadoJornada.TRABAJANDO) {
        LaunchedEffect(estado.estado, estado.registro?.id) {
            while (true) {
                viewModel.actualizarCronometro()
                delay(1_000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fichar_titulo)) },
                actions = {
                    IconButton(onClick = { menuAbierto = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.mas_opciones)
                        )
                    }
                    DropdownMenu(
                        expanded = menuAbierto,
                        onDismissRequest = { menuAbierto = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_contrasena)) },
                            onClick = { menuAbierto = false; onIrContrasena() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_cerrar_sesion)) },
                            onClick = {
                                menuAbierto = false
                                viewModel.cerrarSesion()
                                onCerrarSesion()
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            estado.error?.let { mensaje ->
                BannerError(
                    mensaje = mensaje.resolver(),
                    onReintentar = viewModel::descartarError
                )
            }

            Spacer(Modifier.height(8.dp))

            if (estado.nombreUsuario.isNotBlank()) {
                Text(
                    text = stringResource(R.string.fichar_saludo, estado.nombreUsuario),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            Text(
                text = textoDeEstado(estado),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
            )

            Spacer(Modifier.height(24.dp))

            BotonFichar(
                estado = estado,
                onClick = viewModel::pulsarBotonPrincipal
            )

            Spacer(Modifier.height(24.dp))

            if (estado.estado != EstadoJornada.SIN_JORNADA) {
                OutlinedButton(
                    onClick = viewModel::pulsarBotonPausa,
                    enabled = !estado.cargando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Icon(
                        if (estado.estado == EstadoJornada.EN_PAUSA) Icons.Default.PlayArrow
                        else Icons.Default.Pause,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            if (estado.estado == EstadoJornada.EN_PAUSA) R.string.fichar_reanudar
                            else R.string.fichar_pausar
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            ResumenDeTiempos(estado)

            estado.resumen?.ausenciasPendientes
                ?.takeIf { it > 0 }
                ?.let { pendientes ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.fichar_ausencias_pendientes,
                            pendientes.toInt(),
                            pendientes.toInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

            Spacer(Modifier.height(16.dp))

            /*
             * Solicitar ausencia se queda aquí, y sola: es la única de
             * las tres antiguas que no es un destino de la barra, sino
             * una acción que se inicia desde la jornada.
             */
            OutlinedButton(
                onClick = onIrSolicitud,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(stringResource(R.string.nav_solicitar))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Las tres tarjetas de totales y el saldo de vacaciones.
 *
 * Los tiempos llegan del backend en minutos enteros y se pintan con
 * `DateFormats.minutos` -- nunca en horas decimales: "7,5 h" obliga a
 * decidir cómo redondear algo que es una cuenta exacta.
 *
 * Si el resumen no ha llegado (o falló), no se pinta nada en vez de
 * enseñar ceros: un "0h 00m" es una afirmación falsa, y un hueco no.
 */
@Composable
private fun ResumenDeTiempos(estado: FicharUiState) {
    val resumen = estado.resumen ?: return

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TarjetaTiempo(
                etiqueta = stringResource(R.string.fichar_tiempo_hoy),
                valor = DateFormats.minutos(estado.minutosHoy),
                destacada = true,
                modifier = Modifier.weight(1f)
            )
            TarjetaTiempo(
                etiqueta = stringResource(R.string.fichar_tiempo_semana),
                valor = DateFormats.minutos(estado.minutosSemana),
                modifier = Modifier.weight(1f)
            )
            TarjetaTiempo(
                etiqueta = stringResource(R.string.fichar_tiempo_mes),
                valor = DateFormats.minutos(estado.minutosMes),
                modifier = Modifier.weight(1f)
            )
        }

        resumen.saldoVacaciones?.let { saldo ->
            Spacer(Modifier.height(12.dp))
            TarjetaTiempo(
                etiqueta = stringResource(R.string.fichar_vacaciones),
                valor = stringResource(
                    R.string.fichar_vacaciones_detalle,
                    saldo.diasDisponibles,
                    saldo.diasTotales
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TarjetaTiempo(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    destacada: Boolean = false
) {
    /*
     * La tarjeta destacada usa `secondaryContainer` y no
     * `primaryContainer`: el primario de la paleta es un cian muy
     * saturado (#6FF6FF) que, puesto en un bloque de este tamaño, grita
     * al lado del resto de la pantalla. El secundario es el mismo
     * verde azulado en versión suave y mantiene el contraste AA.
     */
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (destacada) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (destacada) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)) {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(2.dp))
            Text(text = valor, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * El botón central. Cambia de color según el estado, así que basta mirar
 * la pantalla de lejos para saber si se está fichando o no -- antes era
 * siempre del mismo color y había que leer el texto.
 *
 * Con jornada abierta enseña **el cronómetro dentro del propio botón**.
 * Es el dato que el usuario viene a mirar, y hasta ahora la pantalla no
 * lo daba en ninguna parte: se fichaba y no había forma de saber cuánto
 * se llevaba sin abrir el historial al día siguiente.
 *
 * La háptica al pulsar no es adorno: fichar es una acción con
 * consecuencias legales y el golpe confirma que se ha registrado sin
 * tener que mirar.
 */
@Composable
private fun BotonFichar(estado: FicharUiState, onClick: () -> Unit) {
    val colores = LocalColoresJornada.current
    val haptica = LocalHapticFeedback.current
    val destino = when (estado.estado) {
        EstadoJornada.SIN_JORNADA -> colores.trabajando
        EstadoJornada.TRABAJANDO -> colores.parado
        EstadoJornada.EN_PAUSA -> colores.enPausa
    }
    val contenido = when (estado.estado) {
        EstadoJornada.SIN_JORNADA -> colores.onTrabajando
        EstadoJornada.TRABAJANDO -> colores.onParado
        EstadoJornada.EN_PAUSA -> colores.onEnPausa
    }
    val fondo by animateColorAsState(destino, label = "colorBotonFichar")

    /*
     * El botón se redondea menos cuando hay jornada abierta. Es la idea
     * de Material 3 Expressive de que la forma signifique algo: el
     * círculo perfecto es "listo para empezar" y la forma apretada es
     * "esto está corriendo". Se anima, así que el cambio se ve.
     */
    val redondeo by animateDpAsState(
        targetValue = if (estado.estado == EstadoJornada.SIN_JORNADA) 110.dp else 48.dp,
        label = "formaBotonFichar"
    )

    Button(
        onClick = {
            haptica.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = !estado.cargando,
        shape = RoundedCornerShape(redondeo),
        colors = ButtonDefaults.buttonColors(
            containerColor = fondo,
            contentColor = contenido
        ),
        modifier = Modifier.size(220.dp)
    ) {
        if (estado.cargando) {
            CircularProgressIndicator(color = contenido)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (estado.estado != EstadoJornada.SIN_JORNADA) {
                    val tiempo = DateFormats.cronometro(estado.segundosEnCurso)
                    // "02:14:38" leído en voz alta no se entiende; la
                    // descripción lo dice en palabras. No se marca como
                    // región activa a propósito: cambia cada segundo y un
                    // lector de pantalla lo estaría releyendo sin parar.
                    val descripcion =
                        stringResource(R.string.fichar_cronometro_descripcion, tiempo)
                    Text(
                        text = tiempo,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { contentDescription = descripcion }
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = stringResource(
                        when (estado.estado) {
                            EstadoJornada.SIN_JORNADA -> R.string.fichar_iniciar
                            else -> R.string.fichar_finalizar
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun textoDeEstado(estado: FicharUiState): String = when (estado.estado) {
    EstadoJornada.SIN_JORNADA -> stringResource(R.string.fichar_sin_jornada)
    EstadoJornada.TRABAJANDO -> stringResource(
        R.string.fichar_desde, DateFormats.hora(estado.registro?.horaEntrada)
    )
    EstadoJornada.EN_PAUSA -> stringResource(
        R.string.fichar_en_pausa, DateFormats.hora(estado.registro?.horaEntrada)
    )
}
