package com.nxtime.nxtime.domain;

/**
 * Estado de fichaje de un empleado en este preciso momento (Fase 10).
 *
 * No se guarda en ninguna columna: se DERIVA de si tiene una jornada
 * abierta y de si esa jornada está en pausa. Guardarlo sería un cuarto
 * sitio donde el mismo hecho puede quedar desincronizado.
 *
 * Constantes en español a propósito (ver Role.java): son el valor real
 * que viaja en el JSON.
 */
public enum WorkStatus {
    SIN_JORNADA,
    TRABAJANDO,
    EN_PAUSA
}
