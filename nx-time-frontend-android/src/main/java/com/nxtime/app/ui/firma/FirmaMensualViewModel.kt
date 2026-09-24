package com.nxtime.app.ui.firma

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.MesParaFirmarDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FirmaMensualUiState(
    val cargando: Boolean = false,
    /** Ya se pidió al menos una vez: distingue "nada que firmar" de "todavía no ha llegado". */
    val cargado: Boolean = false,
    val meses: List<MesParaFirmarDTO> = emptyList(),
    /** El mes que se está firmando, para no dejar pulsar dos veces. */
    val firmando: MesParaFirmarDTO? = null,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * Firmar el registro de cada mes (Fase B3, ADR 025).
 *
 * La app no decide qué se puede firmar: `puedeFirmar` y el porqué llegan del
 * servidor, que es quien sabe si queda una jornada abierta o una que cerró el
 * sistema. Tras firmar se recarga entero, porque el mes pasa a tener firma y
 * su huella, y eso lo pone el servidor.
 */
class FirmaMensualViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FirmaMensualUiState())
    val uiState: StateFlow<FirmaMensualUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getMisMesesParaFirmar()
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, cargado = true, meses = respuesta.body().orEmpty())
                    }
                } else {
                    _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun firmar(mes: MesParaFirmarDTO) {
        if (_uiState.value.firmando != null) return
        _uiState.update { it.copy(firmando = mes, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.firmarMes(mes.anio, mes.mes)
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(firmando = null, aviso = MensajeUi.de(R.string.firma_hecha)) }
                    cargar()
                } else {
                    // El 422 trae el porqué redactado ("Queda una jornada sin
                    // cerrar..."): se enseña tal cual.
                    _uiState.update { it.copy(firmando = null, error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(firmando = null, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }
}
