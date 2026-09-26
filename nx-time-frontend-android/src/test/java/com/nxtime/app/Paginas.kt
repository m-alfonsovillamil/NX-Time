package com.nxtime.app

import com.nxtime.app.data.dto.PaginaDTO
import retrofit2.Response

/** Una respuesta con una sola página, la última (Fase A7). */
fun <T> unaPagina(contenido: List<T>): Response<PaginaDTO<T>> =
    Response.success(
        PaginaDTO(
            contenido, pagina = 0, tamano = 50, totalElementos = contenido.size.toLong(),
            totalPaginas = if (contenido.isEmpty()) 0 else 1, hayMas = false
        )
    )

/** La página [numero] de varias, diciendo si hay más detrás. */
fun <T> pagina(numero: Int, contenido: List<T>, hayMas: Boolean): Response<PaginaDTO<T>> =
    Response.success(
        PaginaDTO(contenido, pagina = numero, tamano = 50, totalElementos = 0, totalPaginas = 0, hayMas = hayMas)
    )
