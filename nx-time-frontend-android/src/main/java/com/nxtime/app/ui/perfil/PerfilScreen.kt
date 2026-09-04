package com.nxtime.app.ui.perfil

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.PerfilDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BotonPrincipal
import com.nxtime.app.ui.components.EstadoCargando
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Mi perfil.
 *
 * **Absorbe el menú de tres puntos de "Mi jornada"**: cambiar la
 * contraseña y cerrar sesión son cosas de la cuenta, y su sitio natural
 * es donde está la cuenta, no un desbordamiento en la pantalla de
 * fichar. Por eso `FicharScreen` se queda ya sin ese menú.
 *
 * Lo que se ve pero no se edita — rol, jornada, vacaciones,
 * departamento — se pinta aparte y sin lápiz: el backend no lo acepta
 * en `PATCH /perfil`, y ofrecer un campo para comerse un 403 sería
 * peor que no ofrecerlo.
 */
@Composable
fun PerfilScreen(
    onVolver: () -> Unit,
    onIrContrasena: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: PerfilViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(titulo = stringResource(R.string.perfil_titulo), onVolver = onVolver) { modifier ->
        when {
            estado.cargando -> EstadoCargando(modifier)

            estado.perfil == null -> EstadoErrorPantalla(
                mensaje = estado.error?.resolver().orEmpty(),
                onReintentar = viewModel::cargar,
                modifier = modifier
            )

            else -> {
                val perfil = estado.perfil!!
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Cabecera(perfil)
                    Spacer(Modifier.height(24.dp))

                    if (estado.editando) {
                        Formulario(estado, viewModel)
                    } else {
                        DatosPersonales(perfil, onEditar = viewModel::empezarAEditar)
                    }

                    Spacer(Modifier.height(24.dp))
                    DatosLaborales(perfil)

                    Spacer(Modifier.height(24.dp))
                    Cuenta(onIrContrasena = onIrContrasena, onCerrarSesion = onCerrarSesion)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Cabecera(perfil: PerfilDTO) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = perfil.iniciales,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        Column {
            Text(perfil.nombreCompleto, style = MaterialTheme.typography.titleLarge)
            Text(
                text = perfil.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DatosPersonales(perfil: PerfilDTO, onEditar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.perfil_datos_personales),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEditar) { Text(stringResource(R.string.perfil_editar)) }
            }
            Spacer(Modifier.height(8.dp))
            Dato(stringResource(R.string.perfil_apellidos), perfil.apellidos)
            Dato(
                stringResource(R.string.perfil_fecha_nacimiento),
                perfil.fechaNacimiento?.let { DateFormats.fechaCorta(it) }
            )
            Dato(stringResource(R.string.perfil_puesto), perfil.puesto)
        }
    }
}

@Composable
private fun DatosLaborales(perfil: PerfilDTO) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.perfil_datos_laborales),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Dato(stringResource(R.string.perfil_departamento), perfil.departamentoNombre)
            Dato(stringResource(R.string.perfil_rol), perfil.rol)
            Dato(
                stringResource(R.string.perfil_jornada),
                stringResource(R.string.perfil_jornada_valor, perfil.horasSemanales)
            )
            Dato(
                stringResource(R.string.perfil_vacaciones),
                stringResource(R.string.perfil_vacaciones_valor, perfil.diasVacaciones)
            )
            Spacer(Modifier.height(8.dp))
            // Decirlo en la pantalla evita que alguien busque un lápiz
            // que no existe.
            Text(
                text = stringResource(R.string.perfil_laborales_solo_rrhh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Cuenta(onIrContrasena: () -> Unit, onCerrarSesion: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.perfil_cuenta), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onIrContrasena, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nav_contrasena))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nav_cerrar_sesion))
            }
        }
    }
}

@Composable
private fun Formulario(estado: PerfilUiState, viewModel: PerfilViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.perfil_datos_personales),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = estado.nombre,
                onValueChange = viewModel::onNombreCambia,
                label = { Text(stringResource(R.string.perfil_nombre)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.apellidos,
                onValueChange = viewModel::onApellidosCambia,
                label = { Text(stringResource(R.string.perfil_apellidos)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.fechaNacimiento,
                onValueChange = viewModel::onFechaNacimientoCambia,
                label = { Text(stringResource(R.string.perfil_fecha_nacimiento)) },
                placeholder = { Text(stringResource(R.string.perfil_fecha_formato)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.puesto,
                onValueChange = viewModel::onPuestoCambia,
                label = { Text(stringResource(R.string.perfil_puesto)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            estado.errorFormulario?.let { mensaje ->
                Text(
                    text = mensaje.resolver(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            BotonPrincipal(
                texto = stringResource(R.string.perfil_guardar),
                onClick = viewModel::guardar,
                cargando = estado.guardando
            )
            TextButton(
                onClick = viewModel::cancelarEdicion,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancelar))
            }
        }
    }
}

/** Una fila "etiqueta / valor", con un guion cuando el dato no está. */
@Composable
private fun Dato(etiqueta: String, valor: String?) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (valor.isNullOrBlank()) stringResource(R.string.perfil_sin_dato) else valor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
    HorizontalDivider()
}
