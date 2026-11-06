package com.nxtime.app.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.RegistroEquipoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.EstadoDePaginas
import com.nxtime.app.ui.util.MensajeUi
import com.nxtime.app.ui.util.pedirSiguiente
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
    val paginas: EstadoDePaginas = EstadoDePaginas(),
    val error: MensajeUi? = null,
    /**
     * Lo que falló al dar por buena una jornada. Aparte de [error], que
     * sustituye la lista entera por la pantalla de error: un 403 al revisar
     * una jornada no puede esconder todas las demás.
     */
    val errorAlRevisar: MensajeUi? = null,
    /** Las jornadas que se están dando por buenas: su botón no se puede volver a pulsar. */
    val revisando: Set<Long> = emptySet()
) {
    /**
     * Lo que se pinta. El filtro se aplica aquí, sobre lo que ya está en
     * memoria, y no con otra llamada al servidor: cambiar de empleado en el
     * desplegable es instantáneo y no gasta red. Desde la Fase A7 lo cargado
     * es por páginas: si el filtro deja la pantalla corta, el final de la
     * lista sigue a la vista y va pidiendo más.
     *
     * Se compara por id. Hasta octubre de 2026 se comparaba por nombre,
     * porque era lo único que traía cada jornada, y dos personas con el
     * mismo nombre en la empresa mezclaban sus jornadas al filtrar. El nombre
     * solo queda de respaldo para una jornada que llegue sin `usuarioId`.
     */
    val registrosVisibles: List<RegistroEquipoDTO>
        get() = empleadoFiltrado
            ?.let { filtro ->
                registros.filter { registro ->
                    registro.usuarioId?.let { it == filtro.id } ?: (registro.usuario.nombre == filtro.nombre)
                }
            }
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
                            registros = historial.body()?.contenido.orEmpty(),
                            paginas = historial.body()?.let(EstadoDePaginas::tras) ?: EstadoDePaginas(),
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

    /** La página siguiente, al llegar al final de la lista. */
    fun cargarMas() {
        val estado = _uiState.value
        if (!estado.paginas.puedeCargarMas) return
        _uiState.update { it.copy(paginas = it.paginas.copy(cargandoMas = true, fallo = false)) }
        viewModelScope.launch {
            val siguiente = pedirSiguiente(estado.paginas, estado.registros, RegistroEquipoDTO::id) {
                authRepository.getHistorialEquipo(it)
            }
            _uiState.update { it.copy(registros = it.registros + siguiente.nuevos, paginas = siguiente.estado) }
        }
    }

    /**
     * Quita la marca «a revisar: llegó tarde» de una jornada que llegó sin
     * conexión pasadas 72 h (ADR 054), como «Dar por bueno» en la web. No
     * cambia las horas: si están mal, se corrigen.
     *
     * Al acabar bien se quita la marca de la tarjeta sin recargar la lista,
     * que puede llevar varias páginas cargadas.
     */
    fun darPorBueno(fichajeId: Long) {
        if (fichajeId in _uiState.value.revisando) return
        _uiState.update { it.copy(revisando = it.revisando + fichajeId, errorAlRevisar = null) }
        viewModelScope.launch {
            val error = try {
                val respuesta = authRepository.darPorBueno(fichajeId)
                if (respuesta.isSuccessful) null else ApiErrorParser.mensajeDe(respuesta)
            } catch (e: Exception) {
                ApiErrorParser.mensajeDeRed(e)
            }
            _uiState.update { estado ->
                estado.copy(
                    revisando = estado.revisando - fichajeId,
                    errorAlRevisar = error,
                    registros = if (error != null) estado.registros else estado.registros.map {
                        if (it.id == fichajeId) it.copy(revisarPorLlegarTarde = false) else it
                    }
                )
            }
        }
    }

    fun descartarErrorAlRevisar() {
        _uiState.update { it.copy(errorAlRevisar = null) }
    }

    fun onFiltroCambia(empleado: EmpleadoSimpleDTO?) {
        _uiState.update { it.copy(empleadoFiltrado = empleado) }
    }
}
