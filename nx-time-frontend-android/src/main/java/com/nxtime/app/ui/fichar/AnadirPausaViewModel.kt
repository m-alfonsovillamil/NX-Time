package com.nxtime.app.ui.fichar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.PausaAnadidaDTO
import com.nxtime.app.data.dto.PausaAnadidaRequest
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

data class AnadirPausaUiState(
    val fecha: LocalDate = LocalDate.now(DateFormats.ZONA_ESPANA),

    /** La jornada sigue abierta: se aplica en el acto, y se puede deshacer. */
    val jornadaAbierta: Boolean = true,

    // 14:00-15:00 de partida: el caso que motivó esto es la comida.
    val horaInicio: Int = 14,
    val minutoInicio: Int = 0,
    val horaFin: Int = 15,
    val minutoFin: Int = 0,

    /** Una pausa de un turno de noche puede acabar pasada la medianoche. */
    val finEsOtroDia: Boolean = false,

    val motivo: String = "",

    /**
     * Las pausas que ya se añadieron a esta jornada.
     *
     * Están a la vista a propósito: es la única defensa contra añadir dos
     * veces la misma comida. El servidor rechaza el solape entre pausas
     * añadidas, pero no puede ver el solape con una pausa fichada con el
     * botón, porque de esas no se guardó el intervalo.
     */
    val pausas: List<PausaAnadidaDTO> = emptyList(),

    val enviando: Boolean = false,
    val hecho: Boolean = false,

    /**
     * Si el tiempo trabajado YA ha cambiado, o la pausa quedó pedida.
     * Lo dice el servidor: la pantalla no se inventa lo que ha pasado.
     */
    val aplicada: Boolean = false,

    val error: MensajeUi? = null
) {
    /**
     * Lo que se le anuncia a la persona ANTES de enviar. Es la misma regla
     * que aplica el servidor (abierta, o cerrada que empezó hoy), repetida
     * aquí solo para poner el texto correcto. **Si discrepan, manda el
     * servidor**, y el mensaje final se basa en lo que él respondió.
     */
    val vaDirecta: Boolean
        get() = jornadaAbierta || fecha == LocalDate.now(DateFormats.ZONA_ESPANA)
}

/**
 * Añadir a mano una pausa que no se fichó (ADR 015).
 *
 * Mismo patrón que [com.nxtime.app.ui.auditoria.CorregirFichajeViewModel]:
 * las horas se recogen en hora española y se convierten a instante UTC antes
 * de mandarlas, y el motivo es obligatorio antes de dejar enviar.
 */
class AnadirPausaViewModel(
    private val fichajeId: Long,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnadirPausaUiState())
    val uiState: StateFlow<AnadirPausaUiState> = _uiState.asStateFlow()

    fun precargar(entradaIso: String?, salidaIso: String?) {
        val fecha = DateFormats.fechaLocal(entradaIso) ?: return
        _uiState.update { it.copy(fecha = fecha, jornadaAbierta = salidaIso == null) }
        cargarPausas()
    }

    fun cambiarInicio(hora: Int, minuto: Int) =
        _uiState.update { it.copy(horaInicio = hora, minutoInicio = minuto, error = null) }

    fun cambiarFin(hora: Int, minuto: Int) =
        _uiState.update { it.copy(horaFin = hora, minutoFin = minuto, error = null) }

    fun cambiarFinEsOtroDia(esOtroDia: Boolean) =
        _uiState.update { it.copy(finEsOtroDia = esOtroDia, error = null) }

    fun cambiarMotivo(motivo: String) =
        _uiState.update { it.copy(motivo = motivo, error = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    fun guardar() {
        val estado = _uiState.value

        if (estado.motivo.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.pausa_motivo_vacio)) }
            return
        }

        val inicio = DateFormats.aInstanteIso(estado.fecha, estado.horaInicio, estado.minutoInicio)
        val diaDeFin = if (estado.finEsOtroDia) estado.fecha.plusDays(1) else estado.fecha
        val fin = DateFormats.aInstanteIso(diaDeFin, estado.horaFin, estado.minutoFin)

        // Se mira aquí porque la pantalla ya tiene las dos horas delante; el
        // resto (que quepa en la jornada, que no solape) lo sabe el servidor.
        if (fin <= inicio) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.pausa_fin_anterior)) }
            return
        }

        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.anadirPausa(
                    fichajeId, PausaAnadidaRequest(inicio, fin, estado.motivo.trim())
                )
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update {
                        it.copy(enviando = false, hecho = true, aplicada = cuerpo.aplicada)
                    }
                } else {
                    _uiState.update {
                        it.copy(enviando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(enviando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /** Solo se ofrece con la jornada abierta; cerrada, el servidor lo rechazaría. */
    fun deshacer(pausaId: Long) {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.deshacerPausa(fichajeId, pausaId)
                if (respuesta.isSuccessful) {
                    cargarPausas()
                } else {
                    _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /*
     * Un fallo aquí no se enseña como error: la lista es una ayuda para no
     * duplicar, y no poder cargarla no debe impedir añadir la pausa.
     */
    private fun cargarPausas() {
        viewModelScope.launch {
            val pausas = try {
                authRepository.getPausasAnadidas(fichajeId).takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                null
            }
            if (pausas != null) {
                _uiState.update { it.copy(pausas = pausas) }
            }
        }
    }
}
