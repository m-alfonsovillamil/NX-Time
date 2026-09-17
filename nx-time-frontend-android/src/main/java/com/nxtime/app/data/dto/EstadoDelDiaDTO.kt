package com.nxtime.app.data.dto

/**
 * Si hoy es laborable para quien pregunta (`GET /api/v1/fichaje/hoy`).
 * No laborable = festivo o ausencia aprobada; `motivo` dice cuál.
 */
data class EstadoDelDiaDTO(
    val laborable: Boolean,
    val motivo: String? = null
)
