package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AbsenceType;
import java.time.LocalDate;

/**
 * DTO que usa el frontend para enviar la información de una nueva solicitud de ausencia.
 */
public record AbsenceRequestDTO(LocalDate fechaInicio, LocalDate fechaFin, AbsenceType tipo, String motivo) {
}
