package com.nxtime.app.data.dto

/**
 * DTO que la app Android ENVIARÁ al backend para crear una solicitud.
 */
data class PeticionAusenciaDTO(
    val fechaInicio: String,
    val fechaFin: String,
    val tipo: TipoAusencia,
    val motivo: String?
)