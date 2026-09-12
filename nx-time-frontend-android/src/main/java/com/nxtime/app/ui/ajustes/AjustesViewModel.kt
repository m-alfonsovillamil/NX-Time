package com.nxtime.app.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.PeticionLogin
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.Ajustes
import com.nxtime.app.data.session.Tema
import com.nxtime.app.ui.util.MensajeUi
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AjustesUiState(
    val tema: Tema = Tema.SISTEMA,
    val informesDeErrores: Boolean = true,
    val huella: Boolean = false,
    /** Se está pidiendo la contraseña para activar la huella. */
    val confirmandoHuella: Boolean = false,
    val verificandoContrasena: Boolean = false,
    val cerrandoSesiones: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * Los ajustes de la aplicación.
 *
 * Las preferencias no viven aquí: viven en [Ajustes], que las guarda y las
 * publica para toda la aplicación (el tema lo aplica `MainActivity` sobre
 * el árbol entero). Este ViewModel solo es la pantalla que las cambia,
 * así que escribe en [Ajustes] y refleja el resultado.
 */
class AjustesViewModel(
    private val authRepository: AuthRepository,
    private val ajustes: Ajustes
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AjustesUiState(
            tema = ajustes.tema.value,
            informesDeErrores = ajustes.informesDeErrores.value,
            huella = ajustes.huella.value
        )
    )
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    /**
     * Activar la huella **exige la contraseña**; desactivarla, no.
     *
     * La asimetría es deliberada: a quien cogiera el móvil ya desbloqueado
     * le bastaría con activar la huella y poner la suya para quedarse con
     * la cuenta de otra persona. Quitarla, en cambio, solo deja las cosas
     * como estaban -- y bloquear esa salida sería encerrar a alguien.
     */
    fun cambiarHuella(activa: Boolean) {
        if (!activa) {
            ajustes.cambiarHuella(false)
            _uiState.update { it.copy(huella = false, confirmandoHuella = false) }
            return
        }
        _uiState.update { it.copy(confirmandoHuella = true, error = null) }
    }

    fun cancelarActivacionDeHuella() =
        _uiState.update { it.copy(confirmandoHuella = false, error = null) }

    /**
     * Comprueba la contraseña contra el servidor y, si es la suya, activa
     * la huella.
     *
     * El correo sale del perfil y no de la sesión guardada: `SessionManager`
     * guarda el nombre y el rol, pero no el correo.
     */
    fun confirmarHuellaCon(contrasena: String) {
        if (contrasena.isBlank() || _uiState.value.verificandoContrasena) return
        _uiState.update { it.copy(verificandoContrasena = true, error = null) }
        viewModelScope.launch {
            try {
                val perfil = authRepository.getMiPerfil()
                val correo = perfil.body()?.email
                if (!perfil.isSuccessful || correo == null) {
                    _uiState.update {
                        it.copy(verificandoContrasena = false, error = ApiErrorParser.mensajeDe(perfil))
                    }
                    return@launch
                }

                val respuesta = authRepository.login(PeticionLogin(correo, contrasena))
                if (respuesta.isSuccessful) {
                    ajustes.cambiarHuella(true)
                    _uiState.update {
                        it.copy(
                            huella = true,
                            confirmandoHuella = false,
                            verificandoContrasena = false,
                            aviso = MensajeUi.Recurso(R.string.ajustes_huella_activada)
                        )
                    }
                } else {
                    // El 401 de una contraseña que no es la suya se explica
                    // con el mensaje del servidor, como en el login.
                    _uiState.update {
                        it.copy(verificandoContrasena = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(verificandoContrasena = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    fun cambiarTema(nuevo: Tema) {
        ajustes.cambiarTema(nuevo)
        _uiState.update { it.copy(tema = nuevo) }
    }

    /**
     * Enciende o apaga el envío de informes de errores.
     *
     * **Apagarlo tiene efecto en el momento** (`Sentry.close()`): si
     * alguien lo desactiva es porque no quiere que salga nada más de su
     * móvil, y esperar al siguiente arranque sería no hacerle caso.
     * Encenderlo, en cambio, se aplica al arrancar, que es cuando Sentry
     * puede inicializarse (ver `NxTimeApplication.iniciarSentry`).
     */
    fun cambiarInformesDeErrores(activos: Boolean) {
        ajustes.cambiarInformesDeErrores(activos)
        _uiState.update { it.copy(informesDeErrores = activos) }
        if (!activos) Sentry.close()
    }

    /**
     * Cierra la sesión en todos los dispositivos.
     *
     * El servidor revoca los refresh tokens —incluido el de este móvil—,
     * así que después se sale al login: quedarse dentro con un token que
     * ya no se puede renovar daría una sesión que muere sola al rato, y
     * sin explicación.
     */
    fun cerrarTodasLasSesiones(alCerrar: () -> Unit) {
        if (_uiState.value.cerrandoSesiones) return
        _uiState.update { it.copy(cerrandoSesiones = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.cerrarTodasLasSesiones()
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            cerrandoSesiones = false,
                            aviso = MensajeUi.Recurso(R.string.ajustes_cerrar_todas_hecho)
                        )
                    }
                    alCerrar()
                } else {
                    _uiState.update {
                        it.copy(
                            cerrandoSesiones = false,
                            error = ApiErrorParser.mensajeDe(respuesta)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(cerrandoSesiones = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    fun descartarError() = _uiState.update { it.copy(error = null) }
}
