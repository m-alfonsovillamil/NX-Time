package com.nxtime.app.ui.ajustes

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.BuildConfig
import com.nxtime.app.R
import com.nxtime.app.data.session.Tema
import com.nxtime.app.recordatorio.RecordatorioDeFichaje
import com.nxtime.app.recordatorio.ReglaDelRecordatorio
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.resolver

/**
 * Ajustes de la aplicación.
 *
 * Aquí se mudó la tarjeta "Cuenta" del perfil: cambiar la contraseña y
 * cerrar sesión son ajustes de la cuenta, no datos de la ficha. Y se le
 * sumaron el tema, el envío de informes de errores y "Acerca de".
 */
@Composable
fun AjustesScreen(
    onVolver: () -> Unit,
    onIrContrasena: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: AjustesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    /*
     * Programar el trabajo periódico necesita un Context, así que lo hace
     * la pantalla y no el ViewModel -- el mismo reparto que en la descarga
     * del CV. El ViewModel solo guarda la preferencia.
     *
     * Se llama también al cambiar las horas: sin esto el trabajo seguiría
     * avisando a la hora vieja hasta que alguien apagara y encendiera el
     * ajuste.
     */
    fun programar(activo: Boolean, entrada: String, salida: String) {
        if (ReglaDelRecordatorio.esHoraValida(entrada) && ReglaDelRecordatorio.esHoraValida(salida)) {
            RecordatorioDeFichaje.programar(contexto, activo, entrada, salida)
        }
    }

    // El resultado no se usa: si lo deniega, el ajuste queda encendido pero
    // el sistema no dejará notificar, y la propia tarjeta lo explica.
    val permiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    PantallaConBarra(titulo = stringResource(R.string.ajustes_titulo), onVolver = onVolver) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            estado.error?.let {
                BannerError(mensaje = it.resolver(), onReintentar = viewModel::descartarError)
                Spacer(Modifier.height(16.dp))
            }

            Apariencia(temaActual = estado.tema, onElegir = viewModel::cambiarTema)

            Spacer(Modifier.height(16.dp))
            Cuenta(
                cerrando = estado.cerrandoSesiones,
                onIrContrasena = onIrContrasena,
                onCerrarSesion = onCerrarSesion,
                onCerrarTodas = { viewModel.cerrarTodasLasSesiones(onCerrarSesion) }
            )

            Spacer(Modifier.height(16.dp))
            Seguridad(
                activa = estado.huella,
                estadoHuella = estadoDeLaHuella(LocalContext.current),
                onCambiar = viewModel::cambiarHuella
            )

            Spacer(Modifier.height(16.dp))
            Recordatorio(
                activo = estado.recordatorio,
                horaEntrada = estado.horaEntrada,
                horaSalida = estado.horaSalida,
                onCambiarActivo = { activo ->
                    // El permiso solo se pide AL ACTIVAR, y solo en 13+:
                    // pedirlo al arrancar, sin que se vea para qué, es la
                    // forma más segura de que lo denieguen.
                    if (activo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.cambiarRecordatorio(activo)
                    programar(activo, estado.horaEntrada, estado.horaSalida)
                },
                onCambiarHoras = { entrada, salida ->
                    viewModel.cambiarHoras(entrada, salida)
                    programar(estado.recordatorio, entrada, salida)
                }
            )

            Spacer(Modifier.height(16.dp))
            Privacidad(
                activos = estado.informesDeErrores,
                onCambiar = viewModel::cambiarInformesDeErrores
            )

            Spacer(Modifier.height(16.dp))
            AcercaDe()
            Spacer(Modifier.height(24.dp))
        }
    }

    if (estado.confirmandoHuella) {
        DialogoContrasena(
            verificando = estado.verificandoContrasena,
            onConfirmar = viewModel::confirmarHuellaCon,
            onCancelar = viewModel::cancelarActivacionDeHuella
        )
    }
}

/**
 * Pide la contraseña antes de activar la huella.
 *
 * No es burocracia: a quien cogiera el móvil ya desbloqueado le bastaría
 * con activar la huella y poner la suya para quedarse con la cuenta.
 */
@Composable
private fun DialogoContrasena(
    verificando: Boolean,
    onConfirmar: (String) -> Unit,
    onCancelar: () -> Unit
) {
    var contrasena by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.ajustes_huella_confirmar)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.ajustes_huella_confirmar_detalle),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    label = { Text(stringResource(R.string.login_contrasena)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(contrasena) },
                enabled = !verificando && contrasena.isNotBlank()
            ) {
                Text(stringResource(R.string.aceptar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) }
        }
    )
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            contenido()
        }
    }
}

