package com.nxtime.app.ui.fichar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.theme.LocalColoresJornada
import com.nxtime.app.ui.util.DateFormats

/**
 * Pantalla principal.
 *
 * Conserva la mejor idea del diseño anterior -- el botón circular grande
 * como acción central -- pero le añade lo que le faltaba: una barra
 * superior con el resto de acciones, en lugar del muro de cinco botones
 * apilados que ocupaba media pantalla, y color según el estado real de
 * la jornada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicharScreen(
    onIrHistorial: () -> Unit,
    onIrAusencias: () -> Unit,
    onIrSolicitud: () -> Unit,
    onIrContrasena: () -> Unit,
    onIrGestion: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: FicharViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var menuAbierto by remember { mutableStateOf(false) }

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
                        if (estado.esRolDeGestion) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nav_panel_gestion)) },
                                onClick = { menuAbierto = false; onIrGestion() }
                            )
                        }
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            estado.error?.let { mensaje ->
                BannerError(mensaje = mensaje, onReintentar = viewModel::descartarError)
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BotonFichar(
                    estado = estado,
                    onClick = viewModel::pulsarBotonPrincipal
                )
            }

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

            AccesosRapidos(
                onIrHistorial = onIrHistorial,
                onIrAusencias = onIrAusencias,
                onIrSolicitud = onIrSolicitud
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * El botón central. Cambia de color según el estado, así que basta mirar
 * la pantalla de lejos para saber si se está fichando o no -- antes era
 * siempre del mismo color y había que leer el texto.
 */
@Composable
private fun BotonFichar(estado: FicharUiState, onClick: () -> Unit) {
    val colores = LocalColoresJornada.current
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

    Button(
        onClick = onClick,
        enabled = !estado.cargando,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = fondo,
            contentColor = contenido
        ),
        modifier = Modifier.size(220.dp)
    ) {
        if (estado.cargando) {
            CircularProgressIndicator(color = contenido)
        } else {
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

@Composable
private fun AccesosRapidos(
    onIrHistorial: () -> Unit,
    onIrAusencias: () -> Unit,
    onIrSolicitud: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onIrSolicitud, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nav_solicitar))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onIrHistorial, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_historial))
            }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onIrAusencias, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_ausencias))
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
