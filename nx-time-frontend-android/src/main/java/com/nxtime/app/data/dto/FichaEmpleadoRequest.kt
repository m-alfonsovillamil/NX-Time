package com.nxtime.app.data.dto

/**
 * Cuerpo de `PATCH /api/v1/gestor/empleados/{id}/ficha`.
 *
 * Los dos campos son opcionales y **null significa "no tocar"**: es lo
 * que espera el backend de un PATCH, y permite guardar solo lo que se
 * ha cambiado.
 *
 * `horasSemanales` viaja como cadena y no como `Double` a propósito. La
 * columna es un `NUMERIC(4,1)` y el valor sale de un campo de texto que
 * la pantalla ya ha validado; convertirlo a coma flotante por el camino
 * solo abre la puerta a que llegue un 37.499999 al servidor.
 */
data class FichaEmpleadoRequest(
    val horasSemanales: String? = null,
    val diasVacaciones: Int? = null
)
