package com.nxtime.app.data.dto

/**
 * Define los estados de una solicitud (versión del frontend).
 */
enum class EstadoAusencia {
    PENDIENTE,
    APROBADA,
    RECHAZADA,

    /** Retirada por quien la pidió, o anulada por quien aprueba (ADR 058). */
    CANCELADA
}