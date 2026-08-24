package com.nxtime.app.ui.ausencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository

/**
 * Creación de instancias de AusenciasViewModel.
 */
class AusenciasViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AusenciasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AusenciasViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}