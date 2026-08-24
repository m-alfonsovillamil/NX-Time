package com.nxtime.app.ui.usuario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository
import java.lang.IllegalArgumentException

/**
 * CambiarContrasenaViewModelFactory: Su trabajo es saber cómo CREAR un 'CambiarContrasenaViewModel'.
 */

@Suppress("UNCHECKED_CAST")
class CambiarContrasenaViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    /**
     * Esta es la función que comprueba si Android le está pidiendo un 'CambiarContrasenaViewModel' y, si es así, lo crea pasándole el 'authRepository'.
     */

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CambiarContrasenaViewModel::class.java)) {
            return CambiarContrasenaViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}