package com.nxtime.app.ui.gestor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.CrearGestorRequest
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

/*
 * ViewModel encargado de la lógica de negocio para crear un nuevo co-administrador.
 */

class CrearGestorViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _creacionExitosa = MutableLiveData<Boolean>()
    val creacionExitosa: LiveData<Boolean> = _creacionExitosa

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Función llamada desde el botón "Crear Gestor".
     */

    fun crearGestor(nombre: String, email: String, contrasena: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val request = CrearGestorRequest(nombre, email, contrasena)
                val response = authRepository.crearGestor(request)
                if (response.isSuccessful) {
                    _creacionExitosa.value = true
                } else {
                    _error.value = "Error: ${response.code()}"
                    _creacionExitosa.value = false
                }
            } catch (e: Exception) {
                _error.value = "Error de red: ${e.message}"
                _creacionExitosa.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
}