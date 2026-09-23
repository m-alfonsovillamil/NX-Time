package com.nxtime.nxtime.domain;

/**
 * Qué hace una {@link ScheduleException} con el día.
 *
 * El nombre de cada constante es exactamente el valor del CHECK
 * {@code ck_excepciones_horario_tipo} de V31.
 */
public enum ScheduleExceptionType {

    /** Ese día no se trabaja, diga lo que diga la plantilla. Sin horas. */
    LIBRE,

    /**
     * Ese día se trabaja en este tramo EN LUGAR de lo que diga la plantilla.
     * Una jornada partida son dos excepciones del mismo día.
     */
    TRAMO
}
