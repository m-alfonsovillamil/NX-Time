package com.nxtime.app.ui.auditoria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.AuditoriaFichajeDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuditoriaUiState(
    val cargando: Boolean = true,
    val entradas: List<AuditoriaFichajeDTO> = emptyList(),
    val error: MensajeUi? = null
)

/**
 * La línea temporal de cambios de un fichaje:
 * `GET /api/v1/auditoria/fichaje/{id}`.
 *
 * Es lo que el README destaca como la parte más interesante del backend
 * -- una traza inmutable, con un trigger en la base que impide UPDATE y
 * DELETE -- y hasta ahora no se podía enseñar desde la app.
 */
class AuditoriaViewModel(
    private val fichajeId: Long,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditoriaUiState())
    val uiState: StateFlow<AuditoriaUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getAuditoriaFichaje(fichajeId)
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, entradas = respuesta.body().orEmpty())
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
