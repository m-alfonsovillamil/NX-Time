package com.nxtime.app.data.dto

/**
 * Define los estados de una solicitud (versión del frontend).
 */
enum class EstadoAusencia {
    PENDIENTE,
    APROBADA,
    RECHAZADA,

    /** Retirada por quien la pidió, o anulada por quien aprueba (ADR 058). */
    CANCELADA;

    /**
     * Si se le puede adjuntar un justificante: no a una cancelada ni a una
     * rechazada, que ya no se van a disfrutar (el servidor da 409).
     */
    val admiteJustificante: Boolean get() = this != CANCELADA && this != RECHAZADA
}