package com.nxtime.app.ui.gestor.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository

/*
 * GestorHistorialViewModelFactory: Su trabajo es saber cómo crear un 'GestorHistorialViewModel'.
 * Es necesaria porque el ViewModel necesita el 'AuthRepository' para funcionar, y esta fábrica se encarga de "inyectárselo".
 */

class GestorHistorialViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    /*
     * Esta es la función que crea el ViewModel. Comprueba si Android le está pidiendo un 'GestorHistorialViewModel' y, si es así, lo crea pasándole el 'authRepository'.
     */

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GestorHistorialViewModel::class.java)) {
            return GestorHistorialViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}