package com.nxtime.app.ui.ajustes

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.IdentidadVinculadaDTO
import com.nxtime.app.data.dto.ProveedorSsoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.sso.AccesoSso
import com.nxtime.app.data.sso.VueltaDeSso
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.util.MensajeUi
import com.nxtime.app.ui.util.resolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un proveedor de este servidor, con la cuenta mía que tiene vinculada, si tiene. */
data class CuentaDeProveedor(val proveedor: ProveedorSsoDTO, val vinculada: IdentidadVinculadaDTO?)

data class CuentasVinculadasUiState(
    /** Vacía si el servidor no tiene ningún proveedor: entonces la tarjeta no se pinta. */
    val cuentas: List<CuentaDeProveedor> = emptyList(),
    /** Se está vinculando o desvinculando: los botones esperan. */
    val trabajando: Boolean = false,
    /** El proveedor que se ha pedido desvincular, mientras se confirma. */
    val confirmandoDesvincular: ProveedorSsoDTO? = null,
    /** La URL que hay que abrir en el navegador para vincular, hasta que se abre. */
    val abrirEnNavegador: String? = null,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * «Cuentas vinculadas» en Ajustes: con qué cuentas de Google o de Microsoft
 * entro, añadir una y quitarla (ADR 036 y 038).
 *
 * Vincular es el mismo viaje que entrar —navegador, proveedor y vuelta por
 * `nxtime://sso`—, pero lo que vuelve es un **código de vínculo**, y hasta que
 * la app lo confirma con su sesión no hay nada vinculado. Va aparte de
 * `AjustesViewModel` porque no comparte nada con las preferencias: es otra
 * conversación con el servidor.
 */
class CuentasVinculadasViewModel(
    private val authRepository: AuthRepository,
    private val accesoSso: AccesoSso
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuentasVinculadasUiState())
    val uiState: StateFlow<CuentasVinculadasUiState> = _uiState.asStateFlow()

    private var proveedores: List<ProveedorSsoDTO> = emptyList()

    init {
        cargar()
        // Como en el acceso: la vuelta puede llegar con Ajustes abierto o
        // antes de que exista, si el sistema cerró la app mientras tanto.
        viewModelScope.launch {
            accesoSso.vueltaDeVinculo.collect { vuelta -> if (vuelta != null) alVolverDelNavegador(vuelta) }
        }
    }

    private fun cargar() {
        viewModelScope.launch {
            try {
                proveedores = authRepository.getProveedoresSso().takeIf { it.isSuccessful }?.body().orEmpty()
                // Sin proveedores no hay nada que enseñar ni que preguntar.
                val mias = if (proveedores.isEmpty()) {
                    emptyList()
                } else {
                    authRepository.getIdentidadesVinculadas().takeIf { it.isSuccessful }?.body().orEmpty()
                }
                pintar(mias)
            } catch (e: Exception) {
                // Los ajustes no dependen de esto: si falla, la tarjeta no sale.
                _uiState.update { it.copy(cuentas = emptyList()) }
            }
        }
    }

    private fun pintar(mias: List<IdentidadVinculadaDTO>) = _uiState.update { estado ->
        estado.copy(cuentas = proveedores.map { p -> CuentaDeProveedor(p, mias.firstOrNull { it.proveedor == p.id }) })
    }

    /** Prepara la ida y pide a la pantalla que abra el navegador. */
    fun vincular(proveedor: ProveedorSsoDTO) = _uiState.update {
        it.copy(error = null, aviso = null, abrirEnNavegador = accesoSso.empezarAVincular(proveedor.inicio))
    }

    fun navegadorAbierto() = _uiState.update { it.copy(abrirEnNavegador = null) }

    /** No hay navegador con el que seguir. */
    fun sinNavegador() {
        accesoSso.gastarVerificador()
        _uiState.update {
            it.copy(abrirEnNavegador = null, error = MensajeUi.Recurso(R.string.vinculadas_sin_navegador))
        }
    }

    private suspend fun alVolverDelNavegador(vuelta: VueltaDeSso) {
        // Se gasta ya: este código y este verificador valen para un intento.
        val verificador = accesoSso.gastarVerificador()
        if (vuelta.error != null) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(mensajeDeVincular(vuelta.error))) }
            return
        }
        if (vuelta.codigo == null || verificador == null) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.vinculadas_error_fallo)) }
            return
        }
        _uiState.update { it.copy(trabajando = true, error = null, aviso = null) }
        try {
            val respuesta = authRepository.confirmarVinculoSso(vuelta.codigo, verificador)
            val mias = respuesta.body()
            if (respuesta.isSuccessful && mias != null) {
                pintar(mias)
                _uiState.update {
                    it.copy(trabajando = false, aviso = MensajeUi.Recurso(R.string.vinculadas_vinculada))
                }
            } else {
                // El servidor dice por qué (ya es de otra persona, ya tengo otra).
                _uiState.update { it.copy(trabajando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(trabajando = false, error = ApiErrorParser.mensajeDeRed(e)) }
        }
    }

    fun pedirDesvincular(proveedor: ProveedorSsoDTO) = _uiState.update { it.copy(confirmandoDesvincular = proveedor) }

    fun cancelarDesvincular() = _uiState.update { it.copy(confirmandoDesvincular = null) }

    fun desvincular() {
        val proveedor = _uiState.value.confirmandoDesvincular ?: return
        _uiState.update { it.copy(confirmandoDesvincular = null, trabajando = true, error = null, aviso = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.desvincularSso(proveedor.id)
                // 404: ya no estaba. El resultado es el que se pedía.
                if (respuesta.isSuccessful || respuesta.code() == 404) {
                    _uiState.update { estado ->
                        estado.copy(
                            trabajando = false,
                            cuentas = estado.cuentas.map {
                                if (it.proveedor.id == proveedor.id) it.copy(vinculada = null) else it
                            },
                            aviso = MensajeUi.Recurso(R.string.vinculadas_desvinculada)
                        )
                    }
                } else {
                    _uiState.update { it.copy(trabajando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(trabajando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }
}

/**
 * El motivo con el que el servidor devuelve a la app sin código de vínculo.
 * Los que dependen de a quién se vincula (ya es de otra persona, ya tengo
 * otra) no llegan por aquí: los responde el servidor al confirmar.
 */
internal fun mensajeDeVincular(motivo: String): Int = when (motivo) {
    "cancelado" -> R.string.vinculadas_error_cancelado
    "no-disponible" -> R.string.vinculadas_error_no_disponible
    else -> R.string.vinculadas_error_fallo
}

/** La tarjeta de Ajustes. No pinta nada si el servidor no ofrece ningún proveedor. */
@Composable
fun CuentasVinculadas(
    alHaberTarjeta: @Composable () -> Unit = {},
    viewModel: CuentasVinculadasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    // Como en el acceso: una pestaña del navegador encima de la app, que
    // vuelve por nxtime://sso (ver VueltaDeSsoActivity).
    LaunchedEffect(estado.abrirEnNavegador) {
        val url = estado.abrirEnNavegador ?: return@LaunchedEffect
        try {
            CustomTabsIntent.Builder().build().launchUrl(contexto, url.toUri())
            viewModel.navegadorAbierto()
        } catch (e: ActivityNotFoundException) {
            viewModel.sinNavegador()
        }
    }

    if (estado.cuentas.isEmpty()) return

    Tarjeta(stringResource(R.string.vinculadas_titulo)) {
        Text(
            text = stringResource(R.string.vinculadas_detalle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        estado.cuentas.forEach { cuenta ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cuenta.proveedor.nombre, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = cuenta.vinculada?.correo?.takeIf { it.isNotBlank() }
                            ?: stringResource(
                                if (cuenta.vinculada != null) R.string.vinculadas_vinculada_sin_correo
                                else R.string.vinculadas_sin_vincular
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cuenta.vinculada == null) {
                    TextButton(onClick = { viewModel.vincular(cuenta.proveedor) }, enabled = !estado.trabajando) {
                        Text(stringResource(R.string.vinculadas_vincular, cuenta.proveedor.nombre))
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.pedirDesvincular(cuenta.proveedor) },
                        enabled = !estado.trabajando
                    ) {
                        Text(stringResource(R.string.vinculadas_desvincular, cuenta.proveedor.nombre))
                    }
                }
            }
        }
        estado.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.resolver(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        estado.aviso?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.resolver(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    alHaberTarjeta()

    estado.confirmandoDesvincular?.let { proveedor ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarDesvincular,
            title = { Text(stringResource(R.string.vinculadas_confirmar_titulo, proveedor.nombre)) },
            text = { Text(stringResource(R.string.vinculadas_confirmar_texto)) },
            confirmButton = {
                TextButton(onClick = viewModel::desvincular) {
                    Text(stringResource(R.string.vinculadas_confirmar_si))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelarDesvincular) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }
}
