package com.nxtime.app.ui.ofertas

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

data class OfertasUiState(
    val cargando: Boolean = false,
    /** El tablón: las vacantes publicadas de mi empresa. */
    val ofertas: List<OfertaDTO> = emptyList(),
    /** Las que he presentado yo. */
    val misCandidaturas: List<CandidaturaDTO> = emptyList(),
    /** La oferta abierta en el diálogo. */
    val seleccionada: OfertaDTO? = null,
    val enviando: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * El tablón de vacantes internas, visto por la plantilla (Fase H).
 *
 * **La app no decide si se puede optar a una oferta.** Eso son dos
 * condiciones —publicada y en plazo— y llegan resueltas en
 * `admiteCandidaturas`; calcularlas aquí sería tener la misma regla en
 * dos sitios, y el día que cambie una de las dos copias se queda atrás.
 *
 * Tampoco elige el CV: lo adjunta el servidor, y es el vigente. Lo único
 * que hace la pantalla es explicar antes de tiempo el caso en que va a
 * fallar —no tener CV subido—, para que el botón no lleve a un error
 * evitable.
 */
class OfertasViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfertasUiState())
    val uiState: StateFlow<OfertasUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val ofertas = authRepository.getOfertas()
                val mias = authRepository.getMisCandidaturas()

                if (!ofertas.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(ofertas))
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        cargando = false,
                        ofertas = ofertas.body().orEmpty(),
                        // Si esta falla, el tablón se ve igual: lo que
                        // hay que mirar son las vacantes.
                        misCandidaturas = mias.body().orEmpty(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun abrir(oferta: OfertaDTO) = _uiState.update { it.copy(seleccionada = oferta) }

    fun cerrar() = _uiState.update { it.copy(seleccionada = null) }

    /**
     * Presentarse. La carta es opcional.
     *
     * Se recarga entero después: presentarse cambia `yaMePresente` de la
     * oferta Y añade una fila a mis candidaturas, y dejar cualquiera de
     * las dos cosas sin refrescar deja la pantalla contando algo que ya
     * no es verdad.
     */
    fun presentarse(ofertaId: Long, carta: String?) {
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.presentarCandidatura(ofertaId, carta)
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            enviando = false,
                            seleccionada = null,
                            aviso = MensajeUi.de(R.string.candidatura_presentada)
                        )
                    }
                    cargar()
                } else {
                    // El 400 de "no tienes CV" llega redactado desde el
                    // servidor y se enseña tal cual: es más concreto que
                    // cualquier texto genérico de la app.
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
