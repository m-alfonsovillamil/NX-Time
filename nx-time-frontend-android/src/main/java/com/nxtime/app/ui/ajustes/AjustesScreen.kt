package com.nxtime.app.ui.ajustes

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.BuildConfig
import com.nxtime.app.R
import com.nxtime.app.data.session.Tema
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
            Privacidad(
                activos = estado.informesDeErrores,
                onCambiar = viewModel::cambiarInformesDeErrores
            )

            Spacer(Modifier.height(16.dp))
            AcercaDe()
            Spacer(Modifier.height(24.dp))
        }
    }
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
