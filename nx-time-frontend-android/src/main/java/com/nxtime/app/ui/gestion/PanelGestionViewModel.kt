package com.nxtime.app.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.EquipoAhoraDTO
import com.nxtime.app.data.dto.PendientesDTO
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Los contadores del panel de gestión: "Ausencias pendientes (3)".
 *
 * El panel era un menú sin estado y lo seguiría siendo si no fuera por esto:
 * con los números delante, deja de ser un sitio al que se entra para ir a
 * otro y pasa a decir qué hay que hacer hoy.
 *
 * **Un fallo aquí no se enseña como error**, y es a propósito: los
 * contadores son una ayuda. Si no cargan, el panel se ve como antes, sin
 * números, y se puede seguir entrando a cada bandeja. Enseñar un banner de
 * error encima de un menú que funciona sería peor que no enseñar nada. Y
 * tampoco se enseñan ceros inventados: sin datos, `null`.
 */
class PanelGestionViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _pendientes = MutableStateFlow<PendientesDTO?>(null)
    val pendientes: StateFlow<PendientesDTO?> = _pendientes.asStateFlow()

    /**
     * Quién está trabajando ahora (plan N, paso N7). Como los contadores: si
     * falla, se queda lo que hubiera, y sin datos la sección no sale.
     */
    private val _equipoAhora = MutableStateFlow<EquipoAhoraDTO?>(null)
    val equipoAhora: StateFlow<EquipoAhoraDTO?> = _equipoAhora.asStateFlow()

    fun cargar() {
        viewModelScope.launch {
            val respuesta = try {
                authRepository.getPendientes().takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                null
            }
            // Si falla, se queda lo que hubiera: un contador de hace un rato
            // es más útil que ninguno, y el siguiente ON_RESUME lo repite.
            if (respuesta != null) {
                _pendientes.value = respuesta
            }
        }
        viewModelScope.launch {
            val foto = try {
                authRepository.getEquipoAhora().takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                null
            }
            if (foto != null) {
                _equipoAhora.value = foto
            }
        }
    }
}
