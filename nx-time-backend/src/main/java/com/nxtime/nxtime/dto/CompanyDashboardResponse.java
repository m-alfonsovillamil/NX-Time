package com.nxtime.nxtime.dto;

import java.util.List;

/**
 * Panel de empresa (Fase 10), para roles de gestión: cómo va el equipo
 * este mes.
 *
 * "incidenciasAbiertas" son las jornadas que cerró el proceso nocturno
 * por no tener fichaje de salida (Fase 9) y que nadie ha corregido
 * todavía: es trabajo pendiente real de RRHH, no un dato decorativo.
 *
 * "horasExtraAbiertas" (Fase F) es lo mismo para los excesos de jornada
 * detectados y sin revisar. Está aquí y no en un correo a propósito:
 * mandar un aviso por cada exceso a cada gestor sería spam por diseño
 * -- una empresa mediana genera decenas al mes --, así que quien revisa
 * trabaja desde ESTE contador y la bandeja que hay detrás. Ver
 * NotificationEvents.OvertimeDetected.
 */
public record CompanyDashboardResponse(
        int empleadosActivos,
        long minutosMesEmpresa,
        long ausenciasPendientes,
        long incidenciasAbiertas,
        long horasExtraAbiertas,
        List<EmployeeHoursDTO> horasPorEmpleado
) {
}
