package com.nxtime.app.data.network

import com.google.gson.JsonParser
import com.nxtime.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor

/** Lo que dice la API cuando esta app ya no se puede usar (ADR 064). */
data class AppNoValida(
    /** El paquete está retirado (la app de antes de un cambio de nombre); si no, es una versión vieja. */
    val retirada: Boolean,
    /** Dónde está la buena, o null si el servidor no lo sabe. */
    val urlDescarga: String?
)

/**
 * La app dice quién es en cada petición, y se entera de si ya no se puede usar
 * (ADR 064).
 *
 * Las cabeceras van en TODAS las peticiones, también en entrar y en renovar la
 * sesión, porque el interceptor está en el cliente base ([ArranqueEnFrio.clienteBase]).
 * Si la API responde 426, se deja dicho en [aviso], y `MainActivity` tapa la
 * app con la pantalla de actualizar.
 *
 * Es un estado y no un evento, por lo mismo que la sesión caducada: el 426
 * puede llegar antes de que haya pantalla (el registro del push al arrancar).
 * Y no se apaga: una app vieja sigue siéndolo hasta que se actualiza, que es
 * otro proceso.
 */
object VersionDeLaApp {
    const val CABECERA_VERSION = "X-App-Version"
    const val CABECERA_PAQUETE = "X-App-Id"
    const val HTTP_ACTUALIZAR = 426

    private const val RETIRADA = "APP_RETIRADA"
    private const val DESACTUALIZADA = "APP_DESACTUALIZADA"
    private const val MAXIMO_CUERPO = 16_384L

    private val _aviso = MutableStateFlow<AppNoValida?>(null)

    /** Null mientras la API atienda a esta app. */
    val aviso: StateFlow<AppNoValida?> = _aviso.asStateFlow()

    /** El aviso que lleva una respuesta de la API, o null si no es un 426 de los nuestros. */
    fun avisoDe(codigoHttp: Int, cuerpo: String?): AppNoValida? {
        if (codigoHttp != HTTP_ACTUALIZAR || cuerpo.isNullOrBlank()) return null
        return try {
            val json = JsonParser.parseString(cuerpo).asJsonObject
            val codigo = json.get("codigo")?.takeIf { it.isJsonPrimitive }?.asString
            if (codigo != RETIRADA && codigo != DESACTUALIZADA) return null
            val url = json.get("urlDescarga")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            AppNoValida(retirada = codigo == RETIRADA, urlDescarga = url)
        } catch (e: RuntimeException) {
            // Un 426 que no es de la API (un proxy): no dice nada que sepamos leer.
            null
        }
    }

    /** Pone las cabeceras y mira la respuesta. `peekBody` deja el cuerpo para quien venga detrás. */
    fun interceptor(
        version: Int = BuildConfig.VERSION_CODE,
        paquete: String = BuildConfig.APPLICATION_ID
    ): Interceptor = Interceptor { cadena ->
        val respuesta = cadena.proceed(
            cadena.request().newBuilder()
                .header(CABECERA_VERSION, version.toString())
                .header(CABECERA_PAQUETE, paquete)
                .build()
        )
        if (respuesta.code == HTTP_ACTUALIZAR) {
            avisoDe(respuesta.code, respuesta.peekBody(MAXIMO_CUERPO).string())?.let { _aviso.value = it }
        }
        respuesta
    }

    /** Solo para los tests: cada uno empieza con la app atendida. */
    internal fun olvidar() {
        _aviso.value = null
    }
}
