package com.nxtime.app.ui.auditoria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.CorreccionFichajeRequest
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

data class CorregirFichajeUiState(
    val fecha: LocalDate = LocalDate.now(DateFormats.ZONA_ESPANA),
    val horaEntrada: Int = 9,
    val minutoEntrada: Int = 0,
    val horaSalida: Int = 18,
    val minutoSalida: Int = 0,
    val motivo: String = "",
    val enviando: Boolean = false,
    val corregido: Boolean = false,
    val error: MensajeUi? = null
)

/**
 * Corregir un fichaje pasado: `PATCH /api/v1/fichaje/{id}`.
 *
 * El endpoint **nunca sobrescribe**: anula el original y crea uno nuevo
 * enlazado, dejando las dos operaciones en la traza de auditoría. Por eso
 * la pantalla exige un motivo antes de dejar enviar: es el único campo
 * que da valor a esa traza, y el backend también lo valida.
 *
 * Las horas se recogen en hora **española** (que es la que el gestor ve
 * en el historial) y se convierten a instante UTC antes de mandarlas.
 * Componer la cadena con la hora local produciría un fichaje desplazado
 * una o dos horas según la época del año, y encima quedaría firmado.
 */
class CorregirFichajeViewModel(
    private val fichajeId: Long,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CorregirFichajeUiState())
    val uiState: StateFlow<CorregirFichajeUiState> = _uiState.asStateFlow()

    /**
     * Parte de los valores que ya tiene el fichaje, en vez de un
     * formulario en blanco: casi siempre se corrige una sola de las dos
     * horas, y volver a teclear la otra es una invitación a equivocarse.
     */
    fun precargar(entradaIso: String?, salidaIso: String?) {
        val fecha = DateFormats.fechaLocal(entradaIso) ?: return
        val entrada = DateFormats.horaYMinutoLocal(entradaIso)
        val salida = DateFormats.horaYMinutoLocal(salidaIso)
        _uiState.update {
            it.copy(
                fecha = fecha,
                horaEntrada = entrada?.first ?: it.horaEntrada,
                minutoEntrada = entrada?.second ?: it.minutoEntrada,
                horaSalida = salida?.first ?: it.horaSalida,
                minutoSalida = salida?.second ?: it.minutoSalida
            )
        }
    }

    fun cambiarEntrada(hora: Int, minuto: Int) =
        _uiState.update { it.copy(horaEntrada = hora, minutoEntrada = minuto, error = null) }

    fun cambiarSalida(hora: Int, minuto: Int) =
        _uiState.update { it.copy(horaSalida = hora, minutoSalida = minuto, error = null) }

    fun cambiarMotivo(motivo: String) =
        _uiState.update { it.copy(motivo = motivo, error = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    fun guardar() {
        val estado = _uiState.value

        if (estado.motivo.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.correccion_motivo_vacio)) }
            return
        }

        val entrada = DateFormats.aInstanteIso(estado.fecha, estado.horaEntrada, estado.minutoEntrada)
        val salida = DateFormats.aInstanteIso(estado.fecha, estado.horaSalida, estado.minutoSalida)

        /*
         * Se comprueba aquí y no solo en el servidor porque la pantalla
         * ya tiene los dos valores delante: el usuario recibe la
         * respuesta al instante en vez de tras una ida y vuelta. El
         * backend lo valida igualmente, que es lo que manda.
         */
        if (salida <= entrada) {
            _uiState.update {
                it.copy(error = MensajeUi.Recurso(R.string.correccion_salida_anterior))
            }
            return
        }

        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.corregirFichaje(
                    fichajeId,
                    CorreccionFichajeRequest(entrada, salida, estado.motivo.trim())
                )
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(enviando = false, corregido = true) }
                } else {
                    _uiState.update {
                        it.copy(enviando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(enviando = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }
}
