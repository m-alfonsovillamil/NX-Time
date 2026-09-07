package com.nxtime.app.ui.correcciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.CorreccionDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

data class CorreccionesUiState(
    val cargando: Boolean = false,
    /** Las que me toca resolver a mí. */
    val pendientes: List<CorreccionDTO> = emptyList(),
    /** Las que he pedido yo, para ver en qué han quedado. */
    val mias: List<CorreccionDTO> = emptyList(),
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * Las correcciones de fichaje que me afectan (Fase E).
 *
 * Dos listas en una pantalla porque son las dos caras de lo mismo: lo
 * que espera por mí y lo que yo espero de otros. Y la primera mezcla a
 * propósito las de mi equipo con las que alguien ha pedido sobre MIS
 * fichajes: para quien mira son la misma cosa —cosas que tiene que
 * decidir—, aunque por debajo lleguen por caminos distintos.
 *
 * **La app no decide quién puede resolver qué.** Cada solicitud viene
 * con `puedoResolver` y `puedoDisputar` ya calculados por el servidor,
 * porque la regla depende de quién pidió la corrección y tenerla
 * duplicada aquí es garantizar que las dos copias acaben discrepando.
 */
class CorreccionesViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CorreccionesUiState())
    val uiState: StateFlow<CorreccionesUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val pendientes = authRepository.getCorreccionesPendientes()
                val mias = authRepository.getMisCorrecciones()

                if (!pendientes.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(pendientes))
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        cargando = false,
                        pendientes = pendientes.body().orEmpty(),
                        // Si falla solo esta, la pantalla se ve igual: lo
                        // que hay que resolver es lo urgente.
                        mias = mias.body().orEmpty(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun aprobar(correccionId: Long, comentario: String?) {
        ejecutar(R.string.correcciones_aprobada) {
            authRepository.resolverCorreccion(correccionId, aprobada = true, comentario = comentario)
        }
    }

    fun rechazar(correccionId: Long, comentario: String) {
        ejecutar(R.string.correcciones_rechazada) {
            authRepository.resolverCorreccion(correccionId, aprobada = false, comentario = comentario)
        }
    }

    fun disputar(correccionId: Long, motivo: String) {
        ejecutar(R.string.correcciones_disputada) {
            authRepository.disputarCorreccion(correccionId, motivo)
        }
    }

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    /**
     * Recarga siempre después de escribir: resolver una corrección
     * cambia también el historial de fichajes y el contador de avisos, y
     * retocar la lista en local dejaría el resto desincronizado.
     */
    private fun ejecutar(avisoOk: Int, accion: suspend () -> Response<CorreccionDTO>) {
        viewModelScope.launch {
            try {
                val respuesta = accion()
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(aviso = MensajeUi.de(avisoOk)) }
                    cargar()
                } else {
                    _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }
}
