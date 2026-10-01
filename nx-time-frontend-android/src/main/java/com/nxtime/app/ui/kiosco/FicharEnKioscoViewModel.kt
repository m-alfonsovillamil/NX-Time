package com.nxtime.app.ui.kiosco

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.EstadoKioscoDTO
import com.nxtime.app.data.dto.TarjetaKioscoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

data class FicharEnKioscoUiState(
    val cargando: Boolean = false,
    val estado: EstadoKioscoDTO? = null,
    /** Lo que hay tecleado: solo cifras, seis como mucho. No se guarda en ningún sitio más. */
    val pin: String = "",
    val guardando: Boolean = false,
    /** La tarjeta, solo cuando se ha pedido verla: pedirla la crea si no existía. */
    val tarjeta: TarjetaKioscoDTO? = null,
    val cargandoTarjeta: Boolean = false,
    val errorPin: MensajeUi? = null,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * El PIN y la tarjeta para fichar en un kiosco (ADR 033), desde el perfil.
 *
 * La app no ficha en el kiosco: eso lo hace la tablet, que es la web. Aquí se
 * elige el PIN --que no se enseña nunca, ni a quien administra (ADR 014)-- y se
 * enseña la tarjeta a pantalla completa para pasarla por la cámara de la
 * tablet. Qué PIN es demasiado fácil lo decide el servidor; la app solo evita
 * mandar uno que no tenga de 4 a 6 cifras.
 */
class FicharEnKioscoViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FicharEnKioscoUiState())
    val uiState: StateFlow<FicharEnKioscoUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            conEstado(authRepository::getEstadoKiosco) { estado -> _uiState.update { it.copy(estado = estado) } }
            _uiState.update { it.copy(cargando = false) }
        }
    }

    fun cambiarPin(texto: String) {
        _uiState.update { it.copy(pin = texto.filter(Char::isDigit).take(LARGO_MAXIMO), errorPin = null) }
    }

    fun guardarPin() {
        val pin = _uiState.value.pin
        if (!esPinValido(pin)) {
            _uiState.update { it.copy(errorPin = MensajeUi.de(R.string.kiosco_pin_invalido)) }
            return
        }
        _uiState.update { it.copy(guardando = true, errorPin = null, error = null) }
        viewModelScope.launch {
            conEstado({ authRepository.fijarPinKiosco(pin) }) { estado ->
                _uiState.update { it.copy(estado = estado, pin = "", aviso = MensajeUi.de(R.string.kiosco_pin_guardado)) }
            }
            _uiState.update { it.copy(guardando = false) }
        }
    }

    fun quitarPin() {
        _uiState.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            conEstado(authRepository::quitarPinKiosco) { estado ->
                _uiState.update { it.copy(estado = estado, aviso = MensajeUi.de(R.string.kiosco_pin_quitado)) }
            }
            _uiState.update { it.copy(guardando = false) }
        }
    }

    fun verTarjeta() = pedirTarjeta(authRepository::getTarjetaKiosco, null)

    fun regenerarTarjeta() = pedirTarjeta(authRepository::regenerarTarjetaKiosco, R.string.kiosco_tarjeta_regenerada)

    fun ocultarTarjeta() = _uiState.update { it.copy(tarjeta = null) }

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    private fun pedirTarjeta(llamada: suspend () -> Response<TarjetaKioscoDTO>, aviso: Int?) {
        _uiState.update { it.copy(cargandoTarjeta = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = llamada()
                val tarjeta = respuesta.body()
                if (respuesta.isSuccessful && tarjeta != null) {
                    _uiState.update {
                        it.copy(tarjeta = tarjeta, aviso = aviso?.let { id -> MensajeUi.de(id) } ?: it.aviso)
                    }
                    // Con tarjeta ya hay tarjeta: el estado lo dice al volver.
                    cargar()
                } else {
                    _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
            }
            _uiState.update { it.copy(cargandoTarjeta = false) }
        }
    }

    private suspend fun conEstado(
        llamada: suspend () -> Response<EstadoKioscoDTO>,
        alRecibir: (EstadoKioscoDTO) -> Unit
    ) {
        try {
            val respuesta = llamada()
            val estado = respuesta.body()
            if (respuesta.isSuccessful && estado != null) {
                alRecibir(estado)
            } else {
                // El 400 del PIN demasiado fácil trae el porqué redactado: se enseña tal cual.
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
        }
    }

    companion object {
        const val LARGO_MAXIMO = 6

        /** De 4 a 6 cifras. Lo demás (repetidas, seguidas) lo decide el servidor. */
        fun esPinValido(pin: String): Boolean = pin.length in 4..LARGO_MAXIMO && pin.all(Char::isDigit)
    }
}
