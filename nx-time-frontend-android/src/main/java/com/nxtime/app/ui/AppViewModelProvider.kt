package com.nxtime.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.ui.acceso.LoginViewModel
import com.nxtime.app.ui.auditoria.AuditoriaViewModel
import com.nxtime.app.ui.auditoria.CorregirFichajeViewModel
import com.nxtime.app.ui.navegacion.ARG_FICHAJE_ID
import com.nxtime.app.ui.acceso.RegistroEmpresaViewModel
import com.nxtime.app.ui.ausencias.AusenciasViewModel
import com.nxtime.app.ui.ausencias.SolicitudViewModel
import com.nxtime.app.ui.avisos.AvisosViewModel
import com.nxtime.app.ui.fichar.FicharViewModel
import com.nxtime.app.ui.gestion.AltaUsuarioViewModel
import com.nxtime.app.ui.gestion.AusenciasEquipoViewModel
import com.nxtime.app.ui.gestion.HistorialEquipoViewModel
import com.nxtime.app.ui.gestion.PanelEmpresaViewModel
import com.nxtime.app.ui.historial.HistorialViewModel
import com.nxtime.app.ui.perfil.PerfilViewModel
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
        initializer { PanelEmpresaViewModel(app().authRepository) }

        /*
         * Este se pide desde el cuerpo de NxTimeNavHost, fuera de los
         * `composable {}`, así que queda anclado al ViewModelStore de la
         * Activity: es la única instancia, y la comparten la campana de
         * la barra superior y la pantalla de avisos. Es lo más parecido
         * a un "estado de sesión" que tiene la app sin meter Hilt.
         */
        initializer { AvisosViewModel(app().authRepository) }
        initializer { PerfilViewModel(app().authRepository, app().sessionManager) }

        /*
         * Estos dos necesitan saber SOBRE QUÉ fichaje trabajan. El id
         * llega por el `SavedStateHandle`, que es donde Navigation deja
         * los argumentos de la ruta: así el ViewModel lo recibe ya
         * construido en vez de tener que pasárselo desde el composable,
         * y sobrevive a un cambio de configuración sin volver a leerlo.
         */
        initializer {
            AuditoriaViewModel(
                fichajeId = createSavedStateHandle().get<Long>(ARG_FICHAJE_ID) ?: 0L,
                authRepository = app().authRepository
            )
        }
        initializer {
            CorregirFichajeViewModel(
                fichajeId = createSavedStateHandle().get<Long>(ARG_FICHAJE_ID) ?: 0L,
                authRepository = app().authRepository
            )
        }
    }
}

private fun CreationExtras.app(): NxTimeApplication =
    this[APPLICATION_KEY] as NxTimeApplication
