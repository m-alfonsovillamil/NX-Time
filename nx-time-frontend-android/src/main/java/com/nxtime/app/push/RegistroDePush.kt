package com.nxtime.app.push

import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Lo que el registro necesita guardar de este móvil. En la app, [com.nxtime.app.data.session.Ajustes]. */
interface PreferenciasDePush {
    val activo: Boolean
    fun cambiar(activo: Boolean)

    /** El último token que el servidor aceptó, para poder darlo de baja. */
    var token: String?
}

/** Lo que el registro necesita de Firebase. En la app, [TokensDeFirebase]. */
interface TokensDePush {
    /** Deja a Firebase pedir tokens (está apagado hasta que se encienden los push). */
    fun activar()

    suspend fun token(): String?

    /** Invalida el token de este móvil en Google y vuelve a apagar el autoarranque. */
    fun olvidar()
}

/**
 * Cuándo este móvil se apunta para recibir push y cuándo se borra (Fase B5,
 * ADR 028). Sin Android dentro: se prueba en la JVM.
 *
 * <p>Las reglas:
 * <ul>
 *   <li><b>Se registra solo con el ajuste encendido y con sesión</b>: al
 *       encenderlo, al entrar y al arrancar la app. Registrar en cada arranque
 *       no sobra: Google puede cambiar el token, y así el servidor sabe que el
 *       móvil sigue vivo.</li>
 *   <li><b>Al apagarlo</b> se da de baja en el servidor y se olvida el token en
 *       Google.</li>
 *   <li><b>Al cerrar sesión</b>, por cualquiera de los caminos —salir, sesión
 *       caducada, cerrar todas—, solo se olvida el token en Google: para
 *       llamar al servidor haría falta la sesión que se acaba de cerrar. No
 *       hace falta: al siguiente push, Google contesta que ese token ya no
 *       existe y el servidor lo borra (ver `PushSender`). Y mientras tanto la
 *       app no enseña nada sin sesión ([debeEnsenar]).</li>
 * </ul>
 *
 * Nada de esto se le enseña a nadie si falla: el push es un extra, y los
 * avisos siguen llegando dentro de la app y por correo.
 */
class RegistroDePush(
    private val preferencias: PreferenciasDePush,
    private val tieneSesion: () -> Boolean,
    private val repositorio: () -> AuthRepository,
    private val firebase: TokensDePush,
    private val alcance: CoroutineScope
) {

    val activo: Boolean get() = preferencias.activo

    /** Si un push que acaba de llegar se enseña. Sin sesión, nunca: el móvil puede ser ya de otra persona. */
    fun debeEnsenar(): Boolean = preferencias.activo && tieneSesion()

    fun encender(): Job? {
        preferencias.cambiar(true)
        return registrarSiToca()
    }

    fun apagar(): Job {
        preferencias.cambiar(false)
        val token = preferencias.token
        preferencias.token = null
        val conSesion = tieneSesion()
        return alcance.launch {
            try {
                if (token != null && conSesion) {
                    repositorio().darDeBajaDispositivoPush(token)
                }
            } catch (_: Exception) {
                // Si no llega, el token se olvida igual en Google (abajo), y el
                // servidor lo borrará en el siguiente envío.
            } finally {
                firebase.olvidar()
            }
        }
    }

    /** Al entrar y al arrancar. No hace nada con el ajuste apagado o sin sesión. */
    fun registrarSiToca(): Job? {
        if (!debeEnsenar()) return null
        return alcance.launch {
            try {
                firebase.activar()
                val token = firebase.token() ?: return@launch
                registrar(token)
            } catch (_: Exception) {
                // Sin Google Play, sin red: sin push, y nada más.
            }
        }
    }

    /** Google ha dado un token nuevo a este móvil (`onNewToken`). */
    fun tokenNuevo(token: String): Job? {
        if (!debeEnsenar()) return null
        return alcance.launch {
            try {
                registrar(token)
            } catch (_: Exception) {
                // Se reintentará en el siguiente arranque (registrarSiToca).
            }
        }
    }

    fun alCerrarSesion() {
        // Solo si este móvil llegó a tener push: si nunca se encendió, no hay
        // token que olvidar y sería una llamada a Google para nada.
        val teniaToken = preferencias.token != null
        preferencias.token = null
        if (teniaToken || preferencias.activo) {
            firebase.olvidar()
        }
    }

    private suspend fun registrar(token: String) {
        if (repositorio().registrarDispositivoPush(token).isSuccessful) {
            preferencias.token = token
        }
    }
}
