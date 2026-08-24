package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import java.time.LocalDate;

/**
 * DTO para enviar la información de una ausencia a la app.
 */
public record AbsenceResponse(
        long id,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        AbsenceType tipo,
        String motivo,
        AbsenceStatus estado,
        SimpleUserDTO usuario
) {
}
