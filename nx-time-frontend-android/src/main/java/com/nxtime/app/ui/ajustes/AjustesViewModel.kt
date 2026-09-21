package com.nxtime.app.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxtime.app.R
import com.nxtime.app.data.dto.PeticionLogin
import com.nxtime.app.data.dto.SolicitudBorradoDTO
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.session.Ajustes
import com.nxtime.app.data.session.Tema
import com.nxtime.app.recordatorio.ReglaDelRecordatorio
import com.nxtime.app.ui.util.MensajeUi
import io.sentry.Sentry
import okhttp3.ResponseBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AjustesUiState(
    val tema: Tema = Tema.SISTEMA,
    val informesDeErrores: Boolean = true,
    val huella: Boolean = false,
    val recordatorio: Boolean = false,
    val horaEntrada: String = Ajustes.HORA_ENTRADA_POR_DEFECTO,
    val horaSalida: String = Ajustes.HORA_SALIDA_POR_DEFECTO,
    /** Se está pidiendo la contraseña para activar la huella. */
    val confirmandoHuella: Boolean = false,
    val verificandoContrasena: Boolean = false,
    val cerrandoSesiones: Boolean = false,
    /** Se está preparando la descarga de mis datos. */
    val descargandoDatos: Boolean = false,
    /** Mi última solicitud de borrado de datos, o null si nunca pedí ninguna. */
    val solicitudBorrado: SolicitudBorradoDTO? = null,
    /** El diálogo que explica el borrado está abierto. */
    val confirmandoBorrado: Boolean = false,
    val enviandoBorrado: Boolean = false,
    val error: MensajeUi? = null,
    val aviso: MensajeUi? = null
)

/**
 * Los ajustes de la aplicación.
 *
 * Las preferencias no viven aquí: viven en [Ajustes], que las guarda y las
 * publica para toda la aplicación (el tema lo aplica `MainActivity` sobre
 * el árbol entero). Este ViewModel solo es la pantalla que las cambia,
 * así que escribe en [Ajustes] y refleja el resultado.
 */
