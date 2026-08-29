package com.nxtime.app.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.PeticionLogin
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val accesoConcedido: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailCambia(valor: String) = _uiState.update { it.copy(email = valor, error = null) }
    fun onContrasenaCambia(valor: String) = _uiState.update { it.copy(contrasena = valor, error = null) }

    fun entrar() {
        val estado = _uiState.value
        // Se valida antes de salir a la red: enviar una petición que ya
        // se sabe inválida solo añade espera y consume el límite de
        // intentos por IP que el backend aplica a /auth/login.
        if (estado.email.isBlank() || estado.contrasena.isBlank()) {
            _uiState.update { it.copy(error = MENSAJE_CAMPOS_VACIOS) }
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

    companion object {
        const val MENSAJE_CAMPOS_VACIOS = "Escribe tu correo y tu contraseña."
    }
}
