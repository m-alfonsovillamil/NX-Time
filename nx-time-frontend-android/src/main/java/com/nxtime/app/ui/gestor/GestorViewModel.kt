package com.nxtime.app.ui.gestor

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

/*
 * GestorViewModel: Se encarga de pedir las peticiones pendientes y gestionarlas (aprobar/rechazar).
 */

class GestorViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    /*
     * LiveData: contiene el estado de la pantalla. La Activity "observa" esta caja para saber qué mostrar.
     */

    private val _gestorState = MutableLiveData<GestorState>()
    val gestorState: LiveData<GestorState> = _gestorState

    /**
     * Carga la lista de peticiones pendientes desde el repositorio (API).
     */

    fun cargarPeticionesPendientes() {
        _gestorState.value = GestorState.Loading

        viewModelScope.launch {
            try {

                val response = authRepository.getPeticionesPendientes()

                if (response.isSuccessful && response.body() != null) {
                    val peticiones = response.body()!!
                    _gestorState.value = GestorState.Success(peticiones)
                } else {
                    _gestorState.value = GestorState.Error("Error al obtener peticiones: ${response.code()}")
                }
            } catch (e: Exception) {
                _gestorState.value = GestorState.Error("Error de red: ${e.message}")
            }
        }
    }

    /**
     * Aprueba una petición. El comentario es opcional al aprobar.
     */
    fun aprobarPeticion(peticionId: Long, comentario: String? = null) {
        cambiarEstado(peticionId, EstadoAusencia.APROBADA, comentario)
    }

    /**
     * Rechaza una petición. El motivo es OBLIGATORIO: el backend
     * responde 400 si se rechaza sin explicar por qué (Fase 9).
     */
    fun rechazarPeticion(peticionId: Long, motivo: String) {
        cambiarEstado(peticionId, EstadoAusencia.RECHAZADA, motivo)
    }

    /*
     * Fase 9 del backend: los dos POST (aprobar/rechazar) se unificaron
     * en un PATCH con el estado en el cuerpo, así que aquí también hay
     * un único método en vez de dos casi idénticos.
     */
    private fun cambiarEstado(peticionId: Long, estado: EstadoAusencia, comentario: String?) {
        viewModelScope.launch {
            try {
                val response = authRepository.cambiarEstadoPeticion(peticionId, estado, comentario)
                if (response.isSuccessful) {
                    Log.d("GestorViewModel", "Petición $peticionId -> $estado")

                    cargarPeticionesPendientes()
                } else {
                    _gestorState.value =
                        GestorState.Error("Error al cambiar el estado: ${response.code()}")
                }
            } catch (e: Exception) {
                _gestorState.value = GestorState.Error("Error de red: ${e.message}")
            }
        }
    }
}

/*
 * Define los 3 únicos estados posibles de esta pantalla.
 */

sealed class GestorState {
    object Loading : GestorState() // Cargando
    data class Success(val peticiones: List<RespuestaAusencia>) : GestorState() // Éxito con la lista
    data class Error(val message: String) : GestorState() // Error
}