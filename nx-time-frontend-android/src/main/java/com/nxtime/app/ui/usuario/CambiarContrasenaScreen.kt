package com.nxtime.app.ui.usuario

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BotonPrincipal
import com.nxtime.app.ui.components.CampoContrasena
import com.nxtime.app.ui.components.ColumnaFormulario
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.util.resolver

@Composable
fun CambiarContrasenaScreen(
    onCambiada: () -> Unit,
    onVolver: () -> Unit,
    viewModel: CambiarContrasenaViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(estado.cambiada) {
        if (estado.cambiada) onCambiada()
    }

    PantallaConBarra(
        titulo = stringResource(R.string.contrasena_titulo),
        onVolver = onVolver
    ) { modifier ->
        ColumnaFormulario(modifier = modifier) {

            estado.error?.let { BannerError(mensaje = it.resolver()) }

            /*
             * Tres campos de contraseña, cada uno con su propio ojo:
             * CampoContrasena guarda la visibilidad dentro de sí mismo,
             * así que mostrar uno no descubre los otros dos.
             */
            CampoContrasena(
                valor = estado.actual,
                onCambia = viewModel::onActualCambia,
                etiqueta = stringResource(R.string.contrasena_actual)
            )
            CampoContrasena(
                valor = estado.nueva,
                onCambia = viewModel::onNuevaCambia,
                etiqueta = stringResource(R.string.contrasena_nueva)
            )
            CampoContrasena(
                valor = estado.repetida,
                onCambia = viewModel::onRepetidaCambia,
                etiqueta = stringResource(R.string.contrasena_repetir),
                ultimo = true
            )

            BotonPrincipal(
                texto = stringResource(R.string.contrasena_actualizar),
                onClick = viewModel::cambiar,
                cargando = estado.cargando
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
