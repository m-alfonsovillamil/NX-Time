package com.nxtime.app.ui.gestion

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
 * Alta de un empleado o de un gestor: el mismo formulario, con el
 * destino decidido por [esGestor].
 */
@Composable
fun AltaUsuarioScreen(
    esGestor: Boolean,
    onCreado: () -> Unit,
    onVolver: () -> Unit,
    viewModel: AltaUsuarioViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(estado.creado) {
        if (estado.creado) onCreado()
    }

    PantallaConBarra(
        titulo = stringResource(
            if (esGestor) R.string.alta_gestor_titulo else R.string.alta_empleado_titulo
        ),
        onVolver = onVolver
    ) { modifier ->
        ColumnaFormulario(modifier = modifier) {

            estado.error?.let { BannerError(mensaje = it.resolver()) }

            CampoTexto(
                valor = estado.nombre,
                onCambia = viewModel::onNombreCambia,
                etiqueta = stringResource(R.string.alta_nombre)
            )
            CampoTexto(
                valor = estado.email,
                onCambia = viewModel::onEmailCambia,
                etiqueta = stringResource(R.string.alta_email),
                tipoTeclado = KeyboardType.Email
            )
            CampoContrasena(
                valor = estado.contrasena,
                onCambia = viewModel::onContrasenaCambia,
                etiqueta = stringResource(R.string.alta_contrasena),
                ultimo = true
            )

            BotonPrincipal(
                texto = stringResource(R.string.alta_crear),
                onClick = { viewModel.crear(esGestor) },
                cargando = estado.cargando
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
