package com.nxtime.app.ui.login

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * HomeViewModel: Es la "lógica" de la pantalla HomeActivity. Se encarga de gestionar el estado del fichaje y de comunicarse con el repositorio.
 */

class HomeViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    /**
     * LiveData: Es la "caja" que contiene el estado de la pantalla. La Activity "observa" esta caja para saber qué dibujar
     */

    private val _fichajeState = MutableLiveData<FichajeState>()
    val fichajeState: LiveData<FichajeState> = _fichajeState

    /**
     * Caché local: Guarda el registro activo actual. Nos evita tener que preguntar a la API por cada clic.
     */

    private var registroActivo: Registro? = null

    /**
     * Comprueba el estado de la jornada al abrir la app. Llama a la API para saber si el usuario ya está trabajando o en pausa.
     */

    fun comprobarEstadoJornada() {
        _fichajeState.value = FichajeState.Loading

        viewModelScope.launch {
            try {
                val response = authRepository.getRegistroActivo()

                if (response.isSuccessful) {
                    registroActivo = response.body()

                    if (registroActivo != null) {
                        if (registroActivo!!.enPausa) {
                            _fichajeState.value = FichajeState.EnPausa(registroActivo!!)
                        } else {
                            _fichajeState.value = FichajeState.JornadaActiva(registroActivo!!)
                        }
                    } else {
                        _fichajeState.value = FichajeState.SinJornada
                    }
                } else {
                    _fichajeState.value = FichajeState.Error("Error al obtener estado: ${response.code()}")
                }
            } catch (e: Exception) {
                _fichajeState.value = FichajeState.Error("Error de red: ${e.message}")
            }
        }
    }

    /**
     * Lógica para el botón principal
     */

    fun botonFichajePulsado() {
        val tipoPeticion = if (registroActivo == null) "INICIO" else "FIN"

        if (tipoPeticion == "FIN" && registroActivo?.enPausa == true) {
            _fichajeState.value = FichajeState.Error("Debes reanudar la pausa antes de finalizar la jornada")
            return
        }
        registrarFichaje(tipoPeticion)
    }

    /**
     * Lógica para el botón de Pausa
     */

    fun botonPausaPulsado() {
        // --- ¡AQUÍ ESTÁ LA CORRECCIÓN! ---
        // Los strings deben coincidir con el backend ("PAUSA_INICIO" y "PAUSA_FIN")
        val tipoPeticion = if (registroActivo?.enPausa == true) "PAUSA_FIN" else "PAUSA_INICIO"

        if (registroActivo == null) {
            _fichajeState.value = FichajeState.Error("Debes iniciar la jornada antes de pausar")
            return
        }
        registrarFichaje(tipoPeticion)
    }

    /**
     * Función interna que hace la llamada a la API para cualquier tipo de fichaje. Actualiza la caché local  y el estado de la UI
     */

    private fun registrarFichaje(tipo: String) {
        _fichajeState.value = FichajeState.Loading

        viewModelScope.launch {
            try {
                val peticion = PeticionFichaje(tipo = tipo)
                val response = authRepository.registrarFichaje(peticion)

                if (response.isSuccessful && response.body() != null) {
                    val registroRespuesta = response.body()!!


                    if (registroRespuesta.horaSalida != null) {
                        // Jornada finalizada
                        registroActivo = null
                        _fichajeState.value = FichajeState.SinJornada
                        Log.d("HomeViewModel", "Jornada finalizada con éxito.")

                    } else if (registroRespuesta.enPausa) {
                        // Pausa iniciada
                        registroActivo = registroRespuesta
                        _fichajeState.value = FichajeState.EnPausa(registroRespuesta)
                        Log.d("HomeViewModel", "Pausa iniciada.")

                    } else {
                        // Inicio de jornada o reanudación de pausa
                        registroActivo = registroRespuesta
                        _fichajeState.value = FichajeState.JornadaActiva(registroRespuesta)
                        Log.d("HomeViewModel", "Jornada activa (iniciada o reanudada).")
                    }


                } else {
                    _fichajeState.value = FichajeState.Error("Error al registrar fichaje: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                _fichajeState.value = FichajeState.Error("Error de red: ${e.message}")
            }
        }
    }
}

/**
 * FichajeState: Define los 5 únicos estados posibles en los que puede estar esta pantalla.
 */

sealed class FichajeState {
    object Loading : FichajeState()
    object SinJornada : FichajeState()
    data class JornadaActiva(val registro: Registro) : FichajeState()
    data class EnPausa(val registro: Registro) : FichajeState()
    data class Error(val message: String) : FichajeState()
}