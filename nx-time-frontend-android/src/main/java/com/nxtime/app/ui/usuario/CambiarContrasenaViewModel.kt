package com.nxtime.app.ui.usuario

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.CambiarContrasenaRequest
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * CambiarContrasenaViewModel:Se encarga de llamar al repositorio (API) para actualizar la contraseña.
 */

class CambiarContrasenaViewModel(private val authRepository: AuthRepository) : ViewModel() {

    /*
     * LiveData: Son "cajas" observables que la Activity mira para saber qué mostrar
     */

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _cambioExitoso = MutableLiveData<Boolean>()
    val cambioExitoso: LiveData<Boolean> = _cambioExitoso

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Llama al repositorio para cambiar la contraseña en el backend.
     */

    fun cambiarContrasena(antigua: String, nueva: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val request = CambiarContrasenaRequest(antigua, nueva)


                val response = authRepository.cambiarContrasena(request)

                if (response.isSuccessful) {
                    _cambioExitoso.value = true
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Error desconocido"

                    _error.value = errorBody
                    _cambioExitoso.value = false
                }
            } catch (e: Exception) {
                _error.value = "Error de red: ${e.message}"
                _cambioExitoso.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
}