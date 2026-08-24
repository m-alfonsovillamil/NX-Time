package com.nxtime.app.ui.solicitud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nxtime.app.data.repository.AuthRepository

/**
 * Fábrica para crear instancias de SolicitudViewModel.
 */
class SolicitudViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SolicitudViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SolicitudViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}