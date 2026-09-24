package com.nxtime.nxtime.domain;

/**
 * Qué no cuadró un día con el cuadrante (Fase B2). Los nombres son los del
 * CHECK {@code ck_incidencias_tipo} de V32.
 */
public enum ScheduleIncidentType {

    /** La primera entrada del día llegó más tarde que el primer tramo, pasada la tolerancia. */
    RETRASO,

    /**
     * La última salida del día fue antes que el fin del último tramo, pasada la
     * tolerancia. Solo se mira si todas las jornadas de ese día están cerradas
     * y ninguna la cerró el sistema: una hora de salida puesta a las 3:00 por
     * el cierre automático no es un dato real, y acusar de salir pronto con
     * ella sería mentir.
     */
    SALIDA_ANTICIPADA,

    /** Tocaba trabajar y no hay ningún fichaje ese día. */
    AUSENCIA
}