class AjustesViewModel(
    private val authRepository: AuthRepository,
    private val ajustes: Ajustes
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AjustesUiState(
            tema = ajustes.tema.value,
            informesDeErrores = ajustes.informesDeErrores.value,
            huella = ajustes.huella.value,
            recordatorio = ajustes.recordatorio.value,
            horaEntrada = ajustes.horaEntrada.value,
            horaSalida = ajustes.horaSalida.value
        )
    )
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    /**
     * Enciende o apaga el recordatorio de fichar.
     *
     * Aquí solo se guarda la preferencia: **programar el trabajo periódico
     * lo hace la pantalla**, que es quien tiene el Context. Es el mismo
     * reparto que en la descarga del CV, y evita meter un Context en el
     * ViewModel solo para llamar a WorkManager.
     */
    fun cambiarRecordatorio(activo: Boolean) {
        ajustes.cambiarRecordatorio(activo)
        _uiState.update { it.copy(recordatorio = activo, error = null) }
    }

    /**
     * Cambia las horas de aviso, si son horas.
     *
     * Se valida antes de guardar porque un "25:70" guardado no avisaría
     * nunca y no habría forma de saber por qué: el trabajo se programaría
     * en silencio con una hora que no existe.
     */
    fun cambiarHoras(entrada: String, salida: String) {
        if (!ReglaDelRecordatorio.esHoraValida(entrada) || !ReglaDelRecordatorio.esHoraValida(salida)) {
            _uiState.update {
                it.copy(error = MensajeUi.Recurso(R.string.ajustes_recordatorio_hora_invalida))
            }
            return
        }
        ajustes.cambiarHoras(entrada, salida)
        _uiState.update { it.copy(horaEntrada = entrada, horaSalida = salida, error = null) }
    }

    /**
     * Activar la huella **exige la contraseña**; desactivarla, no.
     *
     * La asimetría es deliberada: a quien cogiera el móvil ya desbloqueado
     * le bastaría con activar la huella y poner la suya para quedarse con
     * la cuenta de otra persona. Quitarla, en cambio, solo deja las cosas
     * como estaban -- y bloquear esa salida sería encerrar a alguien.
     */
    fun cambiarHuella(activa: Boolean) {
        if (!activa) {
            ajustes.cambiarHuella(false)
            _uiState.update { it.copy(huella = false, confirmandoHuella = false) }
            return
        }
        _uiState.update { it.copy(confirmandoHuella = true, error = null) }
    }

    fun cancelarActivacionDeHuella() =
        _uiState.update { it.copy(confirmandoHuella = false, error = null) }

    /**
     * Comprueba la contraseña contra el servidor y, si es la suya, activa
     * la huella.
     *
     * El correo sale del perfil y no de la sesión guardada: `SessionManager`
     * guarda el nombre y el rol, pero no el correo.
     */
    fun confirmarHuellaCon(contrasena: String) {
        if (contrasena.isBlank() || _uiState.value.verificandoContrasena) return
        _uiState.update { it.copy(verificandoContrasena = true, error = null) }
        viewModelScope.launch {
            try {
                // Un endpoint que solo dice sí o no, en vez de un login
                // completo (Fase A11). Hacer login para comprobar la
                // contraseña tenía tres efectos que nadie quería: emitía
                // tokens nuevos y los guardaba encima de los que había, dejaba
                // vivo en el servidor el refresh anterior --sesiones abiertas
                // acumulándose-- y consumía el límite de intentos del login,
                // así que activar la huella varias veces podía dejar a alguien
                // sin poder entrar.
                //
                // Con la rotación de refresh tokens el segundo punto pasa de
                // molesto a incorrecto, así que esto dejó de ser una comodidad.
                //
                // De paso se ahorra la consulta del perfil: ya no hace falta
                // saber el correo, porque el servidor comprueba contra quien
                // trae el token.
                val respuesta = authRepository.verificarContrasena(contrasena)
                if (respuesta.isSuccessful) {
                    ajustes.cambiarHuella(true)
                    _uiState.update {
                        it.copy(
                            huella = true,
                            confirmandoHuella = false,
                            verificandoContrasena = false,
                            aviso = MensajeUi.Recurso(R.string.ajustes_huella_activada)
                        )
                    }
                } else {
                    // El 401 de una contraseña que no es la suya se explica
                    // con el mensaje del servidor, como en el login.
                    _uiState.update {
                        it.copy(verificandoContrasena = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(verificandoContrasena = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    fun cambiarTema(nuevo: Tema) {
        ajustes.cambiarTema(nuevo)
        _uiState.update { it.copy(tema = nuevo) }
    }

    /**
     * Enciende o apaga el envío de informes de errores.
     *
     * **Apagarlo tiene efecto en el momento** (`Sentry.close()`): si
     * alguien lo desactiva es porque no quiere que salga nada más de su
     * móvil, y esperar al siguiente arranque sería no hacerle caso.
     * Encenderlo, en cambio, se aplica al arrancar, que es cuando Sentry
     * puede inicializarse (ver `NxTimeApplication.iniciarSentry`).
     */
    fun cambiarInformesDeErrores(activos: Boolean) {
        ajustes.cambiarInformesDeErrores(activos)
        _uiState.update { it.copy(informesDeErrores = activos) }
        if (!activos) Sentry.close()
    }

    /**
     * Cierra la sesión en todos los dispositivos.
     *
     * El servidor revoca los refresh tokens —incluido el de este móvil—,
     * así que después se sale al login: quedarse dentro con un token que
     * ya no se puede renovar daría una sesión que muere sola al rato, y
     * sin explicación.
     */
    /**
     * Descarga todos mis datos (RGPD, arts. 15 y 20).
     *
     * Como en el panel de empresa, el fichero no se escribe aquí: se devuelve
     * el cuerpo a la pantalla, que es quien tiene el `Context`. Un ViewModel
     * que escribe ficheros necesita el contexto de la aplicación y es mucho
     * más difícil de probar.
     *
     * No pide confirmación ni aprobación de nadie: son los datos de uno mismo.
     */
    fun descargarMisDatos(formato: FormatoDeExportacion, alTener: (ResponseBody, String) -> Unit) {
        if (_uiState.value.descargandoDatos) return
        _uiState.update { it.copy(descargandoDatos = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = when (formato) {
                    FormatoDeExportacion.PDF -> authRepository.descargarMisDatosPdf()
                    FormatoDeExportacion.JSON -> authRepository.descargarMisDatosJson()
                }
                val cuerpo = respuesta.body()
                if (respuesta.isSuccessful && cuerpo != null) {
                    alTener(cuerpo, "nxtime-mis-datos.${formato.extension}")
                    _uiState.update { it.copy(descargandoDatos = false) }
                } else {
                    _uiState.update {
                        it.copy(descargandoDatos = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(descargandoDatos = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    /**
     * Carga mi última solicitud de borrado. La llama la pantalla al abrirse y
     * no el `init`, para que los tests de las demás preferencias no tengan que
     * simular una llamada que no les importa.
     *
     * Si falla no se enseña error: la tarjeta se ve como si no hubiera
     * ninguna, y pedirla otra vez daría el 409 que lo explica.
     */
    fun cargarSolicitudBorrado() {
        viewModelScope.launch {
            try {
                val respuesta = authRepository.getMiSolicitudBorrado()
                if (respuesta.isSuccessful) {
                    // 204 = nunca pedí ninguna: cuerpo null, y es lo correcto.
                    _uiState.update { it.copy(solicitudBorrado = respuesta.body()) }
                }
            } catch (e: Exception) {
                // Ver arriba.
            }
        }
    }

    fun pedirBorrado() = _uiState.update { it.copy(confirmandoBorrado = true, error = null) }

    fun cancelarPeticionDeBorrado() = _uiState.update { it.copy(confirmandoBorrado = false) }

    /**
     * Envía la solicitud. **No borra nada**: la ejecuta RRHH o ADMIN después de
     * comprobar que no queda nada abierto (ADR 016).
     */
    fun confirmarBorrado(motivo: String) = enviarBorrado(R.string.ajustes_borrar_datos_enviada) {
        authRepository.solicitarBorrado(motivo)
    }

    fun retirarBorrado() = enviarBorrado(R.string.ajustes_borrar_datos_retirada) {
        authRepository.cancelarBorrado()
    }

    private fun enviarBorrado(
        avisoOk: Int,
        accion: suspend () -> retrofit2.Response<SolicitudBorradoDTO>
    ) {
        if (_uiState.value.enviandoBorrado) return
        _uiState.update { it.copy(enviandoBorrado = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = accion()
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            enviandoBorrado = false,
                            confirmandoBorrado = false,
                            solicitudBorrado = respuesta.body(),
                            aviso = MensajeUi.Recurso(avisoOk)
                        )
                    }
                } else {
                    // El diálogo se queda abierto: lo escrito en el motivo no
                    // se pierde por un fallo que quizá se arregla reintentando.
                    _uiState.update {
                        it.copy(enviandoBorrado = false, error = ApiErrorParser.mensajeDe(respuesta))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(enviandoBorrado = false, error = ApiErrorParser.mensajeDeRed(e)) }
            }
        }
    }

    fun cerrarTodasLasSesiones(alCerrar: () -> Unit) {
        if (_uiState.value.cerrandoSesiones) return
        _uiState.update { it.copy(cerrandoSesiones = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = authRepository.cerrarTodasLasSesiones()
                if (respuesta.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            cerrandoSesiones = false,
                            aviso = MensajeUi.Recurso(R.string.ajustes_cerrar_todas_hecho)
                        )
                    }
                    alCerrar()
                } else {
                    _uiState.update {
                        it.copy(
                            cerrandoSesiones = false,
                            error = ApiErrorParser.mensajeDe(respuesta)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(cerrandoSesiones = false, error = ApiErrorParser.mensajeDeRed(e))
                }
            }
        }
    }

    fun descartarError() = _uiState.update { it.copy(error = null) }
}

/**
 * El mismo contenido en dos formatos: el PDF para leerlo, y el JSON porque el
 * derecho de portabilidad pide un formato "estructurado y de lectura mecánica".
 */
enum class FormatoDeExportacion(val extension: String, val mime: String) {
    PDF("pdf", "application/pdf"),
    JSON("json", "application/json")
}
