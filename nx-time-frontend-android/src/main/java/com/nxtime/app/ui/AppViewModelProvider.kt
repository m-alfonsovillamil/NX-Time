package com.nxtime.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.ui.acceso.LoginViewModel
import com.nxtime.app.ui.acceso.RegistroEmpresaViewModel
import com.nxtime.app.ui.ausencias.AusenciasViewModel
import com.nxtime.app.ui.ausencias.SolicitudViewModel
import com.nxtime.app.ui.fichar.FicharViewModel
import com.nxtime.app.ui.gestion.AltaUsuarioViewModel
import com.nxtime.app.ui.gestion.AusenciasEquipoViewModel
import com.nxtime.app.ui.gestion.HistorialEquipoViewModel
import com.nxtime.app.ui.historial.HistorialViewModel
import com.nxtime.app.ui.usuario.CambiarContrasenaViewModel

/**
 * Fábrica única de todos los ViewModel de la app.
 *
 * Sustituye a las **diez clases `...ViewModelFactory`** que había antes,
 * 255 líneas que solo hacían `return TalViewModel(authRepository) as T`.
 * Eran además inconsistentes: `RegistroEmpresaViewModel` no llegó a
 * tener la suya.
 *
 * No se ha metido Hilt: la aplicación ya montaba sus dependencias a mano
 * en {@link NxTimeApplication} y son tres objetos, así que añadir un
 * marco de inyección con generación de código costaría más de lo que
 * ahorra. Si el proyecto creciera, este es el punto por el que se
 * cambiaría.
 */
object AppViewModelProvider {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { LoginViewModel(app().authRepository) }
        initializer { RegistroEmpresaViewModel(app().authRepository) }
        initializer { FicharViewModel(app().authRepository, app().sessionManager) }
        initializer { HistorialViewModel(app().authRepository) }
        initializer { AusenciasViewModel(app().authRepository) }
        initializer { SolicitudViewModel(app().authRepository) }
        initializer { CambiarContrasenaViewModel(app().authRepository) }
        initializer { HistorialEquipoViewModel(app().authRepository) }
        initializer { AusenciasEquipoViewModel(app().authRepository) }
        initializer { AltaUsuarioViewModel(app().authRepository) }
    }
}

private fun CreationExtras.app(): NxTimeApplication =
    this[APPLICATION_KEY] as NxTimeApplication
