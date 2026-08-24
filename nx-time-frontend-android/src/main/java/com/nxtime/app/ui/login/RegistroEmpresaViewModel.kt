package com.nxtime.app.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.dto.RegistroGestorRequest
import kotlinx.coroutines.launch
import java.lang.IllegalArgumentException

/**
 * RegistroEmpresaViewModel: Se encarga de llamar al repositorio (API) para registrar la nueva empresa y gestor.
 */

class RegistroEmpresaViewModel(private val authRepository: AuthRepository) : ViewModel() {

      /**
       * LiveData: Son "cajas" observables que la Activity mira.
       */

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading


    private val _registroExitoso = MutableLiveData<Boolean>()
    val registroExitoso: LiveData<Boolean> = _registroExitoso

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Llama al repositorio para registrar la nueva empresa/gestor.
     */

    fun registrarEmpresa(nombreEmpresa: String, nombreGestor: String, email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {

                val request = RegistroGestorRequest(nombreEmpresa, nombreGestor, email, password)


                val response = authRepository.registrarEmpresaGestor(request)

                if (response.isSuccessful && response.body() != null) {

                    val authResponse = response.body()!!


                    authRepository.procesarLoginExitoso(authResponse)

                    _registroExitoso.value = true
                } else {

                    val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                    _error.value = "Error: $errorBody"
                    _registroExitoso.value = false
                }

            } catch (e: Exception) {

                _error.value = "Error de red: ${e.message}"
                _registroExitoso.value = false
            } finally {

                _isLoading.value = false
            }
        }
    }
}

/**
 * RegistroEmpresaViewModelFactory: Su trabajo es saber cómo CREAR un 'RegistroEmpresaViewModel'.
 */

@Suppress("UNCHECKED_CAST")
class RegistroEmpresaViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegistroEmpresaViewModel::class.java)) {
            return RegistroEmpresaViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}