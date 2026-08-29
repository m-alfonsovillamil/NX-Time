package com.nxtime.app.ui.usuario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.launch

data class CambiarContrasenaUiState(
    val actual: String = "",
    val nueva: String = "",
    val repetida: String = "",
    val cargando: Boolean = false,
    val error: MensajeUi? = null,
    val cambiada: Boolean = false
)

/**
 * Cambio de contraseña del usuario que ha iniciado sesión.
 *
 * Las dos comprobaciones locales -- que las contraseñas nuevas coincidan
 * y que lleguen al mínimo -- se hacen antes de salir a la red. La
 * primera el backend no puede hacerla (solo recibe una contraseña
 * nueva), y la segunda ahorra una ida y vuelta para un error evidente.
 */
class CambiarContrasenaViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CambiarContrasenaUiState())
    val uiState: StateFlow<CambiarContrasenaUiState> = _uiState.asStateFlow()

    fun onActualCambia(v: String) = _uiState.update { it.copy(actual = v, error = null) }
    fun onNuevaCambia(v: String) = _uiState.update { it.copy(nueva = v, error = null) }
    fun onRepetidaCambia(v: String) = _uiState.update { it.copy(repetida = v, error = null) }

    fun cambiar() {
        val e = _uiState.value

        if (e.actual.isBlank() || e.nueva.isBlank() || e.repetida.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.error_campos_obligatorios)) }
            return
        }
        if (e.nueva != e.repetida) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.contrasena_no_coinciden)) }
            return
        }
        if (e.nueva.length < MINIMO_CONTRASENA) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.contrasena_corta)) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.cambiarContrasena(
                    CambiarContrasenaRequest(
                        contrasenaAntigua = e.actual,
                        contrasenaNueva = e.nueva
                    )
                )
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(cargando = false, cambiada = true) }
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
    }
}
