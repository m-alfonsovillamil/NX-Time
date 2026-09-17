package com.nxtime.app.data.dto

/**
 * Una solicitud de borrado de datos personales (RGPD, art. 17; ADR 016).
 *
 * `estado` va como texto por lo mismo que en [CorreccionDTO]: un valor que
 * esta versión no conozca no debe quedarse a `null` en silencio.
 *
 * `bloqueos` lo calcula el servidor para quien pregunta: lo que hoy impide
 * ejecutarla, en frases para leer. Vacía si se puede, y vacía siempre fuera
 * de la bandeja. La app no conoce las reglas, solo las enseña.
 */
data class SolicitudBorradoDTO(
    val id: Long,
    val usuarioId: Long,
    val nombre: String,
    val email: String,
    val estado: String,
    val motivo: String? = null,
    /** Quién la registró en nombre de la persona; null si la pidió ella. Entonces `motivo` dice cómo llegó. */
    val registradaPor: String? = null,
    val creadaEn: String? = null,
    val resueltaPor: String? = null,
    val resueltaEn: String? = null,
    val comentarioResolucion: String? = null,
    /** Desde qué día se anonimiza. Solo en EJECUTADA. */
    val anonimizarDesde: String? = null,
    val bloqueos: List<String> = emptyList()
) {
    val pendiente: Boolean get() = estado == ESTADO_PENDIENTE
    val rechazada: Boolean get() = estado == ESTADO_RECHAZADA

    companion object {
        const val ESTADO_PENDIENTE = "PENDIENTE"
        const val ESTADO_RECHAZADA = "RECHAZADA"
    }
}

/** El motivo es opcional: el RGPD no obliga a dar razones. */
data class PeticionBorrado(val motivo: String?)

/** Rechazar exige decir por qué. */
data class RechazoBorrado(val comentario: String)

/**
 * Alguien para quien RRHH/ADMIN puede registrar una solicitud recibida fuera
 * de la app. Incluye a quien está de baja, que es el caso para el que existe.
 */
data class CandidatoBorradoDTO(
    val id: Long,
    val nombre: String,
    val email: String,
    val activo: Boolean
)

/** Registrar una solicitud recibida por correo, carta o en persona. `motivo` dice cómo llegó. */
data class RegistroBorrado(val usuarioId: Long, val motivo: String)
