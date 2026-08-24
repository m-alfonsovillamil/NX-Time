package com.nxtime.app.ui.ausencias

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

/*
 * AusenciasViewModel: Es la "lógica" de la pantalla AusenciasActivity. Hereda de 'ViewModel' para sobrevivir a giros de pantalla.
 * authRepository: Recibe el repositorio para poder pedirle datos a la API.
 */
class AusenciasViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    /*
     * LiveData: Son "cajas" observables que contienen el estado de la pantalla.
     */
    private val _ausenciasState = MutableLiveData<AusenciasState>()
    val ausenciasState: LiveData<AusenciasState> = _ausenciasState

    /**
     * Carga la lista de peticiones del usuario desde el backend.
     */
    fun cargarMisPeticiones() {

        _ausenciasState.value = AusenciasState.Loading


        viewModelScope.launch {
            try {

                val response = authRepository.getMisPeticiones()

                if (response.isSuccessful && response.body() != null) {

                    val peticiones = response.body()!!
                    _ausenciasState.value = AusenciasState.Success(peticiones)
                } else {

                    _ausenciasState.value = AusenciasState.Error("Error al obtener peticiones: ${response.code()}")
                }
            } catch (e: Exception) {

                _ausenciasState.value = AusenciasState.Error("Error de red: ${e.message}")
            }
        }
    }
}

/*
 * Define los 3 únicos estados posibles en los que puede estar esta pantalla.
 */
sealed class AusenciasState {
    object Loading : AusenciasState()
    data class Success(val peticiones: List<RespuestaAusencia>) : AusenciasState()
    data class Error(val message: String) : AusenciasState()
}