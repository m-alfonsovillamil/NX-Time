package com.nxtime.app.data.dto

/**
 * Cuerpo de PATCH /api/v1/ausencias/{id}/estado (Fase 9 del backend),
 * que sustituye a los dos POST anteriores (.../gestor/aprobar/{id} y
 * .../gestor/rechazar/{id}).
 *
 * El comentario es opcional al APROBAR, pero OBLIGATORIO al RECHAZAR:
 * el backend responde 400 si se rechaza sin motivo.
 */
data class CambioEstadoAusenciaRequest(
    val estado: EstadoAusencia,
    val comentario: String? = null
)
