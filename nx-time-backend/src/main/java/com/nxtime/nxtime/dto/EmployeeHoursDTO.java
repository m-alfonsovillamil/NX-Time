package com.nxtime.nxtime.dto;

/**
 * Horas trabajadas por un empleado en el periodo consultado (Fase 10).
 * Sale directamente del GROUP BY de
 * {@code TimeEntryRepository.sumarSegundosPorEmpleado}.
 *
 * Incluye a quien se ha dado de baja este mes con horas en él: trabajó, y
 * sus horas cuentan en el total. {@code activo} lo distingue, porque la
 * cifra de «empleados activos» no lo cuenta y sin la marca no cuadraban.
 */
public record EmployeeHoursDTO(
        long usuarioId,
        String nombre,
        long minutos,
        @io.swagger.v3.oas.annotations.media.Schema(description = "false si ya está de baja: sale porque trabajó este mes")
        boolean activo
) {
}
