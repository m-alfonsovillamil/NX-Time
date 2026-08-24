package com.nxtime.app.ui.historial

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

/*
 * HistorialViewModel: Es la "lógica" de la pantalla HistorialActivity.
 */

class HistorialViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

     /*
      * LiveData: Son "cajas" observables que la Activity mira.
      */
    private val _historialState = MutableLiveData<HistorialState>()
    val historialState: LiveData<HistorialState> = _historialState

    /**
     * Carga la lista de historial del empleado desde el backend.
     */
    fun cargarHistorial() {
        _historialState.value = HistorialState.Loading

        viewModelScope.launch {
            try {

                val response = authRepository.getHistorial()

                if (response.isSuccessful && response.body() != null) {
                    val historial = response.body()!!
                    _historialState.value = HistorialState.Success(historial)
                } else {

                    _historialState.value = HistorialState.Error("Error al obtener historial: ${response.code()}")
                }
            } catch (e: Exception) {

                _historialState.value = HistorialState.Error("Error de red: ${e.message}")
            }
        }
    }
}

/*
 * HistorialState: Define los 3 únicos estados posibles en los que puede estar esta pantalla.
 */

sealed class HistorialState {
    object Loading : HistorialState() // Cargando
    data class Success(val registros: List<Registro>) : HistorialState() // Éxito con la lista
    data class Error(val message: String) : HistorialState() // Error
}