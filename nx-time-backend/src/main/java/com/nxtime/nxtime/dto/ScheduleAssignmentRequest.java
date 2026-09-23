package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Asignar una plantilla a una persona, con vigencia (Fase B1).
 *
 * {@code fechaInicio} no puede ser anterior a hoy: un cuadrante que empezara
 * en el pasado reescribiría el horario teórico de días ya informados, que es
 * justo lo que la vigencia existe para impedir (ADR 023). {@code fechaFin}
 * null significa "hasta nuevo aviso".
 */
public record ScheduleAssignmentRequest(

        @NotNull(message = "Hay que decir a quién se asigna.")
        Long usuarioId,

        @NotNull(message = "Hay que decir qué plantilla se asigna.")
        Long plantillaId,

        @NotNull(message = "La fecha de inicio es obligatoria.")
        LocalDate fechaInicio,

        LocalDate fechaFin
) {
}
