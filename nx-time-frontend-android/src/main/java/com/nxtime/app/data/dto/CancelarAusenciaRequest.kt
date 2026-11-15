package com.nxtime.app.data.dto

/**
 * Cuerpo de POST /api/v1/ausencias/{id}/cancelar (ADR 058).
 *
 * @param motivo opcional al retirar una propia; obligatorio al anular la de
 *   otra persona (el backend responde 400 sin él).
 */
data class CancelarAusenciaRequest(
    val motivo: String?
)
