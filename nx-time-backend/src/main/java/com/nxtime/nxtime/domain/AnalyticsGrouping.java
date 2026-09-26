package com.nxtime.nxtime.domain;

/**
 * Cómo se desglosa la analítica (Fase B4). EMPRESA no desglosa: solo el total.
 * Un GESTOR ve su departamento, así que para él DEPARTAMENTO es una sola fila.
 */
public enum AnalyticsGrouping {
    EMPRESA,
    DEPARTAMENTO,
    EMPLEADO
}
