package com.nxtime.app.ui.fichar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.data.dto.DiaTeoricoDTO
import com.nxtime.app.data.dto.OrigenDelDia
import com.nxtime.app.data.dto.PeticionFichaje
import com.nxtime.app.data.dto.ProyectosParaFicharDTO
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
import java.time.LocalDate

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
     * La pantalla está preguntando "¿seguro que terminas?".
     *
     * Vive en el estado y no en un `remember` de la pantalla para que el
     * diálogo sobreviva a un giro de pantalla, y sobre todo para poder
     * comprobar en un test que finalizar pide confirmación: si estuviera
     * en la composición, el test del ViewModel no lo vería.
     */
    val confirmandoFin: Boolean = false,
    /**
     * Hoy no es laborable (festivo o ausencia aprobada) y se está
     * preguntando si iniciar igualmente. Es el motivo, para enseñarlo.
     */
    val confirmandoInicioNoLaborable: String? = null,
    /** Los proyectos para fichar y el de la jornada en curso. Null si no han cargado. */
    val proyectos: ProyectosParaFicharDTO? = null,
    /** Se está eligiendo en qué proyecto empezar la jornada. */
    val eligiendoProyectoAlIniciar: Boolean = false,
    /** Se está eligiendo a qué proyecto cambiar con la jornada abierta. */
    val cambiandoDeProyecto: Boolean = false,

    /**
     * Totales del backend. Es `null` mientras no ha llegado, y puede
     * quedarse a `null` sin que sea un error: el resumen es un extra, y
     * si falla no debe impedir fichar (ver [cargarResumen]).
     */
    val resumen: ResumenPersonalDTO? = null,

    /**
     * El horario teórico de hoy (Fase B1): a qué hora tocaba entrar. Es
     * `null` si no ha llegado o si falló, y como el resumen, no impide fichar:
     * quien no tiene cuadrante —la mayoría— ficha exactamente igual que antes.
     */
    val hoyTeorico: DiaTeoricoDTO? = null
) {
    /**
     * Lo que la pantalla dice del cuadrante de hoy, o `null` si no hay nada
     * que decir.
     *
     * Solo se habla cuando HAY cuadrante. Sin él no se inventa un horario
     * repartiendo la jornada contratada entre los días, y en un festivo o con
     * vacaciones ya se encarga el diálogo de día no laborable.
     */
    val avisoDeCuadrante: AvisoDeCuadrante?
        get() {
            val hoy = hoyTeorico ?: return null
            val origen = OrigenDelDia.de(hoy.origen)
            if (origen != OrigenDelDia.CUADRANTE && origen != OrigenDelDia.EXCEPCION) return null
            return hoy.entrada?.let { AvisoDeCuadrante.EntradaPrevista(it) } ?: AvisoDeCuadrante.SinTurno
        }
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
        cargarCuadranteDeHoy()
    }

    /**
     * Una vez al abrir, no tras cada fichaje: fichar no cambia a qué hora
     * tocaba entrar. Si falla se queda sin decir nada, como el resumen.
     */
    private fun cargarCuadranteDeHoy() {
        viewModelScope.launch {
            val hoy = LocalDate.now(DateFormats.ZONA_ESPANA)
            val dia = try {
                authRepository.getMiCuadrante(hoy, hoy).takeIf { it.isSuccessful }?.body()?.firstOrNull()
            } catch (e: Exception) {
                null
            }
            if (dia != null) {
                _uiState.update { it.copy(hoyTeorico = dia) }
            }
        }
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
     * Botón central. Inicia la jornada si no hay ninguna y **pide
     * confirmación** para terminarla si la hay.
     *
     * Iniciar va directo y finalizar no, y la asimetría es deliberada: es
     * la misma doctrina que el panel de empresa, donde dar de baja pide
     * confirmación y reactivar no. Se confirma lo que cuesta deshacer.
     * Cerrar la jornada por error obliga a pedir una corrección y a que
     * alguien la apruebe; empezarla por error se arregla terminándola.
     *
     * La comprobación de "no puedes terminar estando en pausa" se hace
     * aquí y no se manda al servidor: es una regla que el propio estado
     * de la pantalla ya conoce, y así el usuario recibe la respuesta al
     * instante en vez de tras una ida y vuelta. Va **antes** del diálogo:
     * no tiene sentido confirmar algo que se va a rechazar.
     */
    fun pulsarBotonPrincipal() {
        val estado = _uiState.value.estado
        if (estado == EstadoJornada.EN_PAUSA) {
            _uiState.update { it.copy(error = MensajeUi.Recurso(R.string.fichar_reanuda_antes)) }
            return
        }
        if (estado == EstadoJornada.SIN_JORNADA) {
            iniciarComprobandoElDia()
        } else {
            _uiState.update { it.copy(confirmandoFin = true, error = null) }
        }
    }

    /**
     * Antes de iniciar, pregunta si hoy es laborable. Si no lo es, se pide
     * confirmación con el motivo delante; si lo es, se inicia sin más.
     *
     * **Si la pregunta falla, se inicia igual.** Fichar es la función de la
     * app, y no puede quedar bloqueada por una comprobación de cortesía: el
     * aviso a los gestores lo decide el servidor al registrar el inicio,
     * haya preguntado la app o no.
     */
    private fun iniciarComprobandoElDia() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val motivo = try {
                authRepository.getEstadoDeHoy().body()?.takeIf { !it.laborable }?.motivo
            } catch (e: Exception) {
                null
            }
            if (motivo != null) {
                _uiState.update { it.copy(cargando = false, confirmandoInicioNoLaborable = motivo) }
            } else {
                iniciarEligiendoProyecto()
            }
        }
    }

    /** Sí, empieza la jornada aunque hoy no sea laborable. */
    fun confirmarInicioNoLaborable() {
        _uiState.update { it.copy(confirmandoInicioNoLaborable = null) }
        iniciarEligiendoProyecto()
    }

    /**
     * Con dos o más proyectos se pregunta en cuál se empieza; con uno o
     * ninguno se inicia sin preguntar y decide el servidor (ADR 017).
     *
     * Si los proyectos no han cargado, se inicia igual: fichar no espera a
     * nada, y la jornada se puede asignar después con "Cambiar".
     */
    private fun iniciarEligiendoProyecto() {
        if (_uiState.value.proyectos?.hayQueElegir == true) {
            _uiState.update { it.copy(cargando = false, eligiendoProyectoAlIniciar = true) }
        } else {
            registrarFichaje(TipoFichaje.INICIO)
        }
    }

    fun iniciarEnProyecto(proyectoId: Long) {
        _uiState.update { it.copy(eligiendoProyectoAlIniciar = false) }
        registrarFichaje(TipoFichaje.INICIO, proyectoId)
    }

    fun cancelarEleccionDeProyecto() {
        _uiState.update { it.copy(eligiendoProyectoAlIniciar = false, cargando = false) }
    }

    fun pedirCambioDeProyecto() {
        _uiState.update { it.copy(cambiandoDeProyecto = true, error = null) }
    }

    fun cancelarCambioDeProyecto() {
        _uiState.update { it.copy(cambiandoDeProyecto = false) }
    }

    fun cambiarAProyecto(proyectoId: Long) {
        val registro = _uiState.value.registro ?: return
        _uiState.update { it.copy(cambiandoDeProyecto = false) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.cambiarProyecto(registro.id, proyectoId)
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update { it.copy(proyectos = cuerpo) }
                } else {
                    _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /** Como el resumen: si falla no se enseña nada, y fichar sigue funcionando. */
    private fun cargarProyectos() {
        viewModelScope.launch {
            val proyectos = try {
                authRepository.getProyectosParaFichar().takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                null
            }
            if (proyectos != null) {
                _uiState.update { it.copy(proyectos = proyectos) }
            }
        }
    }

    fun cancelarInicioNoLaborable() {
        _uiState.update { it.copy(confirmandoInicioNoLaborable = null) }
    }

    /** Sí, termina la jornada. */
    fun confirmarFinDeJornada() {
        _uiState.update { it.copy(confirmandoFin = false) }
        registrarFichaje(TipoFichaje.FIN)
    }

    /** No, sigue trabajando. */
    fun cancelarFinDeJornada() {
        _uiState.update { it.copy(confirmandoFin = false) }
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

    private fun registrarFichaje(tipo: TipoFichaje, proyectoId: Long? = null) {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.registrarFichaje(PeticionFichaje(tipo, proyectoId))
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
        // Y el proyecto en curso: al iniciar aparece, al cerrar desaparece.
        cargarProyectos()
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
     * pregunta por las authorities que manda el servidor (Fase C3), y lo
     * consulta el grafo de navegación, que es quien decide qué pestañas
     * existen.
     */

}

/** Qué decir del cuadrante de hoy en "Mi jornada". */
sealed interface AvisoDeCuadrante {
    /** "Entrada prevista: 09:00". */
    data class EntradaPrevista(val hora: String) : AvisoDeCuadrante

    /** Tiene cuadrante, pero hoy no le toca: día libre en la plantilla o por excepción. */
    data object SinTurno : AvisoDeCuadrante
}
