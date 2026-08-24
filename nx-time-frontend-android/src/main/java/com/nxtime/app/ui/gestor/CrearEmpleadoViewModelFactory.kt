package com.nxtime.app.ui.gestor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository
import java.lang.IllegalArgumentException

/*
 * CrearEmpleadoViewModelFactory: Su trabajo es saber cómo crear un 'CrearEmpleadoViewModel'.
 */

@Suppress("UNCHECKED_CAST")
class CrearEmpleadoViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    /*
     * Esta es la función que crea el ViewModel. Comprueba si Android le está pidiendo un 'CrearEmpleadoViewModel' y, si es así, lo crea pasándole el 'authRepository'.
     */

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CrearEmpleadoViewModel::class.java)) {
            return CrearEmpleadoViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}