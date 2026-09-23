package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Poner fecha de fin a una asignación de cuadrante: el último día que aplica.
 *
 * No puede ser anterior a ayer. Ayer sí, porque cerrar "hasta ayer" no cambia
 * ningún día ya pasado: solo deja sin cuadrante a partir de hoy.
 */
public record CloseScheduleAssignmentRequest(
        @NotNull(message = "Hay que indicar la fecha de fin.")
        LocalDate fechaFin
) {
}
