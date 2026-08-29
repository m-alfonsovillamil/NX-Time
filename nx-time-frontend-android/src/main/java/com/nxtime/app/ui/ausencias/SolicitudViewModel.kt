package com.nxtime.app.ui.ausencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.PeticionAusenciaDTO
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SolicitudUiState(
    val tipo: TipoAusencia = TipoAusencia.VACACIONES,
    val fechaInicio: LocalDate? = null,
    val fechaFin: LocalDate? = null,
    val motivo: String = "",
    val cargando: Boolean = false,
    val error: MensajeUi? = null,
    val enviada: Boolean = false
)

/**
 * Formulario de solicitud de ausencia.
 *
 * Las validaciones vivían en la Activity, mezcladas con el código que
 * leía los campos de la vista, y avisaban con Toast. Ahora están aquí,
 * que es donde se pueden probar sin emulador, y el aviso va al estado.
 *
 * El tipo arranca en VACACIONES en lugar de vacío: es con diferencia el
 * más pedido, y evita el caso en que el desplegable anterior se quedaba
 * sin elegir y la validación tenía que rechazarlo.
 */
class SolicitudViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SolicitudUiState())
    val uiState: StateFlow<SolicitudUiState> = _uiState.asStateFlow()

    fun onTipoCambia(tipo: TipoAusencia) = _uiState.update { it.copy(tipo = tipo, error = null) }
    fun onMotivoCambia(valor: String) = _uiState.update { it.copy(motivo = valor, error = null) }

    fun onFechaInicioCambia(fecha: LocalDate) =
        _uiState.update { it.copy(fechaInicio = fecha, error = null) }

    fun onFechaFinCambia(fecha: LocalDate) =
        _uiState.update { it.copy(fechaFin = fecha, error = null) }

    fun enviar() {
        val e = _uiState.value
        val inicio = e.fechaInicio
        val fin = e.fechaFin

        if (inicio == null || fin == null) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.solicitud_fechas_incompletas)) }
            return
        }
        if (fin.isBefore(inicio)) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.solicitud_fecha_invertida)) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.solicitarAusencia(
                    PeticionAusenciaDTO(
                        // El backend espera fechas de calendario ISO
                        // (LocalDate), no instantes: toString() de
                        // LocalDate ya da "2026-08-29".
                        fechaInicio = inicio.toString(),
                        fechaFin = fin.toString(),
                        tipo = e.tipo,
                        motivo = e.motivo.trim().ifBlank { null }
                    )
                )
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(cargando = false, enviada = true) }
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

}
