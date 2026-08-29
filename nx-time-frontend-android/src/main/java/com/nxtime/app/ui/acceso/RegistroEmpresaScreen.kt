package com.nxtime.app.ui.acceso

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BotonPrincipal
import com.nxtime.app.ui.components.CampoContrasena
import com.nxtime.app.ui.components.CampoTexto
import com.nxtime.app.ui.components.ColumnaFormulario
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.resolver

/**
 * Registro de una empresa nueva y de la cuenta de administrador que la
 * funda.
 *
 * El ViewModel ya existía en la rama antes que esta pantalla, que es
 * justo lo que dejaba la migración a medias: la lógica estaba portada y
 * no había forma de llegar a ella.
 */
@Composable
fun RegistroEmpresaScreen(
    onRegistrado: () -> Unit,
    onVolver: () -> Unit,
    viewModel: RegistroEmpresaViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(estado.registrado) {
        if (estado.registrado) onRegistrado()
    }

    PantallaConBarra(
        titulo = stringResource(R.string.registro_titulo),
        onVolver = onVolver
    ) { modifier ->
        ColumnaFormulario(modifier = modifier) {
            Text(
                text = stringResource(R.string.registro_explicacion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            estado.error?.let { BannerError(mensaje = it.resolver()) }

            CampoTexto(
                valor = estado.empresa,
                onCambia = viewModel::onEmpresaCambia,
                etiqueta = stringResource(R.string.registro_empresa)
            )
            CampoTexto(
                valor = estado.nombre,
                onCambia = viewModel::onNombreCambia,
                etiqueta = stringResource(R.string.registro_nombre)
            )
            CampoTexto(
                valor = estado.email,
                onCambia = viewModel::onEmailCambia,
                etiqueta = stringResource(R.string.login_email),
                tipoTeclado = KeyboardType.Email
            )
            CampoContrasena(
                valor = estado.contrasena,
                onCambia = viewModel::onContrasenaCambia,
                etiqueta = stringResource(R.string.login_contrasena),
                ultimo = true
            )

            BotonPrincipal(
                texto = stringResource(R.string.registro_crear),
                onClick = viewModel::registrar,
                cargando = estado.cargando
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
