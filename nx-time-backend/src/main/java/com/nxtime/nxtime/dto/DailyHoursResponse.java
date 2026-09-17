package com.nxtime.nxtime.dto;

import java.time.LocalDate;

/**
 * Un día del gráfico de horas de la pantalla de inicio.
 *
 * @param minutosTrabajados netos, de las jornadas CERRADAS que empezaron ese
 *     día en España (la abierta la suma la app, que ya lleva el cronómetro).
 * @param minutosEsperados la parte diaria de la jornada contratada si el día
 *     es laborable para esta persona; 0 en fin de semana, festivo o ausencia
 *     aprobada. Es la línea de referencia del gráfico.
 * @param festivo el nombre del festivo, o null.
 * @param ausencia el tipo de ausencia aprobada que cubre el día, o null.
 *     Los dos van con nombre y no como booleano para que el gráfico pueda
 *     decir POR QUÉ ese día no se esperaba trabajo.
 */
public record DailyHoursResponse(
        LocalDate fecha,
        long minutosTrabajados,
        long minutosEsperados,
        String festivo,
        String ausencia
) {
}
