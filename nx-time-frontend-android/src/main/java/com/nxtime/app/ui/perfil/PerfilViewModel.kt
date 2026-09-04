package com.nxtime.app.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.PerfilDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class PerfilUiState(
    val cargando: Boolean = false,
    val perfil: PerfilDTO? = null,
    val editando: Boolean = false,
    val guardando: Boolean = false,
    // Campos del formulario, separados del perfil cargado: mientras se
    // edita, la pantalla enseña lo que hay escrito, no lo que hay
    // guardado.
    val nombre: String = "",
    val apellidos: String = "",
    val fechaNacimiento: String = "",
    val puesto: String = "",
    val errorFormulario: MensajeUi? = null,
    val error: MensajeUi? = null
)

/**
 * El perfil propio: verlo y editar los datos personales.
 *
 * Lo que NO está aquí es tan importante como lo que sí: rol, jornada,
 * vacaciones y departamento se ven pero no se editan, porque el backend
 * no los acepta en `PATCH /perfil`. Ofrecer el campo y comerse un 400
 * sería peor que no ofrecerlo.
 */
class PerfilViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PerfilUiState())
    val uiState: StateFlow<PerfilUiState> = _uiState.asStateFlow()

    /**
     * **Sin `init { cargar() }`**, igual que `AvisosViewModel` y por el
     * mismo motivo: `NxTimeNavHost` lo construye para tener las
     * iniciales del avatar, así que existe también mientras se está en
     * el login, donde una petición autenticada saldría sin token y
     * dejaría un 401 pegado al estado que la pantalla enseñaría después.
     * Quien lo usa decide cuándo pedir datos.
     */
    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getMiPerfil()
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update { it.copy(cargando = false, perfil = cuerpo, error = null) }
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

    /** Abre el formulario precargado con lo que hay guardado. */
    fun empezarAEditar() {
        val perfil = _uiState.value.perfil ?: return
        _uiState.update {
            it.copy(
                editando = true,
                nombre = perfil.nombre,
                apellidos = perfil.apellidos.orEmpty(),
                fechaNacimiento = perfil.fechaNacimiento.orEmpty(),
                puesto = perfil.puesto.orEmpty(),
                errorFormulario = null
            )
        }
    }

    fun cancelarEdicion() = _uiState.update { it.copy(editando = false, errorFormulario = null) }

    fun onNombreCambia(valor: String) = _uiState.update { it.copy(nombre = valor, errorFormulario = null) }
    fun onApellidosCambia(valor: String) = _uiState.update { it.copy(apellidos = valor, errorFormulario = null) }
    fun onPuestoCambia(valor: String) = _uiState.update { it.copy(puesto = valor, errorFormulario = null) }
    fun onFechaNacimientoCambia(valor: String) =
        _uiState.update { it.copy(fechaNacimiento = valor, errorFormulario = null) }

    /**
     * Cierra la sesión.
     *
     * Vive aquí desde la Fase B y ya no en `FicharViewModel`: el botón
     * está en esta pantalla, y dejar el método donde estaba el menú
     * viejo habría sido código muerto con test propio.
     */
    fun cerrarSesion() = sessionManager.clearAuthData()

    /** Vacía el estado al cerrar sesión, para no enseñar el perfil del anterior. */
    fun limpiar() {
        _uiState.value = PerfilUiState(cargando = false)
    }

    fun guardar() {
        val estado = _uiState.value

        if (estado.nombre.isBlank()) {
            _uiState.update { it.copy(errorFormulario = MensajeUi.Recurso(R.string.perfil_nombre_obligatorio)) }
            return
        }

        val fecha = estado.fechaNacimiento.trim()
        if (fecha.isNotEmpty()) {
            val comoFecha = try {
                LocalDate.parse(fecha)
            } catch (_: DateTimeParseException) {
                null
            }
            if (comoFecha == null) {
                _uiState.update { it.copy(errorFormulario = MensajeUi.Recurso(R.string.perfil_fecha_invalida)) }
                return
            }
            // El backend también lo rechaza (@Past), pero un viaje de
            // ida y vuelta para decir que no se nace mañana sobra.
            if (!comoFecha.isBefore(LocalDate.now())) {
                _uiState.update { it.copy(errorFormulario = MensajeUi.Recurso(R.string.perfil_fecha_futura)) }
                return
            }
        }

        _uiState.update { it.copy(guardando = true, errorFormulario = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.actualizarMiPerfil(
                    nombre = estado.nombre.trim(),
                    apellidos = estado.apellidos.trim(),
                    // Los de texto se pueden vaciar: la cadena vacía es
                    // "bórralo" y el backend la distingue de null.
                    //
                    // La fecha NO: es un LocalDate y no tiene cadena
                    // vacía, así que dejar el campo en blanco significa
                    // "no la toques" y no "quítamela". Se puede corregir
                    // una fecha mal puesta, pero no borrarla. Si algún
                    // día hace falta, el backend necesita distinguir los
                    // dos casos y eso no se resuelve desde aquí.
                    fechaNacimiento = fecha.ifEmpty { null },
                    puesto = estado.puesto.trim()
                )
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update {
                        it.copy(guardando = false, editando = false, perfil = cuerpo, errorFormulario = null)
                    }
                } else {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(guardando = false, errorFormulario = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }
}
