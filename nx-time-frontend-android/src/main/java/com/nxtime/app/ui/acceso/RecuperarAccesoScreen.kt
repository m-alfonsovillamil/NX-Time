package com.nxtime.app.ui.acceso

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * "¿Has olvidado tu contraseña o es tu primera vez?" (ADR 014). Ver
 * [RecuperarAccesoViewModel] para por qué son dos pasos.
 */
@Composable
fun RecuperarAccesoScreen(
    onVolver: () -> Unit,
    viewModel: RecuperarAccesoViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaConBarra(
        titulo = stringResource(R.string.recuperar_titulo),
        onVolver = onVolver
    ) { modifier ->
        ColumnaFormulario(modifier = modifier) {

            estado.error?.let { BannerError(mensaje = it.resolver()) }

            when (estado.paso) {
                PasoRecuperacion.CORREO -> {
                    Explicacion(stringResource(R.string.recuperar_explicacion))
                    CampoTexto(
                        valor = estado.email,
                        onCambia = viewModel::onEmailCambia,
                        etiqueta = stringResource(R.string.login_email),
                        tipoTeclado = KeyboardType.Email
                    )
                    BotonPrincipal(
                        texto = stringResource(R.string.recuperar_enviar_codigo),
                        onClick = viewModel::pedirCodigo,
                        cargando = estado.cargando
                    )
                    TextButton(onClick = viewModel::yaTengoCodigo, enabled = !estado.cargando) {
                        Text(stringResource(R.string.recuperar_ya_tengo_codigo))
                    }
                }

                PasoRecuperacion.CODIGO -> {
                    Explicacion(
                        if (estado.codigoPedido) {
                            stringResource(R.string.recuperar_codigo_enviado, estado.email.trim())
                        } else {
                            stringResource(R.string.recuperar_codigo_explicacion)
                        }
                    )
                    CampoTexto(
                        valor = estado.codigo,
                        onCambia = viewModel::onCodigoCambia,
                        etiqueta = stringResource(R.string.recuperar_codigo),
                        tipoTeclado = KeyboardType.NumberPassword
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
                        texto = stringResource(R.string.recuperar_guardar),
                        onClick = viewModel::guardar,
                        cargando = estado.cargando
                    )
                    TextButton(onClick = viewModel::volverAlCorreo, enabled = !estado.cargando) {
                        Text(stringResource(R.string.recuperar_pedir_otro))
                    }
                }

                PasoRecuperacion.HECHO -> {
                    Text(
                        text = stringResource(R.string.recuperar_hecho_titulo),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Explicacion(stringResource(R.string.recuperar_hecho_texto))
                    BotonPrincipal(
                        texto = stringResource(R.string.recuperar_ir_a_login),
                        onClick = onVolver,
                        cargando = false
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Explicacion(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
