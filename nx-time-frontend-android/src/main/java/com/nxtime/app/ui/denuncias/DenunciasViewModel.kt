package com.nxtime.app.ui.denuncias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.DenunciaCreadaDTO
import com.nxtime.app.data.dto.DenunciaDTO
import com.nxtime.app.data.dto.ResumenDenunciaDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DenunciasUiState(
    val cargando: Boolean = false,

    /**
     * Las que presenté identificándome. **Las anónimas no están y no
     * pueden estar**: no hay ningún dato que las relacione conmigo.
     */
    val mias: List<ResumenDenunciaDTO> = emptyList(),

    /**
     * La denuncia recién presentada, con su código.
     *
     * Vive en el estado solo hasta que el usuario confirma que lo ha
     * guardado. No se persiste en ningún sitio: ver [recienCreada] en
     * el ViewModel.
     */
    val recienCreada: DenunciaCreadaDTO? = null,

    /** El expediente abierto, venga del código o de "mis denuncias". */
    val expediente: DenunciaDTO? = null,

    /**
     * El código con el que se abrió el expediente, si se abrió así.
     *
     * Decide por qué puerta se contesta: con código o como titular. Sin
     * esto habría que adivinarlo de `expediente.anonima`, y eso falla
     * justo en el caso mixto — una denuncia identificada abierta con su
     * código.
     */
    val codigoAbierto: String? = null,

    val enviando: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * El canal de denuncias visto por quien denuncia (Fase G).
 *
 * **La decisión que ordena esta clase: el código de seguimiento no se
 * guarda en ninguna parte.** Ni en `SessionManager`, ni en las
 * preferencias, ni aquí más allá del diálogo que lo enseña. Guardarlo
 * sería cómodo y convertiría el móvil en la prueba de que esa denuncia
 * anónima es de su dueño: cualquiera que abra la aplicación
 * desbloqueada, o restaure una copia de seguridad, la tendría delante.
 *
 * La contrapartida es dura y hay que decirla en la pantalla, no
 * esconderla: si pierde el código, pierde el acceso al expediente. Por
 * eso el diálogo obliga a confirmar y el texto lo escribe el servidor
 * (`avisoImportante`), no `strings.xml`.
 */
class DenunciasViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DenunciasUiState())
    val uiState: StateFlow<DenunciasUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getMisDenuncias()
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(cargando = false, mias = respuesta.body().orEmpty())
                    }
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

    /**
     * Presenta la denuncia.
     *
     * `anonima` llega desde la pantalla sin valor por defecto en ningún
     * punto del camino, igual que en el DTO y en el servidor: es la
     * única decisión de la app que no se puede deshacer después.
     */
    fun presentar(categoria: CategoriaDenuncia, descripcion: String, anonima: Boolean) {
        if (descripcion.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.de(R.string.denuncia_error_sin_descripcion)) }
            return
        }
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.presentarDenuncia(
                    categoria.name, descripcion.trim(), anonima)
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    _uiState.update { it.copy(enviando = false, recienCreada = respuesta.body()) }
                    // Si fue identificada aparece en "mis denuncias"; si
                    // fue anónima, la lista sigue igual y eso también es
                    // parte de lo que la pantalla tiene que enseñar.
                    cargar()
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

    /**
     * El usuario dice que ya ha guardado el código.
     *
     * A partir de aquí el código deja de existir en la app. No hay
     * ningún sitio del que sacarlo otra vez, ni aquí ni en el servidor.
     */
    fun codigoGuardado() = _uiState.update { it.copy(recienCreada = null) }

    /** Abrir un expediente con el código. Es la única vía a una anónima. */
    fun buscarPorCodigo(codigo: String) {
        if (codigo.isBlank()) {
            _uiState.update { it.copy(error = MensajeUi.de(R.string.denuncia_error_sin_codigo)) }
            return
        }
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getDenunciaPorCodigo(codigo)
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            expediente = respuesta.body(),
                            codigoAbierto = codigo.trim()
                        )
                    }
                } else {
                    // El servidor da el MISMO 404 a un código inventado y
                    // a uno de otra empresa, así que la app tampoco puede
                    // decir cuál de las dos cosas ha pasado.
                    _uiState.update {
                        it.copy(cargando = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /** Abrir una de las mías identificadas, sin necesitar el código. */
    fun abrirMia(denunciaId: Long) {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getMiDenuncia(denunciaId)
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    _uiState.update {
                        it.copy(cargando = false, expediente = respuesta.body(), codigoAbierto = null)
                    }
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

    /** Contestar por la puerta por la que se entró. */
    fun responder(texto: String) {
        val estado = _uiState.value
        val expediente = estado.expediente ?: return
        if (texto.isBlank()) {
            return
        }
        _uiState.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = if (estado.codigoAbierto != null) {
                    authRepository.responderDenunciaPorCodigo(estado.codigoAbierto, texto.trim())
                } else {
                    authRepository.responderMiDenuncia(expediente.id, texto.trim())
                }
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    _uiState.update {
                        it.copy(
                            enviando = false,
                            expediente = respuesta.body(),
                            aviso = MensajeUi.de(R.string.denuncia_mensaje_enviado)
                        )
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

    /**
     * Cierra el expediente abierto y **suelta el código con él**.
     *
     * Volver atrás no puede dejar el código colgando en memoria a la
     * espera de que alguien lo vuelva a usar: el usuario ya decidió que
     * lo guardaba él.
     */
    fun cerrarExpediente() =
        _uiState.update { it.copy(expediente = null, codigoAbierto = null) }

    fun avisoMostrado() = _uiState.update { it.copy(aviso = null) }

    fun descartarError() = _uiState.update { it.copy(error = null) }
}
