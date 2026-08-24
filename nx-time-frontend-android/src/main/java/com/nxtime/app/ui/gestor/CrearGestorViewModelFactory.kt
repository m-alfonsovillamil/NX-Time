package com.nxtime.app.ui.gestor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository

/*
 * CrearGestorViewModelFactory: Es la "fábrica".
 * Su trabajo es crear el 'CrearGestorViewModel' inyectándole el repositorio.
 */
@Suppress("UNCHECKED_CAST")
class CrearGestorViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CrearGestorViewModel::class.java)) {
            return CrearGestorViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}