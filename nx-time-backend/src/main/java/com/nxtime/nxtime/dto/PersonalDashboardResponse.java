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
        VacationBalanceResponse saldoVacaciones,

        /**
         * La jornada esperada del usuario, en minutos por semana.
         *
         * Sale de {@code User.horasSemanales}, que existía desde la Fase
         * 9 con 40 h por defecto -- la jornada máxima ordinaria en España
         * (art. 34 ET) -- pero que **no leía nadie ni salía del
         * backend**: era un dato muerto. Se expone para que el cliente
         * pueda decir "llevas 32 de 40 h" en vez de un número suelto que
         * no se compara con nada.
         *
         * En minutos y no en horas decimales, por lo mismo que el resto
         * de este record: 37,5 h obliga al cliente a arrastrar coma
         * flotante para una cuenta que es exacta.
         */
        long minutosJornadaSemanal
) {
}
