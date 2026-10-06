package com.nxtime.app.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.nxtime.app.data.dto.PeticionLogin
import com.nxtime.app.data.dto.ProveedorSsoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.sso.AccesoSso
import com.nxtime.app.data.sso.VueltaDeSso
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: MensajeUi? = null,
    /**
     * Contraseña buena, correo sin confirmar (403, ADR 034): el servidor acaba
     * de mandar otro código a este correo, y se pide aquí.
     */
    val sinConfirmar: String? = null,
    val codigo: String = "",
    val accesoConcedido: Boolean = false,
    /**
     * Con qué cuentas de fuera se puede entrar (ADR 036). Vacío mientras no se
     * sabe y vacío si el servidor no contesta: sin la lista no hay botones, y
     * la pantalla es la de siempre.
     */
    val proveedoresSso: List<ProveedorSsoDTO> = emptyList(),
    /** La URL que hay que abrir en el navegador para empezar un SSO, hasta que se abre. */
    val abrirEnNavegador: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val accesoSso: AccesoSso
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        cargarProveedoresSso()
        // La vuelta del navegador puede llegar con esta pantalla ya abierta
        // (lo normal) o antes de que exista, si el sistema cerró la app
        // mientras la persona elegía cuenta: por eso es un estado y no un aviso.
        viewModelScope.launch {
            accesoSso.vuelta.collect { vuelta -> if (vuelta != null) alVolverDelNavegador(vuelta) }
        }
    }

    private fun cargarProveedoresSso() {
        viewModelScope.launch {
            val proveedores = try {
                authRepository.getProveedoresSso().takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                null
            }
            // Entrar con contraseña no depende de esto: si falla, no se dice nada.
            _uiState.update { it.copy(proveedoresSso = proveedores.orEmpty()) }
        }
    }

    /** Prepara la ida y pide a la pantalla que abra el navegador. */
    fun entrarCon(proveedor: ProveedorSsoDTO) {
        _uiState.update { it.copy(error = null, abrirEnNavegador = accesoSso.empezar(proveedor.inicio)) }
    }

    fun navegadorAbierto() = _uiState.update { it.copy(abrirEnNavegador = null) }

    /** No hay navegador con el que seguir. */
    fun sinNavegador() = _uiState.update {
        it.copy(abrirEnNavegador = null, error = MensajeUi.Recurso(R.string.sso_sin_navegador))
    }

    private suspend fun alVolverDelNavegador(vuelta: VueltaDeSso) {
        // Se gasta ya: este código y este verificador valen para un intento.
        val verificador = accesoSso.gastarVerificador()
        if (vuelta.error != null) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(mensajeDeSso(vuelta.error))) }
            return
        }
        if (vuelta.codigo == null || verificador == null) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.sso_error_fallo)) }
            return
        }
        _uiState.update { it.copy(cargando = true, error = null) }
        try {
            val respuesta = authRepository.canjearSso(vuelta.codigo, verificador)
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                authRepository.procesarLoginExitoso(cuerpo)
                _uiState.update { it.copy(cargando = false, accesoConcedido = true) }
            } else {
                _uiState.update { it.copy(cargando = false, error = MensajeUi.Recurso(R.string.sso_error_fallo)) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
        }
    }

    fun onEmailCambia(valor: String) = _uiState.update { it.copy(email = valor, error = null) }
    fun onContrasenaCambia(valor: String) = _uiState.update { it.copy(contrasena = valor, error = null) }
    fun onCodigoCambia(valor: String) = _uiState.update { it.copy(codigo = valor, error = null) }

    fun entrar() {
        val estado = _uiState.value
        // Se valida antes de salir a la red: enviar una petición que ya
        // se sabe inválida solo añade espera y consume el límite de
        // intentos por IP que el backend aplica a /auth/login.
        // Se avisa del campo que falta y no de "los campos" en
        // general: strings.xml ya traía un texto para cada uno.
        if (estado.email.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.login_email_vacio)) }
            return
        }
        if (estado.contrasena.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.login_contrasena_vacia)) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.login(
                    PeticionLogin(estado.email.trim(), estado.contrasena)
                )
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    authRepository.procesarLoginExitoso(cuerpo)
                    _uiState.update { it.copy(cargando = false, accesoConcedido = true) }
                } else if (respuesta.code() == 403) {
                    // El login no da 403 por nada más (ver AuthController).
                    _uiState.update { it.copy(cargando = false, sinConfirmar = estado.email.trim()) }
                } else {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }


    /** Canjea el código del correo; si vale, entra. */
    fun confirmar() {
        val estado = _uiState.value
        val correo = estado.sinConfirmar ?: return
        val codigo = codigoLimpio(estado.codigo) ?: run {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.recuperar_codigo_incompleto)) }
            return
        }
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.confirmarRegistro(correo, codigo)
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    authRepository.procesarLoginExitoso(cuerpo)
                    _uiState.update { it.copy(cargando = false, accesoConcedido = true) }
                } else {
                    _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }
}

/**
 * El texto para el motivo con el que el servidor devuelve a la app tras un SSO
 * (`nxtime://sso?error=…`, ADR 036). Uno que esta versión no conozca se explica
 * como un fallo, no se calla.
 */
@StringRes
internal fun mensajeDeSso(motivo: String): Int = when (motivo) {
    "cancelado" -> R.string.sso_error_cancelado
    "sin-cuenta" -> R.string.sso_error_sin_cuenta
    "correo-sin-verificar" -> R.string.sso_error_correo_sin_verificar
    "cuenta-inactiva" -> R.string.sso_error_cuenta_inactiva
    "ya-tiene-otra" -> R.string.sso_error_ya_tiene_otra
    "no-disponible" -> R.string.sso_error_no_disponible
    else -> R.string.sso_error_fallo
}
