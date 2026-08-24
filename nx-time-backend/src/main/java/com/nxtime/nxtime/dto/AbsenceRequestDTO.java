package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AbsenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO que usa el frontend para enviar la información de una nueva solicitud de ausencia.
 */
public record AbsenceRequestDTO(

        @NotNull(message = "La fecha de inicio es obligatoria.")
        LocalDate fechaInicio,

        @NotNull(message = "La fecha de fin es obligatoria.")
        LocalDate fechaFin,

        @NotNull(message = "El tipo de ausencia es obligatorio.")
        AbsenceType tipo,

        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres.")
        String motivo
) {
}
