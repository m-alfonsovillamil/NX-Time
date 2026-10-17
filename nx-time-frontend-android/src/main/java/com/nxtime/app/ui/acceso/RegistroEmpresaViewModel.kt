package com.nxtime.app.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.PeticionRegistroSso
import com.nxtime.app.data.dto.ProveedorSsoDTO
import com.nxtime.app.data.dto.RegistroGestorRequest
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.sso.AccesoSso
import com.nxtime.app.data.sso.VueltaDeRegistro
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La cuenta de Google o de Microsoft con la que se está registrando la
 * empresa (ADR 044).
 *
 * @param pase lo que el servidor da al volver del navegador: dice quién es
 *             durante un cuarto de hora, y va con el formulario
 */
data class CuentaDelRegistro(val proveedor: String, val correo: String, val pase: String)

data class RegistroEmpresaUiState(
    val empresa: String = "",
    val nombre: String = "",
    val apellidos: String = "",
    val email: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: MensajeUi? = null,
    /** El correo al que ha salido el código del registro (ADR 034): con él, se pide el código. */
    val pendienteDe: String? = null,
    val codigo: String = "",
    val registrado: Boolean = false,
    /** Con qué cuentas de fuera se puede registrar. Vacío si no hay o si el servidor no contesta. */
    val proveedoresSso: List<ProveedorSsoDTO> = emptyList(),
    /** La URL que hay que abrir en el navegador para empezar, hasta que se abre. */
    val abrirEnNavegador: String? = null,
    /** Con ella, el formulario no pide correo ni contraseña: quién es ya lo ha dicho el proveedor. */
    val conCuenta: CuentaDelRegistro? = null,
    /** La casilla de las condiciones y el contrato de encargo (ADR 046). */
    val aceptaCondiciones: Boolean = false,
    /**
     * Registrada, y esperando a que el operador la apruebe (423, ADR 046): lo
     * que dice el servidor. Con ella, la pantalla solo enseña eso.
     */
    val empresaPendiente: MensajeUi? = null
)

