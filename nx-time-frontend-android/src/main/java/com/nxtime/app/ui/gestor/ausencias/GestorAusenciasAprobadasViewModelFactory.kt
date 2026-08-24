package com.nxtime.app.ui.gestor.ausencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository
import java.lang.IllegalArgumentException

/*
 * GestorAusenciasAprobadasViewModelFactory: Su trabajo es saber cómo crear un 'GestorAusenciasAprobadasViewModel'.
 */
@Suppress("UNCHECKED_CAST")
class GestorAusenciasAprobadasViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    /*
     * Comprueba si Android le está pidiendo un 'GestorAusenciasAprobadasViewModel' y, si es así, lo crea pasándole el 'authRepository'.
     */

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GestorAusenciasAprobadasViewModel::class.java)) {
            return GestorAusenciasAprobadasViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}