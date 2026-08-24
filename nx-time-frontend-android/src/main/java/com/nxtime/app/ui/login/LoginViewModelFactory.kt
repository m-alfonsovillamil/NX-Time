package com.nxtime.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository

/**
 * LoginViewModelFactory: su trabajo es saber cómo crear un 'LoginViewModel'.
 */

class LoginViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    /**
     * Esta es la función que crea el ViewModel. Comprueba si Android le está pidiendo un 'LoginViewModel' y, si es así, lo crea pasándole el 'authRepository'.
     */

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}