@Composable
private fun Apariencia(temaActual: Tema, onElegir: (Tema) -> Unit) {
    Tarjeta(stringResource(R.string.ajustes_apariencia)) {
        Text(
            text = stringResource(R.string.ajustes_tema),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        // "El del sistema" el primero porque es el valor por defecto: lo
        // que la aplicación hace si nadie toca nada.
        OpcionDeTema(Tema.SISTEMA, R.string.ajustes_tema_sistema, temaActual, onElegir)
        OpcionDeTema(Tema.CLARO, R.string.ajustes_tema_claro, temaActual, onElegir)
        OpcionDeTema(Tema.OSCURO, R.string.ajustes_tema_oscuro, temaActual, onElegir)
    }
}

@Composable
private fun OpcionDeTema(
    tema: Tema,
    etiqueta: Int,
    temaActual: Tema,
    onElegir: (Tema) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = tema == temaActual, onClick = { onElegir(tema) })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = tema == temaActual, onClick = { onElegir(tema) })
        Spacer(Modifier.width(8.dp))
        Text(stringResource(etiqueta), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Cuenta(
    cerrando: Boolean,
    onIrContrasena: () -> Unit,
    onCerrarSesion: () -> Unit,
    onCerrarTodas: () -> Unit
) {
    Tarjeta(stringResource(R.string.ajustes_cuenta)) {
        OutlinedButton(onClick = onIrContrasena, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nav_contrasena))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nav_cerrar_sesion))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCerrarTodas,
            enabled = !cerrando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.ajustes_cerrar_todas))
        }
        // Lo que de verdad pasa, dicho antes de pulsar: el access token ya
        // emitido vale hasta que caduque, así que no es un corte inmediato.
        Text(
            text = stringResource(R.string.ajustes_cerrar_todas_detalle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Seguridad(
    activa: Boolean,
    estadoHuella: EstadoDeLaHuella,
    onCambiar: (Boolean) -> Unit
) {
    Tarjeta(stringResource(R.string.ajustes_seguridad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ajustes_huella),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            // Sin sensor o sin huellas dadas de alta, el interruptor se
            // apaga y se dice POR QUÉ: uno gris sin explicación se lee
            // como una aplicación rota.
            Switch(
                checked = activa,
                onCheckedChange = onCambiar,
                enabled = estadoHuella == EstadoDeLaHuella.DISPONIBLE
            )
        }
        Text(
            text = when (estadoHuella) {
                EstadoDeLaHuella.DISPONIBLE -> stringResource(R.string.ajustes_huella_detalle)
                EstadoDeLaHuella.SIN_REGISTRAR -> stringResource(R.string.ajustes_huella_sin_registrar)
                EstadoDeLaHuella.NO_HAY -> stringResource(R.string.ajustes_huella_no_hay)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Recordatorio(
    activo: Boolean,
    horaEntrada: String,
    horaSalida: String,
    onCambiarActivo: (Boolean) -> Unit,
    onCambiarHoras: (String, String) -> Unit
) {
    Tarjeta(stringResource(R.string.ajustes_recordatorio_seccion)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ajustes_recordatorio),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = activo, onCheckedChange = onCambiarActivo)
        }
        Text(
            text = stringResource(R.string.ajustes_recordatorio_detalle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Las horas solo cuando el recordatorio está encendido: enseñar dos
        // campos que no hacen nada invita a rellenarlos y a no entender por
        // qué no llega ningún aviso.
        if (activo) {
            /*
             * 🚨 Estado local, y no el del ViewModel directamente.
             *
             * Estos campos son controlados: si su `value` fuera el del
             * estado, que solo cambia cuando la hora es VÁLIDA, al teclear
             * "0", "09", "09:"... el campo seguiría enseñando la hora
             * anterior y parecería que no se puede escribir en él.
             *
             * Se guarda cuando el texto está completo (5 caracteres), así
             * que tampoco salta el error a medio escribir.
             */
            var entrada by remember(horaEntrada) { mutableStateOf(horaEntrada) }
            var salida by remember(horaSalida) { mutableStateOf(horaSalida) }

            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = entrada,
                    onValueChange = {
                        entrada = it
                        if (it.length == LARGO_HORA) onCambiarHoras(it, salida)
                    },
                    label = { Text(stringResource(R.string.ajustes_recordatorio_entrada)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = salida,
                    onValueChange = {
                        salida = it
                        if (it.length == LARGO_HORA) onCambiarHoras(entrada, it)
                    },
                    label = { Text(stringResource(R.string.ajustes_recordatorio_salida)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Privacidad(activos: Boolean, onCambiar: (Boolean) -> Unit) {
    Tarjeta(stringResource(R.string.ajustes_privacidad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ajustes_informes_errores),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = activos, onCheckedChange = onCambiar)
        }
        Text(
            text = stringResource(R.string.ajustes_informes_errores_detalle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AcercaDe() {
    Tarjeta(stringResource(R.string.ajustes_acerca_de)) {
        Dato(
            stringResource(R.string.ajustes_version),
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.FLAVOR}"
        )
        Dato(stringResource(R.string.ajustes_servidor), BuildConfig.BASE_URL)
        Dato(stringResource(R.string.ajustes_repositorio), REPOSITORIO)
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = valor, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val REPOSITORIO = "github.com/m-alfonsovillamil/NX-Time"

/** "HH:mm". Con menos caracteres, la hora está a medio escribir. */
private const val LARGO_HORA = 5
