package com.nxtime.app.ui.gestor.historial

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.RegistroEquipoDTO
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/*
 * GestorHistorialViewModel: Es la lógica de la pantalla del gestor para ver el historial de fichajes y filtrarlo.
 */

class GestorHistorialViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // LiveData Públicos
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error


    private val _empleados = MutableLiveData<List<EmpleadoSimpleDTO>>()
    val empleados: LiveData<List<EmpleadoSimpleDTO>> = _empleados


    private val _historialFiltrado = MutableLiveData<List<RegistroEquipoDTO>>()
    val historialFiltrado: LiveData<List<RegistroEquipoDTO>> = _historialFiltrado

    // --- LiveData Privados ---

    private val _historialCompleto = MutableLiveData<List<RegistroEquipoDTO>>()

    /**
     * Carga todos los datos necesarios para esta pantalla. Lanza 2 peticiones a la API en paralelo para ahorrar tiempo.
     */
    fun cargarDatos() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {

                val empleadosDeferred = async { authRepository.getMisEmpleados() }
                val historialDeferred = async { authRepository.getHistorialEquipo() }


                val empleadosResponse = empleadosDeferred.await()
                val historialResponse = historialDeferred.await()


                if (empleadosResponse.isSuccessful && historialResponse.isSuccessful) {
                    val empleados = empleadosResponse.body() ?: emptyList()
                    val historial = historialResponse.body() ?: emptyList()


                    _empleados.value = empleados
                    _historialCompleto.value = historial
                    _historialFiltrado.value = historial

                } else {

                    _error.value = "Error al cargar los datos"
                }
            } catch (e: Exception) {
                _error.value = "Error de red: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Se llama desde la Activity cuando el gestor elige un nombre del filtro.
     */

    fun onFiltroCambiado(nombreEmpleado: String?) {
        val listaCompleta = _historialCompleto.value ?: return

        if (nombreEmpleado == null) {

            _historialFiltrado.value = listaCompleta
        } else {

            val listaFiltrada = listaCompleta.filter {
                it.usuario.nombre == nombreEmpleado
            }
            _historialFiltrado.value = listaFiltrada
        }
    }
}