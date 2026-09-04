package com.nxtime.app.ui.fichar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.ResumenPersonalDTO
import com.nxtime.app.data.dto.TipoFichaje
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.R
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** En qué punto de la jornada está el empleado. */
enum class EstadoJornada { SIN_JORNADA, TRABAJANDO, EN_PAUSA }

data class FicharUiState(
    val cargando: Boolean = true,
    val estado: EstadoJornada = EstadoJornada.SIN_JORNADA,
    val registro: Registro? = null,
    val nombreUsuario: String = "",
    val error: MensajeUi? = null,

    /** Lo que lleva la jornada abierta, en segundos netos. */
    val segundosEnCurso: Long = 0,

    /**
     * Totales del backend. Es `null` mientras no ha llegado, y puede
     * quedarse a `null` sin que sea un error: el resumen es un extra, y
     * si falla no debe impedir fichar (ver [cargarResumen]).
     */
    val resumen: ResumenPersonalDTO? = null
) {
    /**
     * Trabajado hoy, contando la jornada que está abierta ahora mismo.
     *
     * Hay que sumarlo aquí porque `minutosHoy` **solo cuenta jornadas
     * cerradas**: la consulta del backend filtra por `hora_salida IS NOT
     * NULL`. Sin esta suma, alguien que lleva dos horas fichado leería
     * "Hoy: 0h 00m" mientras el cronómetro corre a su lado.
     *
     * Lo mismo vale para la semana y el mes: una jornada abierta hoy
     * también pertenece a esta semana y a este mes.
     */
    val minutosHoy: Long get() = totalCon(resumen?.minutosHoy)
    val minutosSemana: Long get() = totalCon(resumen?.minutosSemana)
    val minutosMes: Long get() = totalCon(resumen?.minutosMes)

    private fun totalCon(minutosCerrados: Long?): Long =
        (minutosCerrados ?: 0) + segundosEnCurso / 60
}

/**
 * Pantalla principal: fichar entrada, pausa y salida.
 *
 * Aquí estaba el fallo de experiencia de uso más caro del proyecto. El
 * mensaje de error se componía así:
 *
 *     "Error al registrar fichaje: ${response.code()} ${response.message()}"
 *
 * y `response.message()` en Retrofit es la frase del estado HTTP, no el
 * cuerpo de la respuesta. Al pulsar "fichar" dos veces, el usuario leía
 * **"Error al registrar fichaje: 409 Conflict"** en lugar del
 * "Ya hay una jornada activa." que el backend devuelve en el `detail`
 * del ProblemDetail. Todo el trabajo de la Fase 2 del backend (RFC 7807)
 * moría aquí. Ahora los errores pasan por {@link ApiErrorParser}.
 */
class FicharViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FicharUiState())
    val uiState: StateFlow<FicharUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(nombreUsuario = sessionManager.fetchUserName().orEmpty()) }
        comprobarEstadoJornada()
    }

    /**
     * Recalcula el cronómetro de la jornada abierta.
     *
     * Lo llama la pantalla una vez por segundo, y **el latido vive allí a
     * propósito**: un bucle infinito aquí dentro seguiría corriendo con
     * la pantalla en segundo plano, y además dejaba colgados los tests
     * (`advanceUntilIdle` nunca termina si siempre hay un `delay`
     * pendiente). En la composición, en cambio, el latido se para solo
     * cuando la pantalla desaparece.
     *
     * Solo cuenta si se está TRABAJANDO: durante una pausa la cuenta se
     * dispararía, porque la pausa en curso todavía no está acumulada --
     * el backend la suma al reanudar.
     *
     * El valor se deriva SIEMPRE de `horaEntrada`, nunca sumando uno al
     * contador anterior. Así sigue siendo correcto aunque se hayan
     * perdido latidos mientras la app estaba en segundo plano.
     */
    fun actualizarCronometro() {
        _uiState.update { estado ->
            if (estado.estado != EstadoJornada.TRABAJANDO) {
                estado
            } else {
                estado.copy(
                    segundosEnCurso = DateFormats.segundosTrabajados(
                        estado.registro?.horaEntrada,
                        estado.registro?.segundosPausaAcumulados ?: 0
                    )
                )
            }
        }
    }

    /**
     * Totales de hoy, la semana y el mes, más el saldo de vacaciones.
     *
     * Un fallo aquí **no** se enseña como error de pantalla ni bloquea
     * nada: fichar tiene que seguir funcionando aunque el resumen no
     * cargue. Simplemente no se pintan las tarjetas.
     */
    private fun cargarResumen() {
        viewModelScope.launch {
            val resumen = try {
                authRepository.getResumenPersonal().takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                null
            }
            if (resumen != null) {
                _uiState.update { it.copy(resumen = resumen) }
            }
        }
    }

    fun comprobarEstadoJornada() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getRegistroActivo()
                if (respuesta.isSuccessful) {
                    aplicarRegistro(respuesta.body())
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

    /**
     * Botón central. Inicia la jornada si no hay ninguna y la termina si
     * la hay.
     *
     * La comprobación de "no puedes terminar estando en pausa" se hace
     * aquí y no se manda al servidor: es una regla que el propio estado
     * de la pantalla ya conoce, y así el usuario recibe la respuesta al
     * instante en vez de tras una ida y vuelta.
     */
    fun pulsarBotonPrincipal() {
        val estado = _uiState.value.estado
        if (estado == EstadoJornada.EN_PAUSA) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.fichar_reanuda_antes)) }
            return
        }
        val tipo = if (estado == EstadoJornada.SIN_JORNADA) TipoFichaje.INICIO else TipoFichaje.FIN
        registrarFichaje(tipo)
    }

    fun pulsarBotonPausa() {
        val estado = _uiState.value.estado
        if (estado == EstadoJornada.SIN_JORNADA) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.fichar_inicia_antes)) }
            return
        }
        val tipo = if (estado == EstadoJornada.EN_PAUSA) {
            TipoFichaje.PAUSA_FIN
        } else {
            TipoFichaje.PAUSA_INICIO
        }
        registrarFichaje(tipo)
    }

    private fun registrarFichaje(tipo: TipoFichaje) {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.registrarFichaje(PeticionFichaje(tipo))
                if (respuesta.isSuccessful) {
                    aplicarRegistro(respuesta.body())
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

    /**
     * Traduce la respuesta del backend al estado de la pantalla.
     *
     * Un registro con hora de salida ya no es una jornada activa: el
     * backend responde con el fichaje recién cerrado, no con null.
     */
    private fun aplicarRegistro(registro: Registro?) {
        val estado = when {
            registro == null || registro.horaSalida != null -> EstadoJornada.SIN_JORNADA
            registro.enPausa -> EstadoJornada.EN_PAUSA
            else -> EstadoJornada.TRABAJANDO
        }
        val activo = if (estado == EstadoJornada.SIN_JORNADA) null else registro
        _uiState.update {
            it.copy(
                cargando = false,
                estado = estado,
                registro = activo,
                error = null,
                /*
                 * Se recalcula ya, sin esperar al siguiente latido: al
                 * iniciar la jornada el cronómetro tiene que arrancar en
                 * el acto.
                 *
                 * En pausa se CONGELA en el valor que llevaba, no se
                 * pone a cero: el tiempo trabajado no desaparece porque
                 * el empleado se vaya a comer. Y no se recalcula, porque
                 * durante la pausa la cuenta se dispararía.
                 *
                 * Al terminar la jornada sí vuelve a cero: lo trabajado
                 * pasa a sumar en el resumen, y dejar la cifra puesta
                 * haría creer que sigue habiendo una jornada abierta.
                 */
                segundosEnCurso = when (estado) {
                    EstadoJornada.TRABAJANDO -> DateFormats.segundosTrabajados(
                        activo?.horaEntrada,
                        activo?.segundosPausaAcumulados ?: 0
                    )
                    EstadoJornada.EN_PAUSA -> it.segundosEnCurso
                    EstadoJornada.SIN_JORNADA -> 0
                }
            )
        }
        // Cada fichaje cambia los totales: al cerrar una jornada, lo
        // trabajado pasa de "en curso" a sumar en el resumen.
        cargarResumen()
    }

    fun descartarError() {
        _uiState.update { it.copy(error = null) }
    }

    /*
     * Quién ve el panel de gestión ya no se decide aquí. Vivía en esta
     * clase como un `setOf("GESTOR", "RRHH", "ADMIN")`, y con esa única
     * brocha se pintaban permisos que el backend distingue mucho más
     * fino: por eso se le ofrecía "Crear gestor" a un GESTOR, que no
     * tiene esa authority. Ahora lo resuelve `ui/util/Permisos.kt`, que
     * es un espejo de `RoleAuthorities.java`, y lo consulta el grafo de
     * navegación, que es quien decide qué pestañas existen.
     */

}
