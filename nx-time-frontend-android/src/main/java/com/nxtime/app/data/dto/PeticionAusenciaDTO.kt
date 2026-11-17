package com.nxtime.app.data.dto

/**
 * DTO que la app Android ENVIARÁ al backend para crear una solicitud.
 */
data class PeticionAusenciaDTO(
    val fechaInicio: String,
    val fechaFin: String,
    val tipo: TipoAusencia,
    val motivo: String?,
    // ADR 063: solo con fechaInicio = fechaFin. Nulo (Gson no lo manda) si no
    // se pide, que es lo que entiende también un servidor anterior.
    val medioDia: Boolean? = null
)