package com.nxtime.app.ui.fichar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.HorasDelDiaDTO
import com.nxtime.app.data.dto.RespuestaAusencia
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Qué tarjeta de la pantalla de inicio se ha abierto. */
enum class TipoDetalle { HOY, SEMANA, MES, VACACIONES }

data class DetalleDeTiempoUiState(
    /** Null = hoja cerrada. */
    val tipo: TipoDetalle? = null,
    val cargando: Boolean = false,
    val dias: List<HorasDelDiaDTO> = emptyList(),
    /** Solo en VACACIONES: las aprobadas que aún no han terminado, por fecha. */
    val proximasAusencias: List<RespuestaAusencia> = emptyList(),
    val error: MensajeUi? = null
)

/**
 * Los datos de la hoja que se abre al tocar una tarjeta de la pantalla de
 * inicio.
 *
 * Aparte de `FicharViewModel` a propósito: aquel lleva el cronómetro y el
 * botón, y cargar gráficos en cada arranque de la app sería pedir datos que
 * casi nadie va a mirar. Esto se pide solo al abrir la hoja, y cada vez: la
 * hoja de hoy no debe enseñar lo de hace una hora.
 */
class DetalleDeTiempoViewModel(
    private val authRepository: AuthRepository,
    private val hoy: () -> LocalDate = { LocalDate.now(ZoneId.of("Europe/Madrid")) }
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleDeTiempoUiState())
    val uiState: StateFlow<DetalleDeTiempoUiState> = _uiState.asStateFlow()

    fun abrir(tipo: TipoDetalle) {
        _uiState.value = DetalleDeTiempoUiState(tipo = tipo, cargando = true)
        viewModelScope.launch {
            try {
                if (tipo == TipoDetalle.VACACIONES) {
                    cargarAusencias()
                } else {
                    cargarDias(tipo)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun cerrar() {
        _uiState.value = DetalleDeTiempoUiState()
    }

    private suspend fun cargarDias(tipo: TipoDetalle) {
        val (desde, hasta) = rango(tipo, hoy())
        val respuesta = authRepository.getHorasPorDia(desde, hasta)
        val cuerpo = respuesta.body()
        if (respuesta.isSuccessful && cuerpo != null) {
            _uiState.update { it.copy(cargando = false, dias = cuerpo) }
        } else {
            _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
        }
    }

    private suspend fun cargarAusencias() {
        val respuesta = authRepository.getMisPeticiones()
        val cuerpo = respuesta.body()
        if (respuesta.isSuccessful && cuerpo != null) {
            _uiState.update { it.copy(cargando = false, proximasAusencias = proximasAprobadas(cuerpo, hoy())) }
        } else {
            _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
        }
    }

    companion object {
        /** HOY es un solo día (para saber cuánto se espera de hoy); la semana empieza en lunes. */
        fun rango(tipo: TipoDetalle, hoy: LocalDate): Pair<LocalDate, LocalDate> = when (tipo) {
            TipoDetalle.HOY, TipoDetalle.VACACIONES -> hoy to hoy
            TipoDetalle.SEMANA -> hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
            TipoDetalle.MES -> hoy.withDayOfMonth(1) to hoy.with(TemporalAdjusters.lastDayOfMonth())
        }

        /** Las aprobadas que acaban hoy o después, la más cercana primero. */
        fun proximasAprobadas(peticiones: List<RespuestaAusencia>, hoy: LocalDate): List<RespuestaAusencia> =
            peticiones
                .filter { it.estado == EstadoAusencia.APROBADA && !LocalDate.parse(it.fechaFin).isBefore(hoy) }
                .sortedBy { it.fechaInicio }

        /**
         * Los días con la jornada abierta sumada al día de hoy.
         *
         * El servidor solo cuenta las cerradas; sin esto, quien lleva tres
         * horas fichado vería la barra de hoy vacía mientras la tarjeta de
         * al lado dice "3h 00m".
         */
        fun conJornadaEnCurso(dias: List<HorasDelDiaDTO>, hoy: LocalDate, minutosEnCurso: Long): List<HorasDelDiaDTO> {
            if (minutosEnCurso <= 0) return dias
            val clave = hoy.toString()
            return dias.map { if (it.fecha == clave) it.copy(minutosTrabajados = it.minutosTrabajados + minutosEnCurso) else it }
        }
    }
}
