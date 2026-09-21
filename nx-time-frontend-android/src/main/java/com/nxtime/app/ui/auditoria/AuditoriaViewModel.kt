package com.nxtime.app.ui.auditoria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.AuditoriaFichajeDTO
import com.nxtime.app.data.dto.ComprobacionDeIntegridadDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuditoriaUiState(
    val cargando: Boolean = true,
    val entradas: List<AuditoriaFichajeDTO> = emptyList(),
    /**
     * La última comprobación automática de la cadena, o null si todavía no se
     * ha hecho ninguna (204) o si no se pudo consultar.
     *
     * Que sea null no es un error que enseñar: la traza se ve igual. Por eso
     * no entra en [error] -- un aviso rojo por no poder pintar un dato de
     * contexto haría parecer rota una pantalla que funciona.
     */
    val integridad: ComprobacionDeIntegridadDTO? = null,
    val error: MensajeUi? = null
)

/**
 * La línea temporal de cambios de un fichaje:
 * `GET /api/v1/auditoria/fichaje/{id}`.
 *
 * Es lo que el README destaca como la parte más interesante del backend
 * -- una traza inmutable, con un trigger en la base que impide UPDATE y
 * DELETE -- y hasta ahora no se podía enseñar desde la app.
 */
class AuditoriaViewModel(
    private val fichajeId: Long,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditoriaUiState())
    val uiState: StateFlow<AuditoriaUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getAuditoriaFichaje(fichajeId)
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, entradas = respuesta.body().orEmpty())
                    }
                    cargarIntegridad()
                } else {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    /**
     * El estado de la cadena, que es contexto de la traza y no la traza.
     *
     * Va en una llamada aparte y detrás, no en paralelo: es lo secundario de
     * esta pantalla, y si falla no debe impedir ver los movimientos. Por eso
     * cualquier problema --204, error del servidor o red-- se resuelve
     * dejándolo en null y no tocando `error`.
     */
    private suspend fun cargarIntegridad() {
        val comprobacion = try {
            authRepository.getUltimaComprobacionDeIntegridad().body()
        } catch (e: Exception) {
            null
        }
        _uiState.update { it.copy(integridad = comprobacion) }
    }
}
