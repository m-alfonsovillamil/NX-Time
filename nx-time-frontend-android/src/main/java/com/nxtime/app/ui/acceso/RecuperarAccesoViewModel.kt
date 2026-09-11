package com.nxtime.app.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PasoRecuperacion {
    /** Escribir el correo y pedir un código, o decir que ya se tiene uno. */
    CORREO,

    /** Escribir el código y elegir la contraseña. */
    CODIGO,

    /** Contraseña guardada: queda ir a iniciar sesión. */
    HECHO
}

data class RecuperarAccesoUiState(
    val paso: PasoRecuperacion = PasoRecuperacion.CORREO,
    val email: String = "",
    val codigo: String = "",
    val nueva: String = "",
    val repetida: String = "",
    /**
     * Si se llegó al paso del código pidiendo uno, y no con "Ya tengo un
     * código". Cambia lo que se le dice: en el primer caso hay que avisar
     * de que solo llega si el correo tiene cuenta.
     */
    val codigoPedido: Boolean = false,
    val cargando: Boolean = false,
    val error: MensajeUi? = null
)

/**
 * Elegir contraseña con un código que llega por correo (ADR 014): para
 * quien la ha olvidado y para quien entra por primera vez.
 *
 * Son dos pasos y no uno porque el código de alta ya está en el correo de
 * bienvenida. Si la única salida fuera "Enviarme un código", quien entra
 * por primera vez pediría otro sin necesidad, y ese otro ANULARÍA el que ya
 * tenía: solo vale el último. Por eso existe "Ya tengo un código".
 */
class RecuperarAccesoViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecuperarAccesoUiState())
    val uiState: StateFlow<RecuperarAccesoUiState> = _uiState.asStateFlow()

    fun onEmailCambia(valor: String) = _uiState.update { it.copy(email = valor, error = null) }

    /** Solo dígitos y como mucho seis: pegar "123 456" desde el correo también vale. */
    fun onCodigoCambia(valor: String) = _uiState.update {
        it.copy(codigo = valor.filter(Char::isDigit).take(DIGITOS_CODIGO), error = null)
    }

    fun onNuevaCambia(valor: String) = _uiState.update { it.copy(nueva = valor, error = null) }
    fun onRepetidaCambia(valor: String) = _uiState.update { it.copy(repetida = valor, error = null) }

    fun pedirCodigo() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.login_email_vacio)) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.solicitarCodigoAcceso(email)
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, paso = PasoRecuperacion.CODIGO, codigoPedido = true)
                    }
                } else {
                    _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /** El de bienvenida, o uno pedido antes: no se pide otro, porque anularía ese. */
    fun yaTengoCodigo() {
        if (_uiState.value.email.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.login_email_vacio)) }
            return
        }
        _uiState.update { it.copy(paso = PasoRecuperacion.CODIGO, codigoPedido = false, error = null) }
    }

    fun volverAlCorreo() = _uiState.update {
        it.copy(paso = PasoRecuperacion.CORREO, codigo = "", error = null)
    }

    fun guardar() {
        val estado = _uiState.value
        val error = when {
            estado.codigo.length != DIGITOS_CODIGO -> R.string.recuperar_codigo_incompleto
            estado.nueva.length < MINIMO_CONTRASENA -> R.string.contrasena_corta
            estado.nueva != estado.repetida -> R.string.contrasena_no_coinciden
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(error)) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.restablecerContrasena(
                    estado.email.trim(), estado.codigo, estado.nueva
                )
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            paso = PasoRecuperacion.HECHO,
                            codigo = "",
                            nueva = "",
                            repetida = ""
                        )
                    }
                } else {
                    // Un código que no vale se vacía: cada intento cuenta
                    // (cinco y se anula), así que no conviene reenviarlo
                    // sin mirarlo.
                    _uiState.update {
                        it.copy(cargando = false, codigo = "", error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    companion object {
        const val DIGITOS_CODIGO = 6
        const val MINIMO_CONTRASENA = 8
    }
}
