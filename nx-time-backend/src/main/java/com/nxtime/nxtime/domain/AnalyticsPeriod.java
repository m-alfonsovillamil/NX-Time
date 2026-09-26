package com.nxtime.nxtime.domain;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/** El periodo de la analítica (Fase B4): el mes, el trimestre o el año natural que contiene una fecha. */
public enum AnalyticsPeriod {
    MES,
    TRIMESTRE,
    ANIO;

    public LocalDate inicio(LocalDate fecha) {
        return switch (this) {
            case MES -> fecha.withDayOfMonth(1);
            case TRIMESTRE -> fecha.with(IsoFields.DAY_OF_QUARTER, 1);
            case ANIO -> fecha.withDayOfYear(1);
        };
    }

    public LocalDate fin(LocalDate fecha) {
        return switch (this) {
            case MES -> fecha.with(TemporalAdjusters.lastDayOfMonth());
            case TRIMESTRE -> inicio(fecha).plusMonths(3).minusDays(1);
            case ANIO -> fecha.with(TemporalAdjusters.lastDayOfYear());
        };
    }
}
