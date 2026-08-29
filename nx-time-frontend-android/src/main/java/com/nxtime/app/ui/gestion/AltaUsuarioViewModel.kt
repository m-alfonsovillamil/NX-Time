package com.nxtime.app.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.CrearEmpleadoRequest
import com.nxtime.app.data.dto.CrearGestorRequest
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.launch

data class AltaUsuarioUiState(
    val nombre: String = "",
    val email: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: MensajeUi? = null,
    val creado: Boolean = false
)

/**
 * Alta de una cuenta nueva, sea de empleado o de gestor.
 *
 * `CrearEmpleadoActivity` y `CrearGestorActivity` eran el mismo
 * formulario (nombre, correo y contraseña provisional) escrito dos
 * veces, con sus dos ViewModel y sus dos Factory. Lo único que
 * cambiaba de verdad era a qué endpoint se enviaba, y eso es el
 * parámetro [esGestor].
 */
class AltaUsuarioViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AltaUsuarioUiState())
    val uiState: StateFlow<AltaUsuarioUiState> = _uiState.asStateFlow()

    fun onNombreCambia(v: String) = _uiState.update { it.copy(nombre = v, error = null) }
    fun onEmailCambia(v: String) = _uiState.update { it.copy(email = v, error = null) }
    fun onContrasenaCambia(v: String) = _uiState.update { it.copy(contrasena = v, error = null) }

    fun crear(esGestor: Boolean) {
        val e = _uiState.value

        if (e.nombre.isBlank() || e.email.isBlank() || e.contrasena.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.error_campos_obligatorios)) }
            return
        }
        if (e.contrasena.length < MINIMO_CONTRASENA) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.contrasena_corta)) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val nombre = e.nombre.trim()
                val email = e.email.trim()
                val respuesta = if (esGestor) {
                    authRepository.crearGestor(CrearGestorRequest(nombre, email, e.contrasena))
                } else {
                    authRepository.crearEmpleado(CrearEmpleadoRequest(nombre, email, e.contrasena))
                }

                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(cargando = false, creado = true) }
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
