package com.nxtime.app.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.RegistroGestorRequest
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegistroEmpresaUiState(
    val empresa: String = "",
    val nombre: String = "",
    val email: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val registrado: Boolean = false
)

class RegistroEmpresaViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroEmpresaUiState())
    val uiState: StateFlow<RegistroEmpresaUiState> = _uiState.asStateFlow()

    fun onEmpresaCambia(v: String) = _uiState.update { it.copy(empresa = v, error = null) }
    fun onNombreCambia(v: String) = _uiState.update { it.copy(nombre = v, error = null) }
    fun onEmailCambia(v: String) = _uiState.update { it.copy(email = v, error = null) }
    fun onContrasenaCambia(v: String) = _uiState.update { it.copy(contrasena = v, error = null) }

    fun registrar() {
        val e = _uiState.value
        if (e.empresa.isBlank() || e.nombre.isBlank() || e.email.isBlank() || e.contrasena.isBlank()) {
            _uiState.update { it.copy(error = MENSAJE_CAMPOS_VACIOS) }
            return
        }
        // El backend exige 8 caracteres desde la Fase 2. Comprobarlo
        // aquí evita un viaje de ida y vuelta para un error que se ve a
        // simple vista.
        if (e.contrasena.length < MINIMO_CONTRASENA) {
            _uiState.update { it.copy(error = MENSAJE_CONTRASENA_CORTA) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.registrarEmpresaGestor(
                    RegistroGestorRequest(
                        nombreEmpresa = e.empresa.trim(),
                        nombreGestor = e.nombre.trim(),
                        email = e.email.trim(),
                        password = e.contrasena
                    )
                )
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    authRepository.procesarLoginExitoso(cuerpo)
                    _uiState.update { it.copy(cargando = false, registrado = true) }
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

    companion object {
        const val MINIMO_CONTRASENA = 8
        const val MENSAJE_CAMPOS_VACIOS = "Rellena todos los campos."
        const val MENSAJE_CONTRASENA_CORTA = "La contraseña debe tener al menos 8 caracteres."
    }
}
