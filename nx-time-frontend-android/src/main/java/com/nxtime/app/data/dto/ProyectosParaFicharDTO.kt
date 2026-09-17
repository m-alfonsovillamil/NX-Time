package com.nxtime.app.data.dto

/**
 * Los proyectos de quien ficha (`GET /api/v1/fichaje/proyectos`, ADR 017).
 *
 * @property disponibles en los que puede fichar hoy. Con dos o más, al iniciar
 *   la jornada se pregunta en cuál; con uno, no.
 * @property enCurso el de la jornada abierta, o null.
 */
data class ProyectosParaFicharDTO(
    val disponibles: List<ProyectoParaFichar> = emptyList(),
    val enCurso: ProyectoParaFichar? = null
) {
    /** Solo tiene sentido preguntar, o dejar cambiar, si hay donde elegir. */
    val hayQueElegir: Boolean get() = disponibles.size >= 2
}

data class ProyectoParaFichar(
    val id: Long,
    val codigo: String,
    val nombre: String
)

/** Cambiar de proyecto con la jornada abierta. */
data class CambioDeProyecto(val proyectoId: Long)
