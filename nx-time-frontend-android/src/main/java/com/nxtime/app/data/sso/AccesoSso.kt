package com.nxtime.app.data.sso

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom

/** Con lo que el navegador vuelve a la app: un código que canjear, o por qué no. */
data class VueltaDeSso(val codigo: String?, val error: String?)

/**
 * Con lo que vuelve quien estaba registrando una empresa (ADR 044): un código
 * de registro ([alta]) si esa cuenta no tiene cuenta de NX Time, uno de sesión
 * ([codigo]) si ya la tiene y por tanto entra, o por qué no.
 */
data class VueltaDeRegistro(val alta: String?, val codigo: String?, val error: String?)

/** Dónde se guarda el verificador mientras la persona está en el navegador. */
interface GuardaDelVerificador {
    var verificador: String?

    /**
     * Para qué era la ida pendiente: vincular una cuenta a la sesión abierta
     * (true) o entrar (false). Se guarda con el verificador por lo mismo: al
     * volver, la app puede haber arrancado de cero.
     */
    var paraVincular: Boolean

    /** Si la ida pendiente era para registrar una empresa (ADR 044). */
    var paraRegistrar: Boolean
}

/**
 * En las preferencias de la app, y no en memoria: mientras la persona elige
 * cuenta en el navegador, el sistema puede cerrar el proceso de la app. Al
 * volver arrancaría de cero, sin el verificador, y el código no serviría.
 */
class VerificadorEnPreferencias(context: Context) : GuardaDelVerificador {
    private val prefs = context.getSharedPreferences("NXTIME_SSO", Context.MODE_PRIVATE)

    override var verificador: String?
        get() = prefs.getString(CLAVE, null)
        set(valor) {
            // commit() y no apply(): justo después de guardar se sale de la app
            // al navegador, y si el proceso muere antes de escribir, se pierde.
            prefs.edit().apply { if (valor == null) remove(CLAVE) else putString(CLAVE, valor) }.commit()
        }

    override var paraVincular: Boolean
        get() = prefs.getBoolean(CLAVE_VINCULAR, false)
        set(valor) {
            prefs.edit().putBoolean(CLAVE_VINCULAR, valor).commit()
        }

    override var paraRegistrar: Boolean
        get() = prefs.getBoolean(CLAVE_REGISTRAR, false)
        set(valor) {
            prefs.edit().putBoolean(CLAVE_REGISTRAR, valor).commit()
        }

    private companion object {
        const val CLAVE = "verificador"
        const val CLAVE_VINCULAR = "para_vincular"
        const val CLAVE_REGISTRAR = "para_registrar"
    }
}

/**
 * Entrar con Google o con Microsoft desde la app (ADR 036).
 *
 * La app no habla con el proveedor: abre el navegador en una URL del servidor,
 * y el servidor la devuelve aquí por `nxtime://sso` con un **código**. El
 * código no es la sesión: se canjea por ella en una petición de la app.
 *
 * Por la URL de vuelta no puede viajar nada que valga por sí solo, porque
 * cualquier otra app puede declarar que atiende `nxtime://`. Así que el código
 * va atado a un secreto que solo tiene esta: antes de salir se genera un
 * **verificador** aleatorio, se manda al servidor su SHA-256 (el «reto»), y
 * para canjear hay que presentar el verificador. Es PKCE.
 *
 * Eso también protege del caso contrario: un enlace `nxtime://sso?codigo=…`
 * que alguien le mande a la persona para que entre en la cuenta de otro. Sin
 * un inicio pendiente no hay verificador, y la vuelta ni se mira.
 */
