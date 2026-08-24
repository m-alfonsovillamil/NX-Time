package com.nxtime.app.ui.gestor.ausencias

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch


/*
 * GestorAusenciasAprobadasActivity: Es la Activity que usa el Gestor para ver el historial de ausencias (aprobadas/rechazadas) y filtrarlas por empleado.
 */
class GestorAusenciasAprobadasViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // LiveData Públicos
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _empleados = MutableLiveData<List<EmpleadoSimpleDTO>>()
    val empleados: LiveData<List<EmpleadoSimpleDTO>> = _empleados

    private val _ausenciasFiltradas = MutableLiveData<List<RespuestaAusencia>>()
    val ausenciasFiltradas: LiveData<List<RespuestaAusencia>> = _ausenciasFiltradas

    // LiveData Privados
    private val _ausenciasCompletas = MutableLiveData<List<RespuestaAusencia>>()

    /**
     * Carga el historial de ausencias (no pendientes) y la lista de empleados.
     */
    fun cargarDatos() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {

                val empleadosDeferred = async { authRepository.getMisEmpleados() }

                val ausenciasDeferred = async { authRepository.getHistorialAusencias() }

                val empleadosResponse = empleadosDeferred.await()
                val ausenciasResponse = ausenciasDeferred.await()

                if (empleadosResponse.isSuccessful && ausenciasResponse.isSuccessful) {
                    val empleados = empleadosResponse.body() ?: emptyList()
                    val ausencias = ausenciasResponse.body() ?: emptyList()

                    _empleados.value = empleados
                    _ausenciasCompletas.value = ausencias
                    _ausenciasFiltradas.value = ausencias
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
     * Se llama cuando el usuario selecciona un nombre del desplegable.
     */
    fun onFiltroCambiado(nombreEmpleado: String?) {
        val listaCompleta = _ausenciasCompletas.value ?: return

        if (nombreEmpleado == null) {
            _ausenciasFiltradas.value = listaCompleta
        } else {

            val listaFiltrada = listaCompleta.filter {
                it.usuario.nombre == nombreEmpleado
            }
            _ausenciasFiltradas.value = listaFiltrada
        }
    }
}