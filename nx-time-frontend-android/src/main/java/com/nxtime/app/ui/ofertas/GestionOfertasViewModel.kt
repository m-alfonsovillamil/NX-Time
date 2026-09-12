package com.nxtime.app.ui.ofertas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.CandidaturaDTO
import com.nxtime.app.data.dto.OfertaDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.ResponseBody

data class GestionOfertasUiState(
    val cargando: Boolean = false,
    /** Todas las de la empresa: borradores y cerradas incluidos. */
    val ofertas: List<OfertaDTO> = emptyList(),
    /** La oferta cuyas candidaturas se están mirando. */
    val seleccionada: OfertaDTO? = null,
    val candidaturas: List<CandidaturaDTO> = emptyList(),
    /** La candidatura cuyo CV se está bajando, para no pedirlo dos veces. */
    val cvDescargandose: Long? = null,
    val enviando: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * Publicar vacantes y valorar a quien opta (Fase H).
 *
 * La app **no reproduce** ninguna regla de valoración: que descartar
 * exija comentario, que una candidatura resuelta no se vuelva a mover o
 * que nadie valore la suya propia las aplica el servidor, y aquí solo se
 * enseñan sus errores. La única que sí se refleja es `puedoValorar`, y
 * porque llega **resuelta desde el servidor**: es la misma decisión, no
 * una copia.
 */
class GestionOfertasViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GestionOfertasUiState())
    val uiState: StateFlow<GestionOfertasUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getOfertasDeGestion()
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, ofertas = respuesta.body().orEmpty())
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

    fun crear(titulo: String, descripcion: String, puesto: String?, fechaCierre: String?) {
        if (titulo.isBlank() || descripcion.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.de(R.string.oferta_error_incompleta)) }
            return
        }
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.crearOferta(
                    titulo, descripcion, puesto?.takeIf { it.isNotBlank() }, fechaCierre)
                if (respuesta.isSuccessful) {
                    // Nace en BORRADOR: el aviso lo dice, para que nadie
                    // se quede esperando a que la vea la plantilla.
                    _uiState.update {
                        it.copy(enviando = false, aviso = MensajeUi.de(R.string.oferta_creada))
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

    fun cambiarEstado(ofertaId: Long, estado: EstadoOferta) {
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.cambiarEstadoOferta(ofertaId, estado.name)
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            enviando = false,
                            aviso = MensajeUi.de(
                                if (estado == EstadoOferta.ABIERTA) R.string.oferta_publicada
                                else R.string.oferta_estado_actualizado
                            )
                        )
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

    fun abrirCandidaturas(oferta: OfertaDTO) {
        _uiState.update { it.copy(seleccionada = oferta, cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getCandidaturasDeOferta(oferta.id)
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, candidaturas = respuesta.body().orEmpty())
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

    fun cerrarCandidaturas() =
        _uiState.update { it.copy(seleccionada = null, candidaturas = emptyList()) }

    /**
     * Baja el CV con el que alguien se presentó y devuelve el cuerpo a la
     * pantalla, que es quien tiene el Context para escribirlo — el mismo
     * reparto que en el perfil y en los informes.
     *
     * Se pide **`cvAdjuntoId`**, que es el adjunto congelado: si esa
     * persona ha subido otro currículum desde entonces, lo que se valora
     * sigue siendo el que presentó.
     */
    fun descargarCv(candidatura: CandidaturaDTO, alTener: (ResponseBody, String) -> Unit) {
        if (_uiState.value.cvDescargandose != null) return
        _uiState.update { it.copy(cvDescargandose = candidatura.id, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.descargarAdjunto(candidatura.cvAdjuntoId)
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    alTener(cuerpo, candidatura.cvNombre)
                    _uiState.update { it.copy(cvDescargandose = null) }
                } else {
                    _uiState.update {
                        it.copy(cvDescargandose = null, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                // La excepción real al log: mensajeDeRed la traduce a "no
                // hay conexión" y taparía la causa.
                Log.w("NxTimeOfertas", "Fallo al descargar el CV de una candidatura", e)
                _uiState.update {
                    it.copy(cvDescargandose = null, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    /**
     * Valorar una candidatura.
     *
     * El comentario solo se manda si tiene contenido: `""` no es "sin
     * comentario" para el servidor, y en un descarte pasaría la
     * validación diciendo nada.
     */
    fun valorar(candidaturaId: Long, estado: EstadoCandidatura, comentario: String?) {
        val oferta = _uiState.value.seleccionada ?: return
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.valorarCandidatura(
                    candidaturaId, estado.name, comentario?.takeIf { it.isNotBlank() })
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(enviando = false, aviso = MensajeUi.de(R.string.candidatura_valorada))
                    }
                    // Se recargan las dos: valorar cambia la fila de la
                    // candidatura y, con ella, lo que la lista de ofertas
                    // enseña como pendiente.
                    abrirCandidaturas(oferta)
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

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }
}
