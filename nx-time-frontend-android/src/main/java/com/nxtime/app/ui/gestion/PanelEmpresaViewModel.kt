package com.nxtime.app.ui.gestion

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.PanelEmpresaDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.time.LocalDate
import java.time.YearMonth

data class PanelEmpresaUiState(
    val cargando: Boolean = true,
    val panel: PanelEmpresaDTO? = null,
    val empleados: List<EmpleadoSimpleDTO> = emptyList(),
    val mes: YearMonth = YearMonth.now(),
    val descargando: Boolean = false,
    val guardandoFicha: Boolean = false,
    val errorFicha: MensajeUi? = null,
    val error: MensajeUi? = null
)

/**
 * Panel de empresa: indicadores del mes, alta/baja de empleados y
 * descarga de los informes.
 *
 * Junta las tres cosas en una pantalla porque son el mismo trabajo: mirar
 * cómo va la empresa este mes y actuar sobre lo que salga. Separarlas
 * obligaría a navegar entre tres sitios para responder a un solo dato.
 */
class PanelEmpresaViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PanelEmpresaUiState())
    val uiState: StateFlow<PanelEmpresaUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                // Independientes: van a la vez, así la pantalla tarda lo
                // que la más lenta y no la suma de ambas.
                val panelDiferido = async { authRepository.getPanelEmpresa() }
                val empleadosDiferido = async { authRepository.getMisEmpleados() }
                val panel = panelDiferido.await()
                val empleados = empleadosDiferido.await()

                if (!panel.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(panel))
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        cargando = false,
                        panel = panel.body(),
                        empleados = empleados.body().orEmpty()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    fun cambiarMes(mes: YearMonth) = _uiState.update { it.copy(mes = mes) }

    fun descartarError() = _uiState.update { it.copy(error = null) }

    /**
     * Da de alta o de baja a un empleado.
     *
     * Al terminar se recarga la lista entera en vez de retocar el estado
     * en local: el alta/baja también mueve `empleadosActivos` del panel,
     * y dejar los dos números desincronizados en pantalla sería peor que
     * esperar medio segundo.
     */
    fun cambiarEstadoEmpleado(empleadoId: Long, activo: Boolean) {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.cambiarEstadoEmpleado(empleadoId, activo)
                if (respuesta.isSuccessful) {
                    cargar()
                } else {
                    _uiState.update { it.copy(error = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun descartarErrorDeFicha() = _uiState.update { it.copy(errorFicha = null) }

    /**
     * Guarda la ficha de un empleado: jornada semanal y días de
     * vacaciones del año en curso.
     *
     * Valida en local antes de salir a la red, igual que hace
     * `AltaUsuarioViewModel`: el backend también lo comprueba (espeja
     * los CHECK de la base), pero un 400 de ida y vuelta para decir que
     * 61 horas no caben en una semana es un viaje que no hace falta.
     *
     * @param alGuardar se llama solo si el servidor acepta, para que la
     *   pantalla pueda cerrar el diálogo.
     */
    fun guardarFicha(empleadoId: Long, horas: String, dias: String, alGuardar: () -> Unit) {
        val horasNormalizadas = horas.trim().replace(',', '.')
        val horasNumero = horasNormalizadas.toDoubleOrNull()
        if (horasNumero == null || horasNumero <= 0.0 || horasNumero > 60.0) {
            _uiState.update {
                it.copy(errorFicha = MensajeUi.Recurso(R.string.empresa_ficha_horas_invalidas))
            }
            return
        }

        val diasNumero = dias.trim().toIntOrNull()
        if (diasNumero == null || diasNumero < 0) {
            _uiState.update {
                it.copy(errorFicha = MensajeUi.Recurso(R.string.empresa_ficha_dias_invalidos))
            }
            return
        }

        _uiState.update { it.copy(guardandoFicha = true, errorFicha = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.configurarFichaEmpleado(
                    empleadoId, horasNormalizadas, diasNumero
                )
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(guardandoFicha = false, errorFicha = null) }
                    alGuardar()
                    // Recargar en vez de retocar la lista en local, igual
                    // que el alta/baja: un solo camino de código.
                    cargar()
                } else {
                    _uiState.update {
                        it.copy(guardandoFicha = false, errorFicha = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(guardandoFicha = false, errorFicha = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    /**
     * Descarga el Excel de horas de la empresa del mes elegido.
     *
     * El fichero no se toca aquí: se devuelve el cuerpo al llamante, que
     * es quien tiene el `Context` para escribirlo. Un ViewModel que
     * guarda ficheros necesitaría el contexto de la aplicación y sería
     * mucho más difícil de probar.
     */
    fun descargarExcel(alTener: (ResponseBody, String) -> Unit) {
        val mes = _uiState.value.mes
        descargar(
            peticion = { authRepository.descargarExcelDeHoras(mes.year, mes.monthValue) },
            nombre = "horas-$mes.xlsx",
            alTener = alTener
        )
    }

    fun descargarPdf(empleadoId: Long, alTener: (ResponseBody, String) -> Unit) {
        val mes = _uiState.value.mes
        descargar(
            peticion = { authRepository.descargarPdfMensual(empleadoId, mes.year, mes.monthValue) },
            nombre = "registro-$empleadoId-$mes.pdf",
            alTener = alTener
        )
    }

    private fun descargar(
        peticion: suspend () -> retrofit2.Response<ResponseBody>,
        nombre: String,
        alTener: (ResponseBody, String) -> Unit
    ) {
        _uiState.update { it.copy(descargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = peticion()
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    alTener(cuerpo, nombre)
                    _uiState.update { it.copy(descargando = false) }
                } else {
                    _uiState.update {
                        it.copy(descargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                /*
                 * Se registra la excepción real porque `mensajeDeRed`
                 * traduce CUALQUIER fallo a "no se ha podido conectar", y
                 * eso oculta la causa: el primer intento de descarga
                 * moría con un `EOFException` del interceptor de logging
                 * (ver RetrofitClient) y la pantalla solo decía que no
                 * había conexión, con el servidor respondiendo 200.
                 */
                Log.w("NxTimeInformes", "Fallo al descargar el informe", e)
                _uiState.update {
                    it.copy(descargando = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    companion object {
        /**
         * Los meses que se ofrecen: este y los once anteriores. Hacia
         * adelante no hay nada que informar, y un selector abierto
         * invitaría a pedir un informe vacío.
         */
        fun mesesDisponibles(hoy: LocalDate = LocalDate.now()): List<YearMonth> {
            val actual = YearMonth.from(hoy)
            return (0..11).map { actual.minusMonths(it.toLong()) }
        }
    }
}
