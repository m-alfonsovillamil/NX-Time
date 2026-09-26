package com.nxtime.app.ui.cuadrante

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.IncidenciaDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.util.EstadoDePaginas
import com.nxtime.app.ui.util.MensajeUi
import com.nxtime.app.ui.util.pedirSiguiente
import com.nxtime.app.ui.util.Permisos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

data class IncidenciasUiState(
    val cargando: Boolean = false,
    /** Las mías del año en curso. */
    val mias: List<IncidenciaDTO> = emptyList(),
    /** Las del equipo que esperan decisión. Vacía si no me toca revisar. */
    val delEquipo: List<IncidenciaDTO> = emptyList(),
    /** La bandeja del equipo va por páginas (Fase A7); las mías son de un año. */
    val paginasEquipo: EstadoDePaginas = EstadoDePaginas(),
    val puedeRevisar: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * Incidencias de cuadrante: las mías y, si me toca, las del equipo (Fase B2).
 *
 * Misma forma que HorasExtraViewModel y por las mismas razones: dos listas
 * porque son las dos caras de lo mismo, `puedeRevisar` explícito porque una
 * bandeja vacía no es lo mismo que no tener bandeja, y el rol solo sirve para
 * no pedir un endpoint que daría 403. Quién decide sobre qué lo sigue
 * mandando el servidor.
 */
class IncidenciasViewModel(
    private val authRepository: AuthRepository,
    sessionManager: SessionManager
) : ViewModel() {

    private val authorities = sessionManager.fetchAuthorities()

    private val _uiState = MutableStateFlow(
        IncidenciasUiState(puedeRevisar = Permisos.puedeRevisarIncidencias(authorities))
    )
    val uiState: StateFlow<IncidenciasUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val mias = authRepository.getMisIncidencias()
                val equipo = if (_uiState.value.puedeRevisar) {
                    authRepository.getIncidenciasDelEquipo()
                } else {
                    null
                }

                val fallida = listOfNotNull(mias, equipo).firstOrNull { !it.isSuccessful }
                if (fallida != null) {
                    _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(fallida)) }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        cargando = false,
                        mias = mias.body().orEmpty(),
                        delEquipo = equipo?.body()?.contenido.orEmpty(),
                        paginasEquipo = equipo?.body()?.let(EstadoDePaginas::tras) ?: EstadoDePaginas(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /** La página siguiente de la bandeja del equipo, al llegar al final. */
    fun cargarMasDelEquipo() {
        val estado = _uiState.value
        if (!estado.paginasEquipo.puedeCargarMas) return
        _uiState.update { it.copy(paginasEquipo = it.paginasEquipo.copy(cargandoMas = true, fallo = false)) }
        viewModelScope.launch {
            val siguiente = pedirSiguiente(estado.paginasEquipo, estado.delEquipo, IncidenciaDTO::id) {
                authRepository.getIncidenciasDelEquipo(pagina = it)
            }
            _uiState.update { it.copy(delEquipo = it.delEquipo + siguiente.nuevos, paginasEquipo = siguiente.estado) }
        }
    }

    /** Explicar una propia. El servidor rechaza una explicación vacía. */
    fun justificar(id: Long, texto: String) = actuar(R.string.incidencias_justificada) {
        authRepository.justificarIncidencia(id, texto.trim())
    }

    fun aceptar(id: Long) = actuar(R.string.incidencias_aceptada) {
        authRepository.resolverIncidencia(id, aceptar = true, comentario = null)
    }

    /** Rechazar exige comentario: es la decisión que hay que poder enseñar motivada. */
    fun rechazar(id: Long, comentario: String) = actuar(R.string.incidencias_rechazada) {
        authRepository.resolverIncidencia(id, aceptar = false, comentario = comentario.trim())
    }

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    /** Recarga entera después, como en horas extra: la incidencia cambia de lista. */
    private fun actuar(mensaje: Int, llamada: suspend () -> Response<IncidenciaDTO>) {
        viewModelScope.launch {
            try {
                val respuesta = llamada()
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(aviso = MensajeUi.de(mensaje)) }
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
