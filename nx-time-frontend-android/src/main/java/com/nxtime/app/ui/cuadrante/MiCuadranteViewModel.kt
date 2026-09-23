package com.nxtime.app.ui.cuadrante

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.DiaTeoricoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.MensajeUi
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MiCuadranteUiState(
    val cargando: Boolean = true,
    val dias: List<DiaTeoricoDTO> = emptyList(),
    val error: MensajeUi? = null
)

/**
 * Mi cuadrante: las dos próximas semanas, en solo lectura (Fase B1).
 *
 * Solo lectura a propósito, y no por falta de tiempo: montar una jornada
 * partida o un turno de noche en 400 dp de ancho es trabajo tirado, y quien
 * organiza los turnos lo hace sentado. El editor de plantillas va a la web
 * (ADR 022). Aquí se mira; allí se decide.
 *
 * @param hoy inyectable para los tests: qué días se piden depende de él.
 */
class MiCuadranteViewModel(
    private val authRepository: AuthRepository,
    private val hoy: () -> LocalDate = { LocalDate.now(DateFormats.ZONA_ESPANA) }
) : ViewModel() {

    private val _uiState = MutableStateFlow(MiCuadranteUiState())
    val uiState: StateFlow<MiCuadranteUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val desde = hoy()
                val respuesta = authRepository.getMiCuadrante(desde, desde.plusDays(DIAS - 1L))
                _uiState.update {
                    if (respuesta.isSuccessful) {
                        it.copy(cargando = false, dias = respuesta.body().orEmpty(), error = null)
                    } else {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun descartarError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        /** Dos semanas: lo que se planifica mirando el móvil. Más allá, la web. */
        const val DIAS = 14
    }
}
