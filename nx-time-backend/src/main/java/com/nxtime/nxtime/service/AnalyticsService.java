package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.AnalyticsGrouping;
import com.nxtime.nxtime.domain.AnalyticsPeriod;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenteeismResponse;
import com.nxtime.nxtime.dto.AnalyticsSummaryResponse;
import com.nxtime.nxtime.dto.PunctualityResponse;
import java.time.LocalDate;

/**
 * Analítica de absentismo y puntualidad (Fase B4, ADR 026).
 *
 * El alcance lo decide quién pregunta, no lo que pide: RRHH y ADMIN ven la
 * empresa; un GESTOR, su departamento, y si no tiene departamento no ve nada
 * (409). Ver {@link com.nxtime.nxtime.domain.AnalyticsScope}.
 *
 * El periodo es el mes, trimestre o año natural que contiene {@code fecha}, y
 * se cuenta hasta ayer: el día de hoy no ha terminado.
 */
public interface AnalyticsService {

    AnalyticsSummaryResponse resumen(User actor, AnalyticsPeriod periodo, LocalDate fecha);

    AbsenteeismResponse absentismo(User actor, AnalyticsPeriod periodo, LocalDate fecha, AnalyticsGrouping agrupar);

    PunctualityResponse puntualidad(User actor, AnalyticsPeriod periodo, LocalDate fecha, AnalyticsGrouping agrupar);
}
