package com.nxtime.app.data.network

import com.nxtime.app.data.dto.AvisoDTO
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Que una página llega entera y con sus elementos TIPADOS (Fase A7).
 *
 * Los tests de los ViewModel usan un repositorio falso, así que nunca pasan
 * por Retrofit ni por Gson. Y el riesgo de un genérico está justo ahí: si el
 * tipo del contenido se perdiera por el camino, Gson rellenaría la lista con
 * `LinkedTreeMap` en vez de `AvisoDTO`, compilaría, y la app reventaría con un
 * `ClassCastException` al pintar la primera tarjeta.
 *
 * Se monta Retrofit igual que `RetrofitClient` (el conversor de Gson por
 * defecto) y un interceptor contesta con un JSON copiado del backend de verdad.
 */
class PaginaDeserializacionTest {

    /** Respuesta real de `GET /api/v1/avisos?tamano=2` con los datos de demo, el 26/09/2026. */
    private val json = """
        {"contenido":[{"id":28,"tipo":"HORAS_EXTRA_DETECTADAS","titulo":"Exceso de jornada de la semana del 07/09/2026 al 13/09/2026","cuerpo":"2 h 35 min por encima de 37 h 30 min. Pendiente de revisar.","rutaDestino":"horas-extra","leido":false,"creadoEn":"2026-09-26T12:55:08.812099Z"},{"id":27,"tipo":"HORAS_EXTRA_DETECTADAS","titulo":"Exceso de jornada de la semana del 14/09/2026 al 20/09/2026","cuerpo":"5 h 35 min por encima de 37 h 30 min. Pendiente de revisar.","rutaDestino":"horas-extra","leido":false,"creadoEn":"2026-09-26T12:55:08.812099Z"}],"pagina":0,"tamano":2,"totalElementos":8,"totalPaginas":4,"hayMas":true}
    """.trimIndent()

    private var urlPedida: String? = null

    private val api: ApiService = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor(Interceptor { cadena ->
                    urlPedida = cadena.request().url.toString()
                    Response.Builder()
                        .request(cadena.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(json.toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    @Test
    fun `una pagina de avisos llega con los avisos tipados y los campos de la pagina`() = runTest {
        val pagina = api.getAvisos(pagina = 0, tamano = 2).body()!!

        val primero: AvisoDTO = pagina.contenido.first()
        assertEquals(28L, primero.id)
        assertEquals("horas-extra", primero.rutaDestino)
        assertEquals(listOf(28L, 27L), pagina.contenido.map { it.id })
        assertEquals(8L, pagina.totalElementos)
        assertEquals(4, pagina.totalPaginas)
        assertTrue(pagina.hayMas)
    }

    @Test
    fun `la pagina y el tamano viajan como parametros`() = runTest {
        api.getAvisos(pagina = 3, tamano = 20)

        assertTrue(urlPedida!!.endsWith("api/v1/avisos?pagina=3&tamano=20"))
    }
}
