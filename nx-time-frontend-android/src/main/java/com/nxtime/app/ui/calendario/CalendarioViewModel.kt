package com.nxtime.app.ui.calendario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.AusenciaCalendarioDTO
import com.nxtime.app.data.dto.FestivoDTO
import com.nxtime.app.data.dto.FestivoRequest
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarioUiState(
    val cargando: Boolean = false,
    val periodo: YearMonth = YearMonth.now(DateFormats.ZONA_ESPANA),
    val festivos: List<FestivoDTO> = emptyList(),
    val ausencias: List<AusenciaCalendarioDTO> = emptyList(),
    /** Si la respuesta trae gente además de uno mismo. Lo dice el servidor. */
    val incluyeEquipo: Boolean = false,
    /** Si el interruptor está pulsado. Lo dice la pantalla. */
    val verEquipo: Boolean = false,
    val diaSeleccionado: LocalDate? = null,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
) {
    /**
     * Los festivos indexados por día, para que pintar una celda sea una
     * búsqueda y no un recorrido de la lista entera.
     *
     * Va aquí y no en la pantalla porque se recalcularía en cada
     * recomposición: una rejilla de 42 celdas por 12 festivos son 504
     * comparaciones cada vez que se mueve un dedo.
     */
    val festivoPorDia: Map<LocalDate, FestivoDTO> =
        festivos.mapNotNull { festivo ->
            DateFormats.fechaIso(festivo.fecha)?.let { it to festivo }
        }.toMap()

    /** Las ausencias que tocan un día concreto, ya expandidas por fecha. */
    val ausenciasPorDia: Map<LocalDate, List<AusenciaCalendarioDTO>> = buildMap {
        val primero = periodo.atDay(1)
        val ultimo = periodo.atEndOfMonth()
        ausencias.forEach { ausencia ->
            val desde = DateFormats.fechaIso(ausencia.fechaInicio) ?: return@forEach
            val hasta = DateFormats.fechaIso(ausencia.fechaFin) ?: return@forEach
            // Se recorta al mes en la app y no en el servidor: allí las
            // fechas llegan completas a propósito, para poder saber que
            // una ausencia viene de antes o sigue después del mes.
            var dia = maxOf(desde, primero)
            val fin = minOf(hasta, ultimo)
            while (!dia.isAfter(fin)) {
                merge(dia, listOf(ausencia)) { viejas, nuevas -> viejas + nuevas }
                dia = dia.plusDays(1)
            }
        }
    }
}

/**
 * El calendario laboral del mes (Fase C).
 *
 * Carga un mes cada vez y no el año entero: el año son doce veces más
 * ausencias para pintar treinta días, y moverse de mes es justo el gesto
 * que tiene que ir rápido.
 *
 * Cambiar de mes **no borra lo que hay en pantalla** mientras llega lo
 * nuevo. Vaciar la rejilla en cada flecha haría parpadear el calendario
 * entero en cada toque; así el mes anterior se queda hasta que el nuevo
 * está listo, que es lo que hace que pasar meses se sienta continuo.
 */
class CalendarioViewModel(
    private val authRepository: AuthRepository,
    /**
     * El mes con el que se abre. Es el actual salvo en los tests, que
     * necesitan un mes fijo: sin esto habría que reescribirlos cada vez
     * que cambia el calendario real, o comprobar el reparto por días
     * contra un mes distinto en cada ejecución.
     */
    periodoInicial: YearMonth = YearMonth.now(DateFormats.ZONA_ESPANA)
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarioUiState(periodo = periodoInicial))
    val uiState: StateFlow<CalendarioUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        val estado = _uiState.value
        _uiState.update { it.copy(cargando = true, error = null) }

        viewModelScope.launch {
            try {
                val respuesta = authRepository.getCalendario(
                    anio = estado.periodo.year,
                    mes = estado.periodo.monthValue,
                    equipo = estado.verEquipo
                )
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            festivos = cuerpo.festivos,
                            ausencias = cuerpo.ausencias,
                            incluyeEquipo = cuerpo.incluyeEquipo,
                            error = null
                        )
                    }
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

    fun mesAnterior() = irA(_uiState.value.periodo.minusMonths(1))

    fun mesSiguiente() = irA(_uiState.value.periodo.plusMonths(1))

    /** Vuelve al mes de hoy y deja el día de hoy seleccionado. */
    fun irAHoy() {
        val hoy = LocalDate.now(DateFormats.ZONA_ESPANA)
        _uiState.update { it.copy(periodo = YearMonth.from(hoy), diaSeleccionado = hoy) }
        cargar()
    }

    private fun irA(periodo: YearMonth) {
        // La selección se suelta al cambiar de mes: un día marcado que ya
        // no está en la rejilla no se ve, pero seguiría mandando en el
        // detalle de abajo.
        _uiState.update { it.copy(periodo = periodo, diaSeleccionado = null) }
        cargar()
    }

    /** Tocar el día ya seleccionado lo deselecciona. */
    fun seleccionarDia(dia: LocalDate) {
        _uiState.update {
            it.copy(diaSeleccionado = if (it.diaSeleccionado == dia) null else dia)
        }
    }

    fun alternarEquipo() {
        _uiState.update { it.copy(verEquipo = !it.verEquipo) }
        cargar()
    }

    fun crearFestivo(fecha: LocalDate, descripcion: String, ambito: AmbitoFestivo) {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.crearFestivo(
                    FestivoRequest(
                        fecha = fecha.toString(),
                        descripcion = descripcion.trim(),
                        ambito = ambito.name
                    )
                )
                if (respuesta.isSuccessful) {
                    // Se recarga el mes en vez de insertar el festivo en
                    // la lista local: la respuesta trae el festivo, pero
                    // no los días hábiles que el servidor acaba de
                    // recalcular, y esta pantalla no es la única que los
                    // usa.
                    _uiState.update { it.copy(aviso = MensajeUi.de(R.string.calendario_festivo_creado)) }
                    cargar()
                } else {
                    _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun borrarFestivo(id: Long) {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.borrarFestivo(id)
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(aviso = MensajeUi.de(R.string.calendario_festivo_borrado)) }
                    cargar()
                } else {
                    _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /** El aviso ya se ha enseñado: que no vuelva a salir al girar el móvil. */
    fun avisoMostrado() {
        _uiState.update { it.copy(aviso = null) }
    }
}
