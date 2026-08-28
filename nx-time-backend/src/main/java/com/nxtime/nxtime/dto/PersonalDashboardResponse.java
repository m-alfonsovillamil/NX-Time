package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.WorkStatus;

/**
 * Panel del propio empleado (Fase 10): cuánto lleva trabajado hoy, esta
 * semana y este mes, en qué estado está ahora mismo, y qué tiene
 * pendiente.
 *
 * Los tiempos viajan en MINUTOS, no en horas decimales: "7,5 h" obliga
 * al cliente a decidir cómo redondear y a arrastrar coma flotante para
 * algo que es una cuenta exacta. Con minutos enteros, cada cliente
 * formatea como quiera (7 h 30 min) sin perder precisión.
 */
public record PersonalDashboardResponse(
        WorkStatus estadoActual,
        long minutosHoy,
        long minutosSemana,
        long minutosMes,
        long ausenciasPendientes,
        VacationBalanceResponse saldoVacaciones
) {
}
