package com.nxtime.app.ui.gestor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.CrearEmpleadoRequest
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

/*
 * CrearEmpleadoViewModel: Es la "lógica" de la pantalla CrearEmpleadoActivity. Hereda de 'ViewModel' para sobrevivir a giros de pantalla.
 * authRepository: Recibe el repositorio para poder pedirle datos a la API.
 */

class CrearEmpleadoViewModel(private val authRepository: AuthRepository) : ViewModel() {

    // LiveData para la UI

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _creacionExitosa = MutableLiveData<Boolean>()
    val creacionExitosa: LiveData<Boolean> = _creacionExitosa

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Llama al repositorio para crear un nuevo empleado en el backend.
     */

    fun crearEmpleado(nombre: String, email: String, contrasena: String) {

        // 1. Lanza una corutina para la llamada de red.
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {

                // 2. Prepara los datos para enviar.
                val request = CrearEmpleadoRequest(nombre, email, contrasena)

                // 3. Llama al repositorio (que llama a la API).
                val response = authRepository.crearEmpleado(request)

                if (response.isSuccessful) {
                    // 4. ÉXITO: Pone el estado a 'true'.
                    _creacionExitosa.value = true
                } else {
                    // 5. ERROR (del servidor): Lee el error y lo pone en el LiveData.
                    val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                    _error.value = "Error: $errorBody"
                    _creacionExitosa.value = false
                }

            } catch (e: Exception) {
                // 6. ERROR (de red): Pone el estado "Error".
                _error.value = "Error de red: ${e.message}"
                _creacionExitosa.value = false
            } finally {
                // 7. Oculta el ProgressBar (tanto si fue éxito como si fue error).
                _isLoading.value = false
            }
        }
    }
}