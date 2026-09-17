package com.nxtime.app.ui.borrados

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.SolicitudBorradoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

data class BorradosUiState(
    val cargando: Boolean = false,
    val pendientes: List<SolicitudBorradoDTO> = emptyList(),
    /** Hay una ejecución o un rechazo en marcha: los botones se apagan. */
    val enviando: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * La bandeja de solicitudes de borrado de datos (ADR 016), para RRHH y ADMIN.
 *
 * **La app no decide si una se puede ejecutar**: cada solicitud trae sus
 * `bloqueos` calculados por el servidor, y el servidor vuelve a comprobarlos
 * al ejecutar. Aquí solo se enseñan y se apaga el botón.
 */
class BorradosViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BorradosUiState())
    val uiState: StateFlow<BorradosUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getBorradosPendientes()
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(cargando = false, pendientes = respuesta.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun ejecutar(solicitudId: Long) = enviar(R.string.borrados_ejecutado) {
        authRepository.ejecutarBorrado(solicitudId)
    }

    fun rechazar(solicitudId: Long, comentario: String) {
        // El servidor también lo rechaza, pero un 400 por un campo vacío que
        // la propia pantalla podía ver es una vuelta al servidor para nada.
        if (comentario.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.de(R.string.borrados_rechazar_sin_comentario)) }
            return
        }
        enviar(R.string.borrados_rechazado) { authRepository.rechazarBorrado(solicitudId, comentario) }
    }

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    /**
     * Recarga después de escribir, también si falla: un 409 porque entre
     * cargar y pulsar alguien abrió una jornada cambia los bloqueos, y la
     * tarjeta tiene que enseñar los de ahora.
     */
    private fun enviar(avisoOk: Int, accion: suspend () -> Response<SolicitudBorradoDTO>) {
        if (_uiState.value.enviando) return
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = accion()
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(enviando = false, aviso = MensajeUi.de(avisoOk)) }
                } else {
                    _uiState.update { it.copy(enviando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(enviando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
            recargarSinBorrarError()
        }
    }

    /** Como [cargar], pero sin tapar el error de la acción que acaba de fallar. */
    private suspend fun recargarSinBorrarError() {
        try {
            val respuesta = authRepository.getBorradosPendientes()
            if (respuesta.isSuccessful) {
                _uiState.update { it.copy(pendientes = respuesta.body().orEmpty()) }
            }
        } catch (e: Exception) {
            // Se queda la lista que había; el error de la acción ya está a la vista.
        }
    }
}
