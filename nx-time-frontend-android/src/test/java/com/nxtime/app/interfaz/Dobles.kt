package com.nxtime.app.interfaz

import com.nxtime.app.data.repository.AuthRepository
import okhttp3.ResponseBody.Companion.toResponseBody
import org.mockito.Mockito
import org.mockito.kotlin.mock
import retrofit2.Response
import kotlin.coroutines.Continuation

/**
 * Un servidor que no responde a nada: cada petición vuelve con un 503.
 *
 * Para los tests que montan media aplicación (la navegación entera) y no
 * quieren decir qué devuelve cada una de las decenas de llamadas que hacen las
 * pantallas al abrirse. Un doble de Mockito a secas devolvería `null` donde el
 * código espera una respuesta, y lo que se estaría probando sería cómo se
 * cae la app con un `null` que el servidor de verdad nunca manda. Con un 503
 * se prueba algo que sí pasa: que las pantallas se pintan con el servidor caído.
 *
 * Lo que un test necesite que funcione, lo define encima con `stub`.
 */
fun servidorCaido(): AuthRepository = mock(defaultAnswer = { llamada ->
    val esSuspend = llamada.method.parameterTypes.lastOrNull() == Continuation::class.java
    if (esSuspend) {
        Response.error<Any>(503, """{"status":503,"detail":"Servicio no disponible."}""".toResponseBody(null))
    } else {
        Mockito.RETURNS_DEFAULTS.answer(llamada)
    }
})
