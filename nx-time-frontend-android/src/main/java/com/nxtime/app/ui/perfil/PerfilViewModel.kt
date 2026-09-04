package com.nxtime.app.ui.perfil

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.AdjuntoDTO
import com.nxtime.app.data.dto.PerfilDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Los dos tipos de adjunto, tal como los nombra la API. */
const val TIPO_CV = "CV"
const val TIPO_FOTO = "FOTO"

data class PerfilUiState(
    val cargando: Boolean = false,
    val perfil: PerfilDTO? = null,
    val cv: AdjuntoDTO? = null,

    /**
     * La foto ya decodificada.
     *
     * Vive en el ViewModel y no se vuelve a pedir en cada
     * recomposición: son unos 30 KB que llegan una vez por sesión. Por
     * eso el proyecto sigue sin ninguna librería de imágenes — Coil o
     * Glide traen caché en disco, transformaciones y peticiones
     * concurrentes, y aquí hay UNA imagen pequeña.
     */
    val foto: ImageBitmap? = null,
    val subiendo: Boolean = false,
    val descargandoCv: Boolean = false,
    val editando: Boolean = false,
    val guardando: Boolean = false,
    // Campos del formulario, separados del perfil cargado: mientras se
    // edita, la pantalla enseña lo que hay escrito, no lo que hay
    // guardado.
    val nombre: String = "",
    val apellidos: String = "",
    val fechaNacimiento: String = "",
    val puesto: String = "",
    val errorFormulario: MensajeUi? = null,
    val error: MensajeUi? = null
)

/**
 * El perfil propio: verlo y editar los datos personales.
 *
 * Lo que NO está aquí es tan importante como lo que sí: rol, jornada,
 * vacaciones y departamento se ven pero no se editan, porque el backend
 * no los acepta en `PATCH /perfil`. Ofrecer el campo y comerse un 400
 * sería peor que no ofrecerlo.
 */
class PerfilViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PerfilUiState())
    val uiState: StateFlow<PerfilUiState> = _uiState.asStateFlow()

    /**
     * **Sin `init { cargar() }`**, igual que `AvisosViewModel` y por el
     * mismo motivo: `NxTimeNavHost` lo construye para tener las
     * iniciales del avatar, así que existe también mientras se está en
     * el login, donde una petición autenticada saldría sin token y
     * dejaría un 401 pegado al estado que la pantalla enseñaría después.
     * Quien lo usa decide cuándo pedir datos.
     */
    fun cargar() {
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getMiPerfil()
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update { it.copy(cargando = false, perfil = cuerpo, error = null) }
                    cargarAdjuntos()
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

    /** Abre el formulario precargado con lo que hay guardado. */
    fun empezarAEditar() {
        val perfil = _uiState.value.perfil ?: return
        _uiState.update {
            it.copy(
                editando = true,
                nombre = perfil.nombre,
                apellidos = perfil.apellidos.orEmpty(),
                fechaNacimiento = perfil.fechaNacimiento.orEmpty(),
                puesto = perfil.puesto.orEmpty(),
                errorFormulario = null
            )
        }
    }

    fun cancelarEdicion() = _uiState.update { it.copy(editando = false, errorFormulario = null) }

    fun onNombreCambia(valor: String) = _uiState.update { it.copy(nombre = valor, errorFormulario = null) }
    fun onApellidosCambia(valor: String) = _uiState.update { it.copy(apellidos = valor, errorFormulario = null) }
    fun onPuestoCambia(valor: String) = _uiState.update { it.copy(puesto = valor, errorFormulario = null) }
    fun onFechaNacimientoCambia(valor: String) =
        _uiState.update { it.copy(fechaNacimiento = valor, errorFormulario = null) }

    /**
     * Qué CV hay y cuál es la foto, ya decodificada.
     *
     * Un fallo aquí no se le enseña a nadie: el perfil se lee igual sin
     * foto ni currículum, y un banner rojo por no haber podido bajar un
     * avatar molestaría más de lo que informa.
     */
    private fun cargarAdjuntos() {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getMisAdjuntos()
                if (!respuesta.isSuccessful) return@launch
                val adjuntos = respuesta.body().orEmpty()

                val cv = adjuntos.firstOrNull { it.tipo == TIPO_CV }
                val fotoAdjunta = adjuntos.firstOrNull { it.tipo == TIPO_FOTO }

                // Si ya no hay foto se quita la que hubiera: si no,
                // borrarla dejaría la vieja en pantalla hasta reiniciar.
                _uiState.update {
                    it.copy(cv = cv, foto = if (fotoAdjunta == null) null else it.foto)
                }
                if (fotoAdjunta != null) {
                    descargarFoto(fotoAdjunta.id)
                }
            } catch (_: Exception) {
                // Ver el comentario de arriba.
            }
        }
    }

    private suspend fun descargarFoto(adjuntoId: Long) {
        try {
            val respuesta = authRepository.descargarAdjunto(adjuntoId)
            val cuerpo = respuesta.body()
            if (!respuesta.isSuccessful || cuerpo == null) return

            // Decodificar fuera del hilo principal. Son 30 KB, pero
            // decodificar imágenes en el hilo de la interfaz es
            // exactamente lo que produce tirones sin motivo aparente.
            val mapa = withContext(Dispatchers.IO) {
                cuerpo.byteStream().use { BitmapFactory.decodeStream(it) }
            }
            if (mapa != null) {
                _uiState.update { it.copy(foto = mapa.asImageBitmap()) }
            }
        } catch (_: Exception) {
            // Igual que en cargarAdjuntos.
        }
    }

    /**
     * Sube un CV o una foto.
     *
     * Los bytes llegan ya leídos desde la pantalla, que es quien tiene
     * el Context para resolver el Uri elegido — el mismo reparto que en
     * la descarga de informes, donde el ViewModel devuelve el cuerpo y
     * la pantalla escribe el fichero.
     *
     * **No se valida el tipo aquí**: lo decide el servidor mirando los
     * primeros bytes. Comprobar la extensión en el móvil daría una falsa
     * sensación de control y dejaría pasar exactamente lo mismo.
     */
    fun subirAdjunto(contenido: ByteArray, nombre: String, mime: String, tipo: String) {
        if (contenido.isEmpty()) {
            _uiState.update {
                it.copy(errorFormulario = MensajeUi.Recurso(R.string.perfil_adjunto_vacio))
            }
            return
        }
        _uiState.update { it.copy(subiendo = true, errorFormulario = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.subirAdjunto(contenido, nombre, mime, tipo)
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(subiendo = false) }
                    cargarAdjuntos()
                } else {
                    _uiState.update {
                        it.copy(subiendo = false, errorFormulario = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(subiendo = false, errorFormulario = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    /** Descarga el CV y devuelve el cuerpo a la pantalla, que lo escribe. */
    fun descargarCv(alTener: (ResponseBody, String) -> Unit) {
        val cv = _uiState.value.cv ?: return
        _uiState.update { it.copy(descargandoCv = true, errorFormulario = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.descargarAdjunto(cv.id)
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    alTener(cuerpo, cv.nombreOriginal)
                    _uiState.update { it.copy(descargandoCv = false) }
                } else {
                    _uiState.update {
                        it.copy(descargandoCv = false, errorFormulario = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                // La excepción real al log: mensajeDeRed traduce
                // cualquier fallo a "no hay conexión" y oculta la causa.
                Log.w("NxTimeAdjuntos", "Fallo al descargar el CV", e)
                _uiState.update {
                    it.copy(descargandoCv = false, errorFormulario = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    fun borrarCv() {
        val cv = _uiState.value.cv ?: return
        viewModelScope.launch {
            try {
                val respuesta = authRepository.borrarAdjunto(cv.id)
                if (respuesta.isSuccessful) {
                    _uiState.update { it.copy(cv = null) }
                } else {
                    _uiState.update { it.copy(errorFormulario = ApiErrorParser.mensajeDe(respuesta)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorFormulario = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /**
     * Cierra la sesión.
     *
     * Vive aquí desde la Fase B y ya no en `FicharViewModel`: el botón
     * está en esta pantalla, y dejar el método donde estaba el menú
     * viejo habría sido código muerto con test propio.
     */
    fun cerrarSesion() = sessionManager.clearAuthData()

    /** Vacía el estado al cerrar sesión, para no enseñar el perfil del anterior. */
    fun limpiar() {
        _uiState.value = PerfilUiState(cargando = false)
    }

    fun guardar() {
        val estado = _uiState.value

        if (estado.nombre.isBlank()) {
            _uiState.update { it.copy(errorFormulario = MensajeUi.Recurso(R.string.perfil_nombre_obligatorio)) }
            return
        }

        val fecha = estado.fechaNacimiento.trim()
        if (fecha.isNotEmpty()) {
            val comoFecha = try {
                LocalDate.parse(fecha)
            } catch (_: DateTimeParseException) {
                null
            }
            if (comoFecha == null) {
                _uiState.update { it.copy(errorFormulario = MensajeUi.Recurso(R.string.perfil_fecha_invalida)) }
                return
            }
            // El backend también lo rechaza (@Past), pero un viaje de
            // ida y vuelta para decir que no se nace mañana sobra.
            if (!comoFecha.isBefore(LocalDate.now())) {
                _uiState.update { it.copy(errorFormulario = MensajeUi.Recurso(R.string.perfil_fecha_futura)) }
                return
            }
        }

        _uiState.update { it.copy(guardando = true, errorFormulario = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.actualizarMiPerfil(
                    nombre = estado.nombre.trim(),
                    apellidos = estado.apellidos.trim(),
                    // Los de texto se pueden vaciar: la cadena vacía es
                    // "bórralo" y el backend la distingue de null.
                    //
                    // La fecha NO: es un LocalDate y no tiene cadena
                    // vacía, así que dejar el campo en blanco significa
                    // "no la toques" y no "quítamela". Se puede corregir
                    // una fecha mal puesta, pero no borrarla. Si algún
                    // día hace falta, el backend necesita distinguir los
                    // dos casos y eso no se resuelve desde aquí.
                    fechaNacimiento = fecha.ifEmpty { null },
                    puesto = estado.puesto.trim()
                )
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    _uiState.update {
                        it.copy(guardando = false, editando = false, perfil = cuerpo, errorFormulario = null)
                    }
                } else {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(guardando = false, errorFormulario = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }
}
