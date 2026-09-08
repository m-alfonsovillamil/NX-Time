package com.nxtime.app.ui.denuncias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.DenunciaDTO
import com.nxtime.app.data.dto.ResumenDenunciaDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CanalDenunciasUiState(
    val cargando: Boolean = false,
    /** Las de la empresa: las abiertas primero, las más antiguas arriba. */
    val bandeja: List<ResumenDenunciaDTO> = emptyList(),
    val expediente: DenunciaDTO? = null,
    val enviando: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * El canal de denuncias visto por quien lo instruye (Fase G).
 *
 * Solo llega aquí quien tiene `denuncia:instruir`, que en este proyecto
 * es únicamente ADMIN: la Ley 2/2023 obliga a designar un Responsable
 * del Sistema Interno de Información, y dársela también a un GESTOR
 * haría que la denuncia sobre un GESTOR la leyera él.
 *
 * La app **no comprueba** ninguna de las reglas de instrucción: que
 * cerrar exija conclusión, que un expediente cerrado no se reabra o que
 * nadie instruya la denuncia que presentó él las aplica el servidor, y
 * aquí solo se enseñan sus errores. Reproducirlas sería tener dos
 * versiones de la misma regla, y una de las dos acabaría desfasada.
 */
class CanalDenunciasViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanalDenunciasUiState())
    val uiState: StateFlow<CanalDenunciasUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getBandejaDenuncias()
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, bandeja = respuesta.body().orEmpty())
                    }
                } else {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun abrir(denunciaId: Long) {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getDenuncia(denunciaId)
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    _uiState.update { it.copy(cargando = false, expediente = respuesta.body()) }
                } else {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun cerrarExpediente() = _uiState.update { it.copy(expediente = null) }

    /**
     * Escribir en el expediente. El primer mensaje vale como acuse de
     * recibo, así que esto también mueve el plazo de los 7 días — por eso
     * se recarga la bandeja después.
     */
    fun responder(texto: String) {
        val expediente = _uiState.value.expediente ?: return
        if (texto.isBlank()) {
            return
        }
        enviar(
            accion = { authRepository.responderDenunciaComoInstructor(expediente.id, texto.trim()) },
            aviso = MensajeUi.de(R.string.denuncia_mensaje_enviado)
        )
    }

    /**
     * Mover de estado.
     *
     * La conclusión solo se manda al cerrar. No se valida aquí: el
     * servidor devuelve un 400 con el texto explicado, y ese texto es
     * mejor que cualquiera que pudiéramos inventar en la app.
     */
    fun cambiarEstado(estado: EstadoDenuncia, conclusion: String?) {
        val expediente = _uiState.value.expediente ?: return
        enviar(
            accion = {
                authRepository.cambiarEstadoDenuncia(
                    expediente.id,
                    estado.name,
                    conclusion?.trim()?.takeIf { it.isNotBlank() }
                )
            },
            aviso = MensajeUi.de(
                if (estado.estaAbierta) R.string.denuncia_estado_actualizado
                else R.string.denuncia_cerrada
            )
        )
    }

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    /**
     * Manda algo y deja el expediente y la bandeja al día.
     *
     * Se recargan las dos y no solo el expediente porque cualquiera de
     * las dos acciones cambia también su fila: el acuse apaga el
     * contador de los 7 días y un cierre la manda al final de la lista.
     * Dejar la bandeja con la cifra vieja es el desfase que hace dudar
     * del número.
     */
    private fun enviar(
        accion: suspend () -> retrofit2.Response<DenunciaDTO>,
        aviso: MensajeUi
    ) {
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = accion()
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    _uiState.update {
                        it.copy(enviando = false, expediente = respuesta.body(), aviso = aviso)
                    }
                    cargar()
                } else {
                    _uiState.update {
                        it.copy(enviando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(enviando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }
}
