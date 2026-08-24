package com.nxtime.app.ui.gestor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository

/**
 * Fábrica para crear instancias de GestorViewModel.
 */
class GestorViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GestorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GestorViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}