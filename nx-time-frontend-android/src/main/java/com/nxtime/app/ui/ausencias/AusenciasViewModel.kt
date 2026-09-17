package com.nxtime.app.ui.ausencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AusenciasUiState(
    val cargando: Boolean = true,
    val peticiones: List<RespuestaAusencia> = emptyList(),
    val filtro: FiltroAusencias = FiltroAusencias(),
    val error: MensajeUi? = null
) {
    /** Lo que se pinta: las peticiones que pasan el filtro. */
    val visibles: List<RespuestaAusencia> get() = FiltroAusencias.filtrar(peticiones, filtro)
}

/** Las ausencias que ha pedido el propio empleado, con su estado. */
class AusenciasViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AusenciasUiState())
    val uiState: StateFlow<AusenciasUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    /**
     * El filtro se conserva al recargar: tirar para refrescar no debe
     * devolver la lista entera a quien estaba mirando sus vacaciones de 2025.
     */
    fun cambiarFiltro(filtro: FiltroAusencias) = _uiState.update { it.copy(filtro = filtro) }

    fun quitarFiltros() = _uiState.update { it.copy(filtro = FiltroAusencias()) }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getMisPeticiones()
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
}