class RegistroEmpresaViewModel(
    private val authRepository: AuthRepository,
    private val accesoSso: AccesoSso
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroEmpresaUiState())
    val uiState: StateFlow<RegistroEmpresaUiState> = _uiState.asStateFlow()

    init {
        cargarProveedoresSso()
        // Como en el acceso: la vuelta puede llegar con esta pantalla abierta
        // o antes de que exista, si el sistema cerró la app mientras tanto.
        viewModelScope.launch {
            accesoSso.vueltaDeRegistro.collect { vuelta -> if (vuelta != null) alVolverDelNavegador(vuelta) }
        }
    }

    private fun cargarProveedoresSso() {
        viewModelScope.launch {
            val proveedores = try {
                authRepository.getProveedoresSso().takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                null
            }
            // Registrar con correo y contraseña no depende de esto.
            _uiState.update { it.copy(proveedoresSso = proveedores.orEmpty()) }
        }
    }

    fun onEmpresaCambia(v: String) = _uiState.update { it.copy(empresa = v, error = null) }
    fun onNombreCambia(v: String) = _uiState.update { it.copy(nombre = v, error = null) }
    fun onApellidosCambia(v: String) = _uiState.update { it.copy(apellidos = v, error = null) }
    fun onEmailCambia(v: String) = _uiState.update { it.copy(email = v, error = null) }
    fun onContrasenaCambia(v: String) = _uiState.update { it.copy(contrasena = v, error = null) }
    fun onCodigoCambia(v: String) = _uiState.update { it.copy(codigo = v, error = null) }
    fun onAceptaCambia(v: Boolean) = _uiState.update { it.copy(aceptaCondiciones = v, error = null) }

    /** Prepara la ida y pide a la pantalla que abra el navegador. */
    fun registrarCon(proveedor: ProveedorSsoDTO) {
        _uiState.update { it.copy(error = null, abrirEnNavegador = accesoSso.empezarARegistrar(proveedor.inicio)) }
    }

    fun navegadorAbierto() = _uiState.update { it.copy(abrirEnNavegador = null) }

    /** No hay navegador con el que seguir: la ida que se había preparado ya no sirve. */
    fun sinNavegador() {
        accesoSso.gastarVerificador()
        _uiState.update {
            it.copy(abrirEnNavegador = null, error = MensajeUi.Recurso(R.string.registro_sso_sin_navegador))
        }
    }

    /** Vuelve al formulario de siempre, con correo y contraseña. */
    fun dejarLaCuenta() = _uiState.update { it.copy(conCuenta = null, error = null) }

    private suspend fun alVolverDelNavegador(vuelta: VueltaDeRegistro) {
        // Se gasta ya: estos códigos y este verificador valen para un intento.
        val verificador = accesoSso.gastarVerificador()
        if (vuelta.error != null) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(mensajeDeRegistroConSso(vuelta.error))) }
            return
        }
        if (verificador == null || (vuelta.alta == null && vuelta.codigo == null)) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.registro_sso_error_fallo)) }
            return
        }
        _uiState.update { it.copy(cargando = true, error = null) }
        try {
            if (vuelta.alta != null) {
                val respuesta = authRepository.canjearRegistroSso(vuelta.alta, verificador)
                val cuenta = respuesta.body()
                if (respuesta.isSuccessful && cuenta != null) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            conCuenta = CuentaDelRegistro(cuenta.nombre, cuenta.correo, cuenta.pase)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(cargando = false, error = MensajeUi.Recurso(R.string.registro_sso_error_fallo))
                    }
                }
            } else {
                // Ya tenía cuenta de NX Time: ha demostrado lo mismo que
                // pulsando «Entrar con…», así que entra.
                val respuesta = authRepository.canjearSso(vuelta.codigo!!, verificador)
                val sesion = respuesta.body()
                if (respuesta.isSuccessful && sesion != null) {
                    authRepository.procesarLoginExitoso(sesion)
                    _uiState.update { it.copy(cargando = false, registrado = true) }
                } else {
                    _uiState.update {
                        it.copy(cargando = false, error = MensajeUi.Recurso(R.string.registro_sso_error_fallo))
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
        }
    }

    fun registrar() {
        val e = _uiState.value
        val cuenta = e.conCuenta
        if (cuenta != null) {
            registrarConLaCuenta(e, cuenta)
            return
        }
        if (e.empresa.isBlank() || e.nombre.isBlank() || e.apellidos.isBlank()
            || e.email.isBlank() || e.contrasena.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.error_campos_obligatorios)) }
            return
        }
        // El backend exige 8 caracteres desde la Fase 2. Comprobarlo
        // aquí evita un viaje de ida y vuelta para un error que se ve a
        // simple vista.
        if (e.contrasena.length < MINIMO_CONTRASENA) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.contrasena_corta)) }
            return
        }
        if (!e.aceptaCondiciones) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.registro_falta_aceptar)) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.registrarEmpresaGestor(
                    RegistroGestorRequest(
                        nombreEmpresa = e.empresa.trim(),
                        nombre = e.nombre.trim(),
                        apellidos = e.apellidos.trim(),
                        email = e.email.trim(),
                        contrasena = e.contrasena,
                        aceptaCondiciones = true
                    )
                )
                if (respuesta.isSuccessful) {
                    // Desde la V37 no hay sesión todavía: se pide el código
                    // que acaba de salir hacia ese correo.
                    val correo = respuesta.body()?.email ?: e.email.trim()
                    _uiState.update { it.copy(cargando = false, pendienteDe = correo) }
                } else {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(ex))
                }
            }
        }
    }

    /** Con la cuenta del proveedor no hay código que esperar: la respuesta ya es la sesión. */
    private fun registrarConLaCuenta(e: RegistroEmpresaUiState, cuenta: CuentaDelRegistro) {
        if (e.empresa.isBlank() || e.nombre.isBlank() || e.apellidos.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.error_campos_obligatorios)) }
            return
        }
        if (!e.aceptaCondiciones) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.registro_falta_aceptar)) }
            return
        }
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.registrarEmpresaConSso(
                    PeticionRegistroSso(
                        pase = cuenta.pase,
                        nombreEmpresa = e.empresa.trim(),
                        nombre = e.nombre.trim(),
                        apellidos = e.apellidos.trim(),
                        aceptaCondiciones = true
                    )
                )
                val sesion = respuesta.body()
                if (respuesta.isSuccessful && sesion != null) {
                    authRepository.procesarLoginExitoso(sesion)
                    _uiState.update { it.copy(cargando = false, registrado = true) }
                } else if (respuesta.code() == EMPRESA_NO_ACTIVA) {
                    _uiState.update { it.copy(cargando = false, empresaPendiente = ApiErrorParser.mensajeDe(respuesta)) }
                } else if (respuesta.code() == PASE_CADUCADO) {
                    // El pase ya no vale: se vuelve al formulario de siempre,
                    // con los botones para empezar otra vez.
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            conCuenta = null,
                            error = MensajeUi.Recurso(R.string.registro_sso_error_caducado)
                        )
                    }
                } else {
                    // Lo normal aquí es un 409: el nombre de la empresa está
                    // cogido. El pase sigue valiendo para probar con otro.
                    _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (ex: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(ex)) }
            }
        }
    }

    /** Canjea el código del correo; si vale, la sesión queda abierta como tras el login. */
    fun confirmar() {
        val e = _uiState.value
        val correo = e.pendienteDe ?: return
        val codigo = codigoLimpio(e.codigo) ?: run {
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
                    _uiState.update { it.copy(cargando = false, registrado = true) }
                } else if (respuesta.code() == EMPRESA_NO_ACTIVA) {
                    // El correo ya está confirmado; la empresa espera aprobación (ADR 046).
                    _uiState.update { it.copy(cargando = false, empresaPendiente = ApiErrorParser.mensajeDe(respuesta)) }
                } else {
                    _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (ex: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(ex)) }
            }
        }
    }

    companion object {
        const val MINIMO_CONTRASENA = 8

        /** Lo que responde el servidor a un pase que no vale o ha caducado. */
        private const val PASE_CADUCADO = 404

        /** Lo que responde el servidor si la empresa espera aprobación o está suspendida (ADR 046). */
        private const val EMPRESA_NO_ACTIVA = 423
    }
}

/**
 * Por qué no se ha podido seguir registrando con esa cuenta. Los motivos son
 * los del acceso, dichos para quien registra: «entra con tu contraseña» no le
 * sirve a quien todavía no tiene cuenta.
 */
internal fun mensajeDeRegistroConSso(motivo: String): Int = when (motivo) {
    "cancelado" -> R.string.registro_sso_error_cancelado
    "correo-sin-verificar" -> R.string.registro_sso_error_correo_sin_verificar
    "cuenta-inactiva" -> R.string.registro_sso_error_cuenta_inactiva
    "empresa-pendiente" -> R.string.registro_sso_error_empresa_pendiente
    "empresa-suspendida" -> R.string.registro_sso_error_empresa_suspendida
    "ya-tiene-otra" -> R.string.registro_sso_error_ya_tiene_otra
    "no-disponible" -> R.string.registro_sso_error_no_disponible
    else -> R.string.registro_sso_error_fallo
}
