package com.nxtime.app.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
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
            informesDeErrores = ajustes.informesDeErrores.value
        )
    )
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

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
