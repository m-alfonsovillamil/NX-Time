package com.nxtime.app.data.dto

/**
 * Una página de una lista del servidor (Fase A7 del backend, ADR 027).
 *
 * Las siete listas que crecen sin límite —historiales, avisos, correcciones y
 * ausencias— llegan así desde la versión 7 de la app. Para un scroll basta
 * con [hayMas]; los totales están por si una pantalla quiere decir "124
 * fichajes".
 */
data class PaginaDTO<T>(
    val contenido: List<T> = emptyList(),
    val pagina: Int = 0,
    val tamano: Int = 0,
    val totalElementos: Long = 0,
    val totalPaginas: Int = 0,
    val hayMas: Boolean = false
)
