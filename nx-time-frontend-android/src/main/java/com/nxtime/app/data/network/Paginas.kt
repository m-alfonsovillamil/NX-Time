package com.nxtime.app.data.network

import com.nxtime.app.data.dto.PaginaDTO
import retrofit2.Response

/**
 * Cómo se piden las listas paginadas (Fase A7).
 *
 * [TAMANO] es lo que se pide para un scroll: lo bastante para llenar la
 * pantalla dos veces sin que la primera carga se note. [TAMANO_MAXIMO] es el
 * tope del servidor, y lo que se usa para juntar un periodo entero en pocas
 * peticiones.
 */
object Paginas {

    const val TAMANO = 50
    const val TAMANO_MAXIMO = 200

    /**
     * Sin esto, un servidor que siempre dijera `hayMas = true` dejaría a la
     * app pidiendo páginas para siempre. 20 páginas de 200 son 4000
     * elementos: un año de fichajes de alguien que ficha diez veces al día.
     */
    private const val PAGINAS_MAXIMAS = 20

    /**
     * Todas las páginas de una lista, juntas.
     *
     * Para las pantallas que necesitan el periodo ENTERO —el historial de un
     * mes suma sus horas; la pantalla de ausencias de un año cuenta sus
     * días—: sumar solo la primera página daría un total falso sin avisar.
     * El servidor limita esos periodos a un año, así que son pocas
     * peticiones.
     *
     * Si una página falla, se devuelve ESA respuesta de error tal cual, para
     * que la pantalla enseñe el mismo mensaje que con una lista sin paginar.
     */
    suspend fun <T> todas(pedir: suspend (pagina: Int) -> Response<PaginaDTO<T>>): Response<List<T>> {
        val todo = mutableListOf<T>()
        var pagina = 0
        while (pagina < PAGINAS_MAXIMAS) {
            val respuesta = pedir(pagina)
            val cuerpo = respuesta.body()
            if (!respuesta.isSuccessful || cuerpo == null) {
                @Suppress("UNCHECKED_CAST")
                return respuesta as Response<List<T>>
            }
            todo += cuerpo.contenido
            if (!cuerpo.hayMas) break
            pagina++
        }
        return Response.success(todo)
    }
}
