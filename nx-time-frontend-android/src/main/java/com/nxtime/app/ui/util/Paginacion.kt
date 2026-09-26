package com.nxtime.app.ui.util

import com.nxtime.app.data.dto.PaginaDTO
import retrofit2.Response

/**
 * Por dónde va una lista paginada (Fase A7). Los elementos viven aparte, en
 * el estado de cada pantalla, para que lo que ya las tocaba —marcar un aviso
 * como leído, quitar una corrección resuelta— siga igual.
 *
 * @param siguiente la página que toca pedir.
 * @param fallo si la última "siguiente" falló: entonces no se reintenta sola,
 *   para no quedarse pidiendo en bucle sin red; se ofrece un botón.
 */
data class EstadoDePaginas(
    val siguiente: Int = 1,
    val hayMas: Boolean = false,
    val cargandoMas: Boolean = false,
    val fallo: Boolean = false
) {
    val puedeCargarMas: Boolean get() = hayMas && !cargandoMas

    companion object {
        /** Tras recibir la primera página. */
        fun tras(pagina: PaginaDTO<*>) = EstadoDePaginas(siguiente = pagina.pagina + 1, hayMas = pagina.hayMas)
    }
}

/** Lo que devuelve [pedirSiguiente]: el estado nuevo y lo que hay que añadir al final. */
data class Siguiente<T>(val estado: EstadoDePaginas, val nuevos: List<T>)

/**
 * Pide la página siguiente y dice qué añadir.
 *
 * Quita lo que ya estaba (por [clave]): entre una página y la siguiente
 * pueden haber llegado filas nuevas arriba, que empujan una hacia abajo y la
 * harían salir dos veces. Si falla, no añade nada y marca [EstadoDePaginas.fallo].
 */
suspend fun <T, K> pedirSiguiente(
    estado: EstadoDePaginas,
    yaCargados: List<T>,
    clave: (T) -> K,
    pedir: suspend (pagina: Int) -> Response<PaginaDTO<T>>
): Siguiente<T> {
    val cuerpo = try {
        pedir(estado.siguiente).let { if (it.isSuccessful) it.body() else null }
    } catch (_: Exception) {
        null
    } ?: return Siguiente(estado.copy(cargandoMas = false, fallo = true), emptyList())

    val vistos = yaCargados.mapTo(HashSet(), clave)
    return Siguiente(
        EstadoDePaginas(siguiente = cuerpo.pagina + 1, hayMas = cuerpo.hayMas),
        cuerpo.contenido.filter { vistos.add(clave(it)) }
    )
}
