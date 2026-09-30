package com.nxtime.app.ui.kiosco

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.TarjetaKioscoDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import java.time.Instant

/**
 * Fichar en un kiosco (ADR 033): el PIN y la tarjeta, desde el perfil.
 *
 * La tarjeta se enseña a pantalla completa, sobre blanco y con el brillo al
 * máximo mientras está abierta: es lo que hace falta para que la cámara de la
 * tablet la lea a la primera, también con el móvil en ahorro de batería.
 */
@Composable
fun FicharEnKioscoScreen(
    onVolver: () -> Unit,
    viewModel: FicharEnKioscoViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var regenerando by remember { mutableStateOf(false) }

    PantallaConBarra(titulo = stringResource(R.string.kiosco_titulo), onVolver = onVolver) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            estado.error?.let { BannerError(mensaje = it.resolver(), onReintentar = viewModel::descartarError) }
            Text(
                text = stringResource(R.string.kiosco_explicacion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Seccion(titulo = stringResource(R.string.kiosco_pin_seccion)) {
                val datos = estado.estado
                if (datos != null) {
                    Text(stringResource(if (datos.tienePin) R.string.kiosco_tiene_pin else R.string.kiosco_sin_pin))
                    datos.pinBloqueadoHasta
                        ?.takeIf { runCatching { Instant.parse(it).isAfter(Instant.now()) }.getOrDefault(false) }
                        ?.let { hasta ->
                            Text(
                                stringResource(R.string.kiosco_pin_bloqueado, DateFormats.hora(hasta)),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                }
                OutlinedTextField(
                    value = estado.pin,
                    onValueChange = viewModel::cambiarPin,
                    label = { Text(stringResource(R.string.kiosco_pin_campo)) },
                    supportingText = {
                        Text(estado.errorPin?.resolver() ?: stringResource(R.string.kiosco_pin_ayuda))
                    },
                    isError = estado.errorPin != null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = viewModel::guardarPin,
                    enabled = !estado.guardando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (estado.estado?.tienePin == true) R.string.kiosco_cambiar_pin else R.string.kiosco_guardar_pin
                        )
                    )
                }
                if (estado.estado?.tienePin == true) {
                    TextButton(onClick = viewModel::quitarPin, enabled = !estado.guardando) {
                        Text(stringResource(R.string.kiosco_quitar_pin))
                    }
                }
            }

            Seccion(titulo = stringResource(R.string.kiosco_tarjeta_seccion)) {
                Text(
                    stringResource(R.string.kiosco_tarjeta_ayuda),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = viewModel::verTarjeta,
                    enabled = !estado.cargandoTarjeta,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.kiosco_ensenar_tarjeta)) }
                OutlinedButton(
                    onClick = { regenerando = true },
                    enabled = !estado.cargandoTarjeta,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.kiosco_regenerar)) }
            }
        }
    }

    estado.tarjeta?.let { tarjeta -> TarjetaAPantallaCompleta(tarjeta, onCerrar = viewModel::ocultarTarjeta) }

    if (regenerando) {
        AlertDialog(
            onDismissRequest = { regenerando = false },
            title = { Text(stringResource(R.string.kiosco_regenerar_titulo)) },
            text = { Text(stringResource(R.string.kiosco_regenerar_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.regenerarTarjeta()
                    regenerando = false
                }) { Text(stringResource(R.string.kiosco_regenerar)) }
            },
            dismissButton = {
                TextButton(onClick = { regenerando = false }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }

    estado.aviso?.let { aviso ->
        val texto = aviso.resolver()
        LaunchedEffect(aviso) {
            Toast.makeText(contexto, texto, Toast.LENGTH_SHORT).show()
            viewModel.avisoMostrado()
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            contenido()
        }
    }
}

/** La tarjeta sobre blanco, a todo el ancho, con el brillo al máximo mientras se ve. */
@Composable
private fun TarjetaAPantallaCompleta(tarjeta: TarjetaKioscoDTO, onCerrar: () -> Unit) {
    val actividad = LocalContext.current.actividad()
    DisposableEffect(actividad) {
        val ventana = actividad?.window
        val antes = ventana?.attributes?.screenBrightness
        ventana?.let {
            it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            ventana?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = antes ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val modulos = remember(tarjeta.codigo) { CodigoQr.modulos(tarjeta.codigo) }
    val descripcion = stringResource(R.string.kiosco_tarjeta_descripcion)

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .semantics { contentDescription = descripcion }
                ) {
                    val lado = size.minDimension / modulos.size
                    modulos.forEachIndexed { y, fila ->
                        fila.forEachIndexed { x, negro ->
                            if (negro) {
                                drawRect(Color.Black, topLeft = Offset(x * lado, y * lado), size = Size(lado, lado))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                tarjeta.nombre?.let { Text(it, color = Color.Black, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCerrar) { Text(stringResource(R.string.kiosco_cerrar_tarjeta)) }
            }
        }
    }
}

private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}
