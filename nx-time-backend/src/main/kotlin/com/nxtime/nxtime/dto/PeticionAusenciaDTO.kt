package com.nxtime.nxtime.dto

import com.nxtime.nxtime.dominio.TipoAusencia
import java.time.LocalDate

/**
 * DTO que usa el frontend para enviar la información de una nueva solicitud de ausencia.
 */
data class PeticionAusenciaDTO(
    val fechaInicio: LocalDate,
    val fechaFin: LocalDate,
    val tipo: TipoAusencia,
    val motivo: String?
)