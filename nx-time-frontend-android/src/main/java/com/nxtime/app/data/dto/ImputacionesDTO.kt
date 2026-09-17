package com.nxtime.app.data.dto

/**
 * El reparto por proyecto de una jornada (`GET /api/v1/fichaje/{id}/imputaciones`,
 * ADR 017).
 *
 * @property repartoLibre la jornada es de esta semana y se puede repartir sin
 *   permiso. Si es false, el reparto se pide y lo aprueba un gestor.
 * @property disponibles los proyectos que esa persona tenía asignados ese día.
 * @property solicitudPendienteId si hay una corrección sin resolver sobre el
 *   fichaje: mientras exista no se puede repartir.
 */
data class ImputacionesDTO(
    val fichajeId: Long = 0,
    val netoMinutos: Long = 0,
    val repartoLibre: Boolean = false,
    val lineas: List<LineaImputacion> = emptyList(),
    val disponibles: List<ProyectoParaFichar> = emptyList(),
    val solicitudPendienteId: Long? = null
)

data class LineaImputacion(
    val proyectoId: Long,
    val codigo: String,
    val nombre: String,
    val minutos: Long
)

/** Lo que se manda al repartir. `motivo` solo hace falta si lo tiene que aprobar un gestor. */
data class PeticionReparto(
    val lineas: List<LineaReparto>,
    val motivo: String? = null
)

data class LineaReparto(val proyectoId: Long, val minutos: Long)