class AccesoSso(
    private val guarda: GuardaDelVerificador,
    private val aleatorio: () -> ByteArray = { ByteArray(32).also(SecureRandom()::nextBytes) }
) {

    private val _vuelta = MutableStateFlow<VueltaDeSso?>(null)

    /** La vuelta de una ida para ENTRAR que nadie ha atendido todavía, o null. */
    val vuelta: StateFlow<VueltaDeSso?> = _vuelta.asStateFlow()

    private val _vueltaDeVinculo = MutableStateFlow<VueltaDeSso?>(null)

    /**
     * La vuelta de una ida para VINCULAR, aparte de [vuelta] a propósito: la
     * atiende Ajustes, no la pantalla de acceso. En un solo flujo, una vuelta
     * de vincular que nadie recogiera se la encontraría el acceso al cerrar la
     * sesión, y la tomaría por un intento de entrar.
     */
    val vueltaDeVinculo: StateFlow<VueltaDeSso?> = _vueltaDeVinculo.asStateFlow()

    private val _vueltaDeRegistro = MutableStateFlow<VueltaDeRegistro?>(null)

    /**
     * La vuelta de una ida para REGISTRAR una empresa, también aparte: la
     * atiende la pantalla de registro. Si el sistema cerró la app mientras la
     * persona estaba en el navegador, quien arranca es el acceso, que al verla
     * lleva al registro.
     */
    val vueltaDeRegistro: StateFlow<VueltaDeRegistro?> = _vueltaDeRegistro.asStateFlow()

    /**
     * Prepara una ida para entrar y devuelve la URL que hay que abrir en el
     * navegador. Empezar otra vez descarta la anterior: solo vale la última.
     */
    fun empezar(inicio: String): String = preparar(inicio, Para.ENTRAR)

    /**
     * Lo mismo, para añadir una cuenta a la sesión que ya hay abierta. El
     * navegador volverá con un código de vínculo, que no vincula nada por sí
     * solo: lo confirma la app con su sesión.
     */
    fun empezarAVincular(inicio: String): String = preparar(inicio, Para.VINCULAR)

    /**
     * Lo mismo, para registrar una empresa con esa cuenta. El navegador
     * volverá con un código de registro, que no crea nada: la app lo cambia
     * por un pase y manda con él el nombre de la empresa.
     */
    fun empezarARegistrar(inicio: String): String = preparar(inicio, Para.REGISTRAR)

    private enum class Para(val parametro: String) {
        ENTRAR(""), VINCULAR("&vincular=1"), REGISTRAR("&registro=1")
    }

    private fun preparar(inicio: String, para: Para): String {
        val verificador = base64Url(aleatorio().toByteString())
        guarda.verificador = verificador
        guarda.paraVincular = para == Para.VINCULAR
        guarda.paraRegistrar = para == Para.REGISTRAR
        _vuelta.value = null
        _vueltaDeVinculo.value = null
        _vueltaDeRegistro.value = null
        val union = if (inicio.contains('?')) '&' else '?'
        return "$inicio${union}cliente=app${para.parametro}&reto=${retoDe(verificador)}"
    }

    /**
     * Lo llama quien recibe `nxtime://sso?…`, con sus parámetros. Sin una ida
     * pendiente, se ignora. De los códigos solo se miran los de la ida que
     * había: uno de entrar no sirve a quien estaba vinculando, ni al revés.
     * Quien registraba puede volver con dos: el de registro o, si ya tenía
     * cuenta, el de entrar.
     */
    fun recibir(codigo: String?, error: String?, vinculo: String? = null, alta: String? = null) {
        if (guarda.verificador == null) return
        val motivo = error?.takeIf { it.isNotBlank() }
        when {
            guarda.paraVincular ->
                _vueltaDeVinculo.value = VueltaDeSso(codigo = vinculo?.takeIf { it.isNotBlank() }, error = motivo)
            guarda.paraRegistrar ->
                _vueltaDeRegistro.value = VueltaDeRegistro(
                    alta = alta?.takeIf { it.isNotBlank() },
                    codigo = codigo?.takeIf { it.isNotBlank() },
                    error = motivo
                )
            else -> _vuelta.value = VueltaDeSso(codigo = codigo?.takeIf { it.isNotBlank() }, error = motivo)
        }
    }

    /**
     * El verificador de la ida pendiente, que se gasta al pedirlo: vale para
     * un canje, salga bien o mal.
     */
    fun gastarVerificador(): String? {
        val verificador = guarda.verificador
        guarda.verificador = null
        guarda.paraVincular = false
        guarda.paraRegistrar = false
        _vuelta.value = null
        _vueltaDeVinculo.value = null
        _vueltaDeRegistro.value = null
        return verificador
    }

    companion object {
        /** El reto de un verificador: su SHA-256 en base64url sin relleno (PKCE, S256). */
        fun retoDe(verificador: String): String = base64Url(verificador.encodeUtf8().sha256())

        // Con okio y no con android.util.Base64, que en un test de JVM no
        // existe, ni con java.util.Base64, que pide API 26 y la app admite 24.
        private fun base64Url(bytes: ByteString): String = bytes.base64Url().trimEnd('=')
    }
}
