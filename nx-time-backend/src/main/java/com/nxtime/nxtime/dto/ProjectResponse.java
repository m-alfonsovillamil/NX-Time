package com.nxtime.nxtime.dto;

import java.time.LocalDate;

/**
 * Un proyecto tal y como se lista.
 *
 * {@code asignados} es cuánta gente ha pasado por él (histórico, no solo
 * los vigentes hoy) y viaja con el listado por el mismo motivo que
 * {@code empleados} en {@link DepartmentResponse}: es lo que decide si
 * se puede ofrecer el botón de borrar, porque un proyecto con
 * asignaciones no se borra. Enterarse por un 409 después de pulsar es
 * peor que no poder pulsar.
 *
 * {@code activo} y {@code fechaFin} llegan los dos porque no significan
 * lo mismo: un proyecto puede haber terminado en plazo o haberse
 * cancelado sin fecha.
 */
public record ProjectResponse(
        long id,
        String codigo,
        String nombre,
        String descripcion,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        boolean activo,
        long asignados
) {
}
