package com.nxtime.app.ui.reparto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.ImputacionesDTO
import com.nxtime.app.data.dto.LineaReparto
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepartoUiState(
    val cargando: Boolean = true,
    val imputaciones: ImputacionesDTO? = null,
    /** Lo que se está repartiendo ahora mismo: minutos por proyecto. */
    val minutos: Map<Long, Long> = emptyMap(),
    val motivo: String = "",
    val enviando: Boolean = false,
    /** Se aplicó al momento. */
    val aplicado: Boolean = false,
    /** Se creó una solicitud: la tiene que aprobar un gestor. */
    val pedido: Boolean = false,
    val error: MensajeUi? = null
) {
    val totalMinutos: Long get() = minutos.values.sum()
    val netoMinutos: Long get() = imputaciones?.netoMinutos ?: 0
    val diferencia: Long get() = totalMinutos - netoMinutos

    /**
     * Lo que va a pasar al enviar, que es lo que la pantalla anuncia ANTES de
     * pulsar: el mismo reparto puede aplicarse solo o acabar en manos de un
     * gestor, y descubrirlo después es lo que hace dudar de si se ha guardado.
     */
    val necesitaAprobacion: Boolean
        get() = diferencia > 0 || imputaciones?.repartoLibre == false

    val puedeEnviar: Boolean
        get() = !enviando && totalMinutos > 0 && diferencia >= 0 &&
            imputaciones?.solicitudPendienteId == null &&
            (!necesitaAprobacion || motivo.isNotBlank())
}

/**
 * Repartir las horas de una jornada entre proyectos (ADR 017).
 *
 * La app no decide si el reparto se aplica o se pide: lo dice el servidor con
 * el código de respuesta (200 aplicado, 202 pendiente de aprobar). Aquí solo
 * se anticipa para avisar, con `repartoLibre` y el total.
 */
class RepartoViewModel(
    private val fichajeId: Long,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepartoUiState())
    val uiState: StateFlow<RepartoUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getImputaciones(fichajeId)
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            imputaciones = cuerpo,
                            minutos = cuerpo.lineas.associate { linea -> linea.proyectoId to linea.minutos }
                        )
                    }
                } else {
                    _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun cambiarMinutos(proyectoId: Long, minutos: Long) {
        _uiState.update { it.copy(minutos = it.minutos + (proyectoId to minutos.coerceAtLeast(0)), error = null) }
    }

    fun cambiarMotivo(texto: String) = _uiState.update { it.copy(motivo = texto) }

    /** Pone todo el neto en un proyecto: el caso más común es no repartir nada. */
    fun todoA(proyectoId: Long) {
        _uiState.update { it.copy(minutos = mapOf(proyectoId to it.netoMinutos), error = null) }
    }

    fun enviar() {
        val estado = _uiState.value
        if (!estado.puedeEnviar) return
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.repartir(
                    fichajeId,
                    estado.minutos.filterValues { it > 0 }.map { (proyecto, minutos) -> LineaReparto(proyecto, minutos) },
                    estado.motivo.takeIf { it.isNotBlank() }
                )
                if (respuesta.isSuccessful) {
                    // 200 = aplicado; 202 = lo tiene que aprobar un gestor.
                    val aplicado = respuesta.code() == 200
                    _uiState.update { it.copy(enviando = false, aplicado = aplicado, pedido = !aplicado) }
                } else {
                    _uiState.update { it.copy(enviando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(enviando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    companion object {
        /** Para los textos de la pantalla: qué falta o qué sobra. */
        val SIN_DIFERENCIA = 0L
    }
}
