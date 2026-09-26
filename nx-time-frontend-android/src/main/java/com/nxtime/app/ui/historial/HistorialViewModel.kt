package com.nxtime.app.ui.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.network.Paginas
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.EstadoDePaginas
import com.nxtime.app.ui.util.MensajeUi
import com.nxtime.app.ui.util.pedirSiguiente
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class HistorialUiState(
    val cargando: Boolean = true,
    val registros: List<Registro> = emptyList(),
    /** Solo en [PeriodoHistorial.Recientes]: un periodo se carga entero. */
    val paginas: EstadoDePaginas = EstadoDePaginas(),
    val periodo: PeriodoHistorial = PeriodoHistorial.Recientes,
    /** Los dos días del periodo, para la cabecera. Null en [PeriodoHistorial.Recientes]. */
    val rango: Pair<LocalDate, LocalDate>? = null,
    val error: MensajeUi? = null
) {
    val segundosNetos: Long get() = PeriodoHistorial.segundosNetos(registros)
}

/**
 * Historial de fichajes del propio empleado.
 *
 * El estado vacío deja de confundirse con el de error: antes, una lista
 * sin fichajes y un fallo de red producían exactamente la misma pantalla
 * en blanco, y solo el segundo mostraba además un Toast que se iba solo.
 */
class HistorialViewModel(
    private val authRepository: AuthRepository,
    /** "Hoy" en España. Se inyecta para poder probar la semana y el mes. */
    private val hoy: () -> LocalDate = { LocalDate.now(DateFormats.ZONA_ESPANA) }
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialUiState())
    val uiState: StateFlow<HistorialUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    /** Cambia el periodo y recarga. Se conserva al volver a la pantalla (ON_RESUME). */
    fun cambiarPeriodo(periodo: PeriodoHistorial) {
        _uiState.update { it.copy(periodo = periodo, registros = emptyList()) }
        cargar()
    }

    fun cargar() {
        // "Esta semana" se recalcula en cada carga: la pantalla puede quedarse
        // abierta de un lunes a otro.
        val rango = _uiState.value.periodo.rango(hoy())
        _uiState.update { it.copy(cargando = true, error = null, rango = rango) }
        viewModelScope.launch {
            try {
                if (rango == null) {
                    cargarRecientes()
                } else {
                    cargarPeriodo(rango)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    /** Los recientes, por páginas: la siguiente llega al bajar ([cargarMas]). */
    private suspend fun cargarRecientes() {
        val respuesta = authRepository.getHistorial()
        val cuerpo = respuesta.body()
        if (respuesta.isSuccessful && cuerpo != null) {
            _uiState.update {
                it.copy(
                    cargando = false,
                    registros = cuerpo.contenido,
                    paginas = EstadoDePaginas.tras(cuerpo),
                    error = null
                )
            }
        } else {
            _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
        }
    }

    /**
     * Un periodo, ENTERO: la cabecera suma sus horas, y sumar solo la
     * primera página daría un total falso sin avisar. El servidor limita el
     * periodo a un año, así que son pocas peticiones de 200.
     */
    private suspend fun cargarPeriodo(rango: Pair<LocalDate, LocalDate>) {
        val respuesta = Paginas.todas { pagina ->
            authRepository.getHistorial(rango.first, rango.second, pagina, Paginas.TAMANO_MAXIMO)
        }
        val cuerpo = respuesta.body()
        if (respuesta.isSuccessful && cuerpo != null) {
            _uiState.update {
                it.copy(cargando = false, registros = cuerpo, paginas = EstadoDePaginas(), error = null)
            }
        } else {
            _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
        }
    }

    /** La página siguiente de los recientes, al llegar al final de la lista. */
    fun cargarMas() {
        val estado = _uiState.value
        if (!estado.paginas.puedeCargarMas) return
        _uiState.update { it.copy(paginas = it.paginas.copy(cargandoMas = true, fallo = false)) }
        viewModelScope.launch {
            val siguiente = pedirSiguiente(estado.paginas, estado.registros, Registro::id) {
                authRepository.getHistorial(pagina = it)
            }
            _uiState.update { it.copy(registros = it.registros + siguiente.nuevos, paginas = siguiente.estado) }
        }
    }

}
