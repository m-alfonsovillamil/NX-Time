package com.nxtime.app.ui.horasextra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.BolsaHorasExtraDTO
import com.nxtime.app.data.dto.HorasExtraDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.util.MensajeUi
import com.nxtime.app.ui.util.Permisos
import com.nxtime.app.ui.util.Rol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HorasExtraUiState(
    val cargando: Boolean = false,
    /** La bolsa anual del art. 35.2 ET. Null mientras no ha llegado. */
    val bolsa: BolsaHorasExtraDTO? = null,
    /** Los excesos detectados en mis propios fichajes. */
    val mios: List<HorasExtraDTO> = emptyList(),
    /** Los de toda la empresa. Vacío si no tengo que revisar nada. */
    val delEquipo: List<HorasExtraDTO> = emptyList(),
    val puedeRevisar: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * Horas extra: lo detectado en mis jornadas y, si me toca, lo de la
 * empresa (Fase F).
 *
 * Dos listas en una pantalla por la misma razón que en correcciones: son
 * las dos caras de lo mismo. La diferencia es que aquí la segunda
 * **puede no existir** — un empleado solo ve la suya —, y por eso el
 * estado lleva `puedeRevisar`: sin él la pantalla tendría que deducirlo
 * de que la lista viene vacía, y una lista vacía también significa "no
 * hay nada que revisar", que no es lo mismo.
 *
 * La app **no decide** quién puede revisar qué: el rol solo evita pedir
 * un endpoint que devolvería 403, y quien manda es el servidor — que
 * además rechaza revisar los avisos propios aunque el rol dé el permiso,
 * cosa que aquí ni se intenta reproducir.
 */
class HorasExtraViewModel(
    private val authRepository: AuthRepository,
    sessionManager: SessionManager
) : ViewModel() {

    private val rol = Rol.de(sessionManager.fetchUserRole())

    private val _uiState = MutableStateFlow(
        HorasExtraUiState(puedeRevisar = Permisos.puedeRevisarHorasExtra(rol))
    )
    val uiState: StateFlow<HorasExtraUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val mios = authRepository.getMisHorasExtra()
                val bolsa = authRepository.getBolsaHorasExtra()
                // Solo se pide si el rol lo permite: pedirlo igualmente
                // daría un 403 previsible y ensuciaría el log del
                // servidor con errores que no lo son.
                val equipo = if (Permisos.puedeRevisarHorasExtra(rol)) {
                    authRepository.getHorasExtraDelEquipo()
                } else {
                    null
                }

                if (!mios.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(mios))
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        cargando = false,
                        mios = mios.body().orEmpty(),
                        // Si la bolsa falla, la pantalla se ve igual sin
                        // la barra: lo que hay que mirar son los avisos.
                        bolsa = bolsa.body(),
                        delEquipo = equipo?.body().orEmpty(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /** Sí eran horas extra: descuentan de la bolsa anual. */
    fun aceptar(avisoId: Long) = revisar(avisoId, aceptar = true, justificacion = null)

    /** No lo eran (intensiva pactada, turno mal fichado): se archiva. */
    fun justificar(avisoId: Long, justificacion: String) =
        revisar(avisoId, aceptar = false, justificacion = justificacion)

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    /**
     * Recarga entera después de revisar, en vez de retocar la lista en
     * local: aceptar un aviso cambia también la bolsa, y dejar la barra
     * con la cifra vieja es justo el tipo de desfase que hace dudar del
     * número.
     */
    private fun revisar(avisoId: Long, aceptar: Boolean, justificacion: String?) {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.revisarHorasExtra(avisoId, aceptar, justificacion)
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            aviso = MensajeUi.de(
                                if (aceptar) R.string.horas_extra_aceptada
                                else R.string.horas_extra_justificada
                            )
                        )
                    }
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
