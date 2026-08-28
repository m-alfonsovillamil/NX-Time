package com.nxtime.nxtime.dto;

import java.util.List;

/**
 * Panel de empresa (Fase 10), para roles de gestión: cómo va el equipo
 * este mes.
 *
 * "incidenciasAbiertas" son las jornadas que cerró el proceso nocturno
 * por no tener fichaje de salida (Fase 9) y que nadie ha corregido
 * todavía: es trabajo pendiente real de RRHH, no un dato decorativo.
 */
public record CompanyDashboardResponse(
        int empleadosActivos,
        long minutosMesEmpresa,
        long ausenciasPendientes,
        long incidenciasAbiertas,
        List<EmployeeHoursDTO> horasPorEmpleado
) {
}
