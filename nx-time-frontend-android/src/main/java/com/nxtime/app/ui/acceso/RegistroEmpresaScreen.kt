package com.nxtime.app.ui.acceso

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.BuildConfig
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
 *
 * Desde la 1.15 también con una cuenta de Google o de Microsoft (ADR 044):
 * se sale al navegador como al entrar, y al volver el formulario ya no pide
 * correo ni contraseña.
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

    // Una pestaña del navegador y no un WebView, por lo mismo que en el acceso
    // (ver LoginScreen): Google no deja entrar desde un WebView.
    val contexto = LocalContext.current
    LaunchedEffect(estado.abrirEnNavegador) {
        val url = estado.abrirEnNavegador ?: return@LaunchedEffect
        try {
            CustomTabsIntent.Builder().build().launchUrl(contexto, url.toUri())
            viewModel.navegadorAbierto()
        } catch (e: ActivityNotFoundException) {
            viewModel.sinNavegador()
        }
    }

    PantallaConBarra(
        titulo = stringResource(R.string.registro_titulo),
        onVolver = onVolver
    ) { modifier ->
        ColumnaFormulario(modifier = modifier) {
            // Registrada y esperando aprobación (ADR 046): solo eso.
            val pendiente = estado.empresaPendiente
            if (pendiente != null) {
                EmpresaPendiente(mensaje = pendiente.resolver(), onVolver = onVolver)
                return@ColumnaFormulario
            }

            // Desde la V37 (ADR 034) registrar no abre sesión: se pide el
            // código que ha salido hacia el correo.
            val pendienteDe = estado.pendienteDe
            if (pendienteDe != null) {
                ConfirmarCorreoFormulario(
                    email = pendienteDe,
                    codigo = estado.codigo,
                    onCodigoCambia = viewModel::onCodigoCambia,
                    onConfirmar = viewModel::confirmar,
                    cargando = estado.cargando,
                    error = estado.error
                )
                Spacer(Modifier.height(24.dp))
                return@ColumnaFormulario
            }

            val cuenta = estado.conCuenta
            Text(
                text = if (cuenta != null) {
                    stringResource(R.string.registro_sso_explicacion, cuenta.proveedor, cuenta.correo)
                } else {
                    stringResource(R.string.registro_explicacion)
                },
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
                valor = estado.apellidos,
                onCambia = viewModel::onApellidosCambia,
                etiqueta = stringResource(R.string.registro_apellidos),
                ultimo = cuenta != null
            )
            if (cuenta == null) {
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
            }

            CasillaDeCondiciones(
                marcada = estado.aceptaCondiciones,
                onCambia = viewModel::onAceptaCambia,
                onAbrir = { ruta -> abrirEnElNavegador(contexto, BuildConfig.WEB_URL + ruta) }
            )

            BotonPrincipal(
                texto = stringResource(R.string.registro_crear),
                onClick = viewModel::registrar,
                cargando = estado.cargando
            )

            if (cuenta != null) {
                TextButton(onClick = viewModel::dejarLaCuenta, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.registro_sso_otra_forma))
                }
            } else if (estado.proveedoresSso.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                estado.proveedoresSso.forEach { proveedor ->
                    OutlinedButton(
                        onClick = { viewModel.registrarCon(proveedor) },
                        enabled = !estado.cargando,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(stringResource(R.string.registro_sso_registrar_con, proveedor.nombre))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
