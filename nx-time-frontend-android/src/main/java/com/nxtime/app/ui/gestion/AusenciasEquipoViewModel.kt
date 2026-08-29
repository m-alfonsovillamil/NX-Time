package com.nxtime.app.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.launch

data class AusenciasEquipoUiState(
    val cargando: Boolean = true,
    val peticiones: List<RespuestaAusencia> = emptyList(),
    /** Id de la petición que se está resolviendo ahora mismo. */
    val resolviendo: Long? = null,
    val error: MensajeUi? = null
)

/**
 * Las ausencias del equipo: las pendientes de responder y las ya
 * resueltas.
 *
 * Un único ViewModel para las dos listas, donde antes había dos clases
 * (`GestorViewModel` y `GestorAusenciasAprobadasViewModel`) que
 * cargaban de endpoints distintos y por lo demás hacían lo mismo. La
 * diferencia real cabe en un booleano.
 */
class AusenciasEquipoViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AusenciasEquipoUiState())
    val uiState: StateFlow<AusenciasEquipoUiState> = _uiState.asStateFlow()

    private var resueltas = false

    /**
     * Fija qué lista se muestra y la carga.
     *
     * Lo llama la pantalla desde un `LaunchedEffect`, porque el modo lo
     * decide la ruta de navegación y el ViewModel se construye antes de
     * poder saberlo.
     */
    fun mostrar(resueltas: Boolean) {
        if (this.resueltas == resueltas && !_uiState.value.cargando) return
        this.resueltas = resueltas
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = if (resueltas) {
                    authRepository.getHistorialAusencias()
                } else {
                    authRepository.getPeticionesPendientes()
                }
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update {
                        it.copy(cargando = false, peticiones = cuerpo, error = null)
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

    fun aprobar(peticionId: Long) = resolver(peticionId, EstadoAusencia.APROBADA, comentario = null)

    /**
     * Rechaza una petición.
     *
     * El motivo es obligatorio desde la Fase 9 del backend, que responde
     * 400 sin él. Se comprueba también aquí para que el gestor vea el
     * aviso al instante y no como un error del servidor.
     */
    fun rechazar(peticionId: Long, motivo: String) {
        if (motivo.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.pendientes_motivo_obligatorio)) }
            return
        }
        resolver(peticionId, EstadoAusencia.RECHAZADA, motivo.trim())
    }

    private fun resolver(peticionId: Long, estado: EstadoAusencia, comentario: String?) {
        _uiState.update { it.copy(resolviendo = peticionId, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.cambiarEstadoPeticion(peticionId, estado, comentario)
                if (respuesta.isSuccessful) {
                    /*
                     * Se recarga en vez de quitar la fila a mano: en la
                     * lista de resueltas la petición no desaparece, sino
                     * que cambia de estado, y el backend devuelve además
                     * quién la resolvió y cuándo. Editar la lista en
                     * memoria dejaría esos datos vacíos hasta salir y
                     * volver a entrar.
                     */
                    _uiState.update { it.copy(resolviendo = null) }
                    cargar()
                } else {
                    _uiState.update {
                        it.copy(resolviendo = null, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(resolviendo = null, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    fun descartarError() = _uiState.update { it.copy(error = null) }

}
