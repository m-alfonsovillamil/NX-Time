package com.nxtime.app.ui.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository

/*
 * HistorialViewModelFactory: Su trabajo es saber cómo crear un 'HistorialViewModel'.
 */

class HistorialViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    /*
     * Esta es la función que crea el ViewModel. Comprueba si Android le está pidiendo un 'HistorialViewModel' y, si es así, lo crea pasándole el 'authRepository'.
     */

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistorialViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistorialViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}