package com.nxtime.app.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.RegistroEquipoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistorialEquipoUiState(
    val cargando: Boolean = true,
    val empleados: List<EmpleadoSimpleDTO> = emptyList(),
    /** null = sin filtro, se ven todos. */
    val empleadoFiltrado: EmpleadoSimpleDTO? = null,
    val registros: List<RegistroEquipoDTO> = emptyList(),
    val error: MensajeUi? = null
) {
    /**
     * Lo que se pinta. El filtro se aplica aquí, sobre la lista completa
     * que ya está en memoria, y no con otra llamada al servidor: cambiar
     * de empleado en el desplegable es instantáneo y no gasta red.
     *
     * Se compara por nombre porque es el único dato común: el historial
     * del equipo trae a cada empleado como `SimpleUserDTO`, que solo
     * lleva `nombre`, mientras que la lista de empleados sí trae id y
     * correo. Dos empleados homónimos en la misma empresa mezclarían por
     * tanto sus jornadas al filtrar; arreglarlo de verdad pide añadir el
     * id a `SimpleUserDTO` en el backend, que es un cambio de contrato y
     * no cabe en esta migración.
     */
    val registrosVisibles: List<RegistroEquipoDTO>
        get() = empleadoFiltrado
            ?.let { filtro -> registros.filter { it.usuario.nombre == filtro.nombre } }
            ?: registros
}

/** Historial de fichajes de todo el equipo, con filtro por empleado. */
class HistorialEquipoViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialEquipoUiState())
    val uiState: StateFlow<HistorialEquipoUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                // Las dos peticiones son independientes, así que van a
                // la vez: la pantalla tarda lo que la más lenta y no la
                // suma de ambas.
                val empleadosDiferido = async { authRepository.getMisEmpleados() }
                val historialDiferido = async { authRepository.getHistorialEquipo() }

                val empleados = empleadosDiferido.await()
                val historial = historialDiferido.await()

                if (empleados.isSuccessful && historial.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            empleados = empleados.body().orEmpty(),
                            registros = historial.body().orEmpty(),
                            error = null
                        )
                    }
                } else {
                    // Se informa del que falló de verdad, en lugar del
                    // "Error al cargar los datos" que ponía antes sin
                    // mirar cuál de las dos respuestas venía mal.
                    val fallida = if (!empleados.isSuccessful) empleados else historial
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(fallida))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    fun onFiltroCambia(empleado: EmpleadoSimpleDTO?) {
        _uiState.update { it.copy(empleadoFiltrado = empleado) }
    }
}
