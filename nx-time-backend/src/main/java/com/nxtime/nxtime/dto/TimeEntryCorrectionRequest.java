package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Cuerpo de {@code PATCH /api/v1/fichaje/{id}} (Fase 8, corrección de un
 * fichaje pasado por RRHH/ADMIN). El motivo es obligatorio a propósito:
 * una corrección sin justificar no tiene valor de auditoría.
 */
public record TimeEntryCorrectionRequest(

        @NotNull(message = "La hora de entrada corregida es obligatoria.")
        Instant horaEntrada,

        @NotNull(message = "La hora de salida corregida es obligatoria.")
        Instant horaSalida,

        @NotBlank(message = "El motivo de la corrección es obligatorio.")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres.")
        String motivo
) {
}
