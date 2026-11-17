package com.nxtime.app.ui.ausencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.JustificanteDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JustificantesUiState(
    /** Si la empresa deja adjuntarlos (ADR 062). Apagado por defecto. */
    val activo: Boolean = false,
    val porId: Map<Long, JustificanteDTO> = emptyMap(),
    /** La ausencia cuyo justificante se está subiendo, para su botón. */
    val subiendo: Long? = null,
    val aviso: MensajeUi? = null,
    val error: MensajeUi? = null
)

/**
 * Los justificantes de mis ausencias (ADR 062): cuáles tienen y subir uno.
 * Aparte del de la lista para no mezclar la carga de las ausencias con la de
 * los justificantes, que pueden no estar activados.
 */
class JustificantesViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(JustificantesUiState())
    val uiState: StateFlow<JustificantesUiState> = _uiState.asStateFlow()

    private var ultimos: List<Long> = emptyList()

    fun cargar(peticiones: List<Long>) {
        ultimos = peticiones.take(MAXIMO)
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getJustificantes(ultimos)
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update { estado ->
                        estado.copy(activo = cuerpo.activo, porId = cuerpo.justificantes.associateBy { it.peticionId })
                    }
                }
                // Si falla, la lista de ausencias sigue sirviendo: sin botones de justificante.
            } catch (_: Exception) {
            }
        }
    }

    fun subir(peticionId: Long, contenido: ByteArray, nombre: String, mime: String) {
        if (contenido.isEmpty()) {
            _uiState.update { it.copy(error = MensajeUi.de(R.string.justificante_vacio)) }
            return
        }
        if (contenido.size > CINCO_MB) {
            _uiState.update { it.copy(error = MensajeUi.de(R.string.justificante_grande)) }
            return
        }
        _uiState.update { it.copy(subiendo = peticionId, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.subirJustificante(peticionId, contenido, nombre, mime)
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(subiendo = null, aviso = MensajeUi.de(R.string.justificante_subido)) }
                    cargar(ultimos)
                } else {
                    _uiState.update { it.copy(subiendo = null, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(subiendo = null, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun errorMostrado() = _uiState.update { it.copy(error = null) }

    private companion object {
        const val MAXIMO = 200
        const val CINCO_MB = 5 * 1024 * 1024
    }
}
