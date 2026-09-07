package com.nxtime.app.ui.proyectos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.AsignarProyectoRequest
import com.nxtime.app.data.dto.DetalleProyectoDTO
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.ProyectoDTO
import com.nxtime.app.data.dto.ProyectoRequest
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

data class ProyectosUiState(
    val cargando: Boolean = false,
    val proyectos: List<ProyectoDTO> = emptyList(),
    /** El proyecto abierto en detalle, o null si se está en la lista. */
    val detalle: DetalleProyectoDTO? = null,
    /** Para el desplegable de "asignar a...". */
    val empleados: List<EmpleadoSimpleDTO> = emptyList(),
    val periodo: YearMonth = YearMonth.now(DateFormats.ZONA_ESPANA),
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
) {
    /**
     * Quién NO está ya en este proyecto, que es lo único que tiene
     * sentido ofrecer al asignar.
     *
     * Se compara contra las asignaciones VIGENTES y no contra todas: a
     * quien pasó por aquí el año pasado y salió sí se le puede volver a
     * asignar, y dejarle fuera de la lista obligaría a preguntarse por
     * qué no aparece.
     */
    val asignables: List<EmpleadoSimpleDTO>
        get() {
            val dentro = detalle?.asignaciones.orEmpty()
                .filter { it.vigente }
                .map { it.usuarioId }
                .toSet()
            return empleados.filter { it.id !in dentro }
        }
}

/**
 * Proyectos de la empresa y sus horas (Fase D).
 *
 * La lista y el detalle viven en el mismo ViewModel a propósito: abrir un
 * proyecto y volver es un gesto constante, y con dos ViewModel la lista
 * se recargaría entera cada vez que se cierra el detalle.
 */
class ProyectosViewModel(
    private val authRepository: AuthRepository,
    periodoInicial: YearMonth = YearMonth.now(DateFormats.ZONA_ESPANA)
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProyectosUiState(periodo = periodoInicial))
    val uiState: StateFlow<ProyectosUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getProyectos()
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update { it.copy(cargando = false, proyectos = cuerpo, error = null) }
                } else {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun abrir(proyectoId: Long) {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            cargarDetalle(proyectoId)
            // La plantilla se pide una sola vez y se queda: es la misma
            // para todos los proyectos y no cambia mientras se navega.
            if (_uiState.value.empleados.isEmpty()) {
                cargarEmpleados()
            }
        }
    }

    fun cerrarDetalle() {
        _uiState.update { it.copy(detalle = null) }
    }

    fun cambiarMes(periodo: YearMonth) {
        _uiState.update { it.copy(periodo = periodo) }
        val abierto = _uiState.value.detalle?.proyecto?.id ?: return
        viewModelScope.launch { cargarDetalle(abierto) }
    }

    fun crear(codigo: String, nombre: String, descripcion: String, fechaInicio: LocalDate) {
        viewModelScope.launch {
            ejecutar(R.string.proyectos_creado) {
                authRepository.crearProyecto(
                    ProyectoRequest(
                        codigo = codigo.trim(),
                        nombre = nombre.trim(),
                        descripcion = descripcion.ifBlank { null },
                        fechaInicio = fechaInicio.toString()
                    )
                )
            }
        }
    }

    fun cambiarEstado(proyectoId: Long, activo: Boolean) {
        viewModelScope.launch {
            val aviso = if (activo) R.string.proyectos_reabierto else R.string.proyectos_cerrado
            ejecutar(aviso) { authRepository.cambiarEstadoProyecto(proyectoId, activo) }
        }
    }

    fun borrar(proyectoId: Long) {
        viewModelScope.launch {
            ejecutar(R.string.proyectos_borrado) { authRepository.borrarProyecto(proyectoId) }
        }
    }

    fun asignar(proyectoId: Long, usuarioId: Long, desde: LocalDate) {
        viewModelScope.launch {
            ejecutar(R.string.proyectos_asignado) {
                authRepository.asignarAProyecto(
                    proyectoId, AsignarProyectoRequest(usuarioId, desde.toString()))
            }
        }
    }

    fun finalizarAsignacion(asignacionId: Long, hasta: LocalDate) {
        viewModelScope.launch {
            ejecutar(R.string.proyectos_asignacion_cerrada) {
                authRepository.finalizarAsignacion(asignacionId, hasta.toString())
            }
        }
    }

    fun avisoMostrado() {
        _uiState.update { it.copy(aviso = null) }
    }

    fun descartarError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Lanza una escritura y, si sale bien, recarga lo que haya en
     * pantalla.
     *
     * Se recarga en vez de retocar la lista en local porque casi toda
     * escritura aquí cambia MÁS de lo que devuelve: asignar a alguien
     * sube el contador de asignados del proyecto, y cerrar una
     * asignación cambia el reparto de horas. Mantener eso a mano en el
     * cliente es como se desincronizan las pantallas.
     */
    private suspend fun ejecutar(avisoOk: Int, accion: suspend () -> retrofit2.Response<*>) {
        try {
            val respuesta = accion()
            if (respuesta.isSuccessful) {
                _uiState.update { it.copy(aviso = MensajeUi.de(avisoOk)) }
                cargar()
                _uiState.value.detalle?.proyecto?.id?.let { cargarDetalle(it) }
            } else {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
        }
    }

    private suspend fun cargarDetalle(proyectoId: Long) {
        try {
            val periodo = _uiState.value.periodo
            val respuesta =
                authRepository.getProyecto(proyectoId, periodo.year, periodo.monthValue)
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                _uiState.update { it.copy(cargando = false, detalle = cuerpo, error = null) }
            } else {
                _uiState.update {
                    it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
        }
    }

    private suspend fun cargarEmpleados() {
        try {
            val respuesta = authRepository.getMisEmpleados()
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                // Solo la gente de alta: asignar a alguien dado de baja
                // crearía una asignación que nadie va a cumplir.
                _uiState.update { estado -> estado.copy(empleados = cuerpo.filter { it.activo }) }
            }
            // Un fallo aquí no se enseña: el detalle del proyecto se ve
            // igual, solo que sin poder asignar a nadie hasta recargar.
        } catch (_: Exception) {
            // Mismo motivo.
        }
    }
}
