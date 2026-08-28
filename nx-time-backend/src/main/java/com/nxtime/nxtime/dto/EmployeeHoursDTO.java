package com.nxtime.nxtime.dto;

/**
 * Horas trabajadas por un empleado en el periodo consultado (Fase 10).
 * Sale directamente del GROUP BY de
 * {@code TimeEntryRepository.sumarSegundosPorEmpleado}.
 */
public record EmployeeHoursDTO(
        long usuarioId,
        String nombre,
        long minutos
) {
}
