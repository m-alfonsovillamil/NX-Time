package com.nxtime.app.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.PeticionLogin
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * LoginViewModel: Es la "lógica" de la pantalla de Login. Se encarga de llamar al repositorio (API) para autenticar al usuario.
 */

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    /**
     * LiveData: Es la "caja" que contiene el estado de la pantalla. La Activity "observa" esta caja para saber qué mostrar.
     */

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    /**
     * Esta función se llama cuando el usuario pulsa el botón "Login".
     */

    fun login(email: String, contrasena: String) {
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            try {
                val peticion = PeticionLogin(email, contrasena)
                val response = authRepository.login(peticion)

                if (response.isSuccessful && response.body() != null) {

                    val respuesta = response.body()!!

                    _loginState.value = LoginState.Success(respuesta.token, respuesta.nombre, respuesta.rol)
                } else {
                    _loginState.value = LoginState.Error("Credenciales incorrectas o error del servidor")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Error de red: ${e.message}")
            }
        }
    }
}

/**
 * LoginState: Define los 3 únicos estados posibles en los que puede estar esta pantalla.
 */

sealed class LoginState {
    object Loading : LoginState()
    data class Success(val token: String, val nombre: String, val rol: String) : LoginState()
    data class Error(val message: String) : LoginState()
}