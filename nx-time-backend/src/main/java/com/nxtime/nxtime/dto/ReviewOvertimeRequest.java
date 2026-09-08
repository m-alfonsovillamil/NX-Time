package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * La decisión sobre un aviso de horas extra (Fase F).
 *
 * {@code aceptar} true = sí eran horas extra y descuentan de la bolsa
 * anual; false = no lo eran (intensiva pactada, turno partido mal
 * fichado, guardia) y el aviso se archiva sin consumir bolsa.
 *
 * La justificación es obligatoria justo en el caso en que se descarta el
 * exceso, y esa asimetría es deliberada: aceptar unas horas extra que el
 * reloj ya ha medido no necesita explicación, pero decidir que once
 * horas trabajadas no cuentan sí — es la decisión que un inspector
 * querría ver motivada.
 */
public record ReviewOvertimeRequest(
        @NotNull Boolean aceptar,
        @Size(max = 500) String justificacion
) {
}
