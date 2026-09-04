package com.nxtime.app.ui.avisos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.AvisoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AvisosUiState(
    val cargando: Boolean = false,
    val avisos: List<AvisoDTO> = emptyList(),
    val noLeidos: Int = 0,
    val error: MensajeUi? = null
)

/**
 * Los avisos de la persona con la sesión iniciada.
 *
 * A diferencia del resto de ViewModel de lista, **este no tiene
 * `init { cargar() }`**, y no es un olvido: se construye desde
 * `NxTimeNavHost` para que la campana y la pantalla compartan
 * instancia, así que existe también mientras se está en el login, donde
 * una petición autenticada saldría sin token. Quien lo usa decide
 * cuándo pedir datos.
 */
class AvisosViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AvisosUiState())
    val uiState: StateFlow<AvisosUiState> = _uiState.asStateFlow()

    /** Solo el número de la campana: no descarga la lista entera. */
    fun refrescarContador() {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getContadorAvisos()
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update { it.copy(noLeidos = cuerpo.noLeidos) }
                }
                // Un fallo aquí no se le enseña a nadie: la campana es
                // un adorno hasta que se toca, y un banner de error en
                // la pantalla de fichar por no poder contar avisos
                // molestaría más de lo que informa.
            } catch (_: Exception) {
                // Mismo motivo.
            }
        }
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getAvisos()
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            avisos = cuerpo,
                            noLeidos = cuerpo.count { aviso -> !aviso.leido },
                            error = null
                        )
                    }
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

    /**
     * Marca uno como leído y actualiza el estado en local, sin recargar.
     *
     * Al contrario que el alta/baja de empleado, aquí no hay ningún otro
     * número en pantalla que se pueda desincronizar, y recargar haría
     * saltar la lista justo debajo del dedo que acaba de tocarla.
     */
    fun marcarLeido(avisoId: Long) {
        val yaEstaba = _uiState.value.avisos.firstOrNull { it.id == avisoId }?.leido ?: return
        if (yaEstaba) return

        _uiState.update { estado ->
            estado.copy(
                avisos = estado.avisos.map { if (it.id == avisoId) it.copy(leido = true) else it },
                noLeidos = (estado.noLeidos - 1).coerceAtLeast(0)
            )
        }

        viewModelScope.launch {
            try {
                authRepository.marcarAvisoLeido(avisoId)
            } catch (_: Exception) {
                // Se queda marcado en pantalla y se corregirá en la
                // siguiente recarga: revertir el punto azul al soltar el
                // dedo confundiría más que dejarlo.
            }
        }
    }

    fun marcarTodosLeidos() {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.marcarTodosLosAvisosLeidos()
                if (respuesta.isSuccessful) {
                    _uiState.update { estado ->
                        estado.copy(
                            avisos = estado.avisos.map { it.copy(leido = true) },
                            noLeidos = 0
                        )
                    }
                } else {
                    _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /**
     * Vacía el estado al cerrar sesión.
     *
     * Sin esto, el ViewModel vive en la Activity y sobrevive al cambio
     * de usuario: quien entrase después vería el contador del anterior.
     */
    fun limpiar() {
        _uiState.value = AvisosUiState()
    }
}
