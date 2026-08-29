package com.nxtime.app.ui.fichar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.TipoFichaje
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** En qué punto de la jornada está el empleado. */
enum class EstadoJornada { SIN_JORNADA, TRABAJANDO, EN_PAUSA }

data class FicharUiState(
    val cargando: Boolean = true,
    val estado: EstadoJornada = EstadoJornada.SIN_JORNADA,
    val registro: Registro? = null,
    val nombreUsuario: String = "",
    val esRolDeGestion: Boolean = false,
    val error: MensajeUi? = null
)

/**
 * Pantalla principal: fichar entrada, pausa y salida.
 *
 * Aquí estaba el fallo de experiencia de uso más caro del proyecto. El
 * mensaje de error se componía así:
 *
 *     "Error al registrar fichaje: ${response.code()} ${response.message()}"
 *
 * y `response.message()` en Retrofit es la frase del estado HTTP, no el
 * cuerpo de la respuesta. Al pulsar "fichar" dos veces, el usuario leía
 * **"Error al registrar fichaje: 409 Conflict"** en lugar del
 * "Ya hay una jornada activa." que el backend devuelve en el `detail`
 * del ProblemDetail. Todo el trabajo de la Fase 2 del backend (RFC 7807)
 * moría aquí. Ahora los errores pasan por {@link ApiErrorParser}.
 */
class FicharViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FicharUiState())
    val uiState: StateFlow<FicharUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                nombreUsuario = sessionManager.fetchUserName().orEmpty(),
                esRolDeGestion = sessionManager.fetchUserRole() in ROLES_DE_GESTION
            )
        }
        comprobarEstadoJornada()
    }

    fun comprobarEstadoJornada() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getRegistroActivo()
                if (respuesta.isSuccessful) {
                    aplicarRegistro(respuesta.body())
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
     * Botón central. Inicia la jornada si no hay ninguna y la termina si
     * la hay.
     *
     * La comprobación de "no puedes terminar estando en pausa" se hace
     * aquí y no se manda al servidor: es una regla que el propio estado
     * de la pantalla ya conoce, y así el usuario recibe la respuesta al
     * instante en vez de tras una ida y vuelta.
     */
    fun pulsarBotonPrincipal() {
        val estado = _uiState.value.estado
        if (estado == EstadoJornada.EN_PAUSA) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.fichar_reanuda_antes)) }
            return
        }
        val tipo = if (estado == EstadoJornada.SIN_JORNADA) TipoFichaje.INICIO else TipoFichaje.FIN
        registrarFichaje(tipo)
    }

    fun pulsarBotonPausa() {
        val estado = _uiState.value.estado
        if (estado == EstadoJornada.SIN_JORNADA) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.fichar_inicia_antes)) }
            return
        }
        val tipo = if (estado == EstadoJornada.EN_PAUSA) {
            TipoFichaje.PAUSA_FIN
        } else {
            TipoFichaje.PAUSA_INICIO
        }
        registrarFichaje(tipo)
    }

    private fun registrarFichaje(tipo: TipoFichaje) {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.registrarFichaje(PeticionFichaje(tipo))
                if (respuesta.isSuccessful) {
                    aplicarRegistro(respuesta.body())
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
     * Traduce la respuesta del backend al estado de la pantalla.
     *
     * Un registro con hora de salida ya no es una jornada activa: el
     * backend responde con el fichaje recién cerrado, no con null.
     */
    private fun aplicarRegistro(registro: Registro?) {
        val estado = when {
            registro == null || registro.horaSalida != null -> EstadoJornada.SIN_JORNADA
            registro.enPausa -> EstadoJornada.EN_PAUSA
            else -> EstadoJornada.TRABAJANDO
        }
        _uiState.update {
            it.copy(
                cargando = false,
                estado = estado,
                registro = if (estado == EstadoJornada.SIN_JORNADA) null else registro,
                error = null
            )
        }
    }

    fun descartarError() {
        _uiState.update { it.copy(error = null) }
    }

    fun cerrarSesion() {
        sessionManager.clearAuthData()
    }

    companion object {
        /**
         * GESTOR, RRHH y ADMIN ven el panel de gestión. Desde la Fase 4
         * del backend no basta con mirar si el rol es GESTOR: una
         * empresa recién creada solo tiene un ADMIN, que es quien la
         * funda, y dejarlo fuera lo cerraría fuera de su propio panel.
         */
        private val ROLES_DE_GESTION = setOf("GESTOR", "RRHH", "ADMIN")
    }
}
