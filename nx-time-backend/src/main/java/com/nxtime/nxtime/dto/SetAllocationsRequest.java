package com.nxtime.nxtime.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Repartir las horas de una jornada entre proyectos (ADR 017).
 *
 * @param motivo obligatorio solo cuando el reparto tiene que aprobarlo un
 *     gestor (semana pasada, o más horas de las fichadas). En el reparto libre
 *     sobra: no hay nadie a quien explicárselo.
 */
public record SetAllocationsRequest(
        @NotEmpty(message = "Hay que indicar el reparto.")
        @Valid
        // El mismo tope que CorrectionRequestDTO.reparto: no es una regla de
        // negocio sino un freno, para no recorrer miles de líneas antes de
        // rechazarlas. Ver ValidadorDeReparto.MAX_LINEAS.
        @Size(max = 50, message = "El reparto no puede tener más de 50 líneas.")
        List<Linea> lineas,

        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres.")
        String motivo
) {
    public record Linea(
            @Positive(message = "Falta el proyecto.")
            long proyectoId,

            @PositiveOrZero(message = "Los minutos no pueden ser negativos.")
            long minutos
    ) {
    }
}
