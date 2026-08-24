package com.nxtime.app.ui.solicitud

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.PeticionAusenciaDTO
import com.nxtime.app.data.dto.TipoAusencia
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * SolicitudViewModel: Se encarga de coger los datos del formulario y enviarlos a la API.
 */

class SolicitudViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // LiveData para que la Activity observe el estado
    private val _solicitudState = MutableLiveData<SolicitudState>()
    val solicitudState: LiveData<SolicitudState> = _solicitudState

    // Función para enviar la solicitud
    fun enviarSolicitud(
        fechaInicio: LocalDate,
        fechaFin: LocalDate,
        tipo: TipoAusencia,
        motivo: String?
    ) {
        _solicitudState.value = SolicitudState.Loading

        // Convertimos las fechas a String (formato AAAA-MM-DD)
        val peticionDTO = PeticionAusenciaDTO(
            fechaInicio = fechaInicio.toString(),
            fechaFin = fechaFin.toString(),
            tipo = tipo,
            motivo = motivo?.takeIf { it.isNotBlank() }
        )

        viewModelScope.launch {
            try {
                val response = authRepository.solicitarAusencia(peticionDTO)
                if (response.isSuccessful) {
                    _solicitudState.value = SolicitudState.Success
                } else {
                    _solicitudState.value = SolicitudState.Error("Error al enviar: ${response.code()}")
                }
            } catch (e: Exception) {
                _solicitudState.value = SolicitudState.Error("Error de red: ${e.message}")
            }
        }
    }
}

/**
 * SolicitudState: Define los 3 únicos estados posibles en los que puede estar esta pantalla.
 */
sealed class SolicitudState {
    object Loading : SolicitudState()
    object Success : SolicitudState()
    data class Error(val message: String) : SolicitudState()
}