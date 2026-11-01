package com.nxtime.app.data.dto

/**
 * Si hoy es laborable para quien pregunta (`GET /api/v1/fichaje/hoy`).
 * No laborable = festivo o ausencia aprobada; `motivo` dice cuál. Y lo que pide
 * su empresa al fichar (ADR 055 y 056).
 */
data class EstadoDelDiaDTO(
    val laborable: Boolean,
    val motivo: String? = null,
    val preguntarModalidad: Boolean = false,
    val pedirUbicacion: Boolean = false
)
