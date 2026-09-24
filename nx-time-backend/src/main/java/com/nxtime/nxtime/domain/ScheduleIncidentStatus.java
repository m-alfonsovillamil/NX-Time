package com.nxtime.nxtime.domain;

/**
 * En qué punto está una incidencia de cuadrante (Fase B2). Los nombres son los
 * del CHECK {@code ck_incidencias_estado} de V32.
 *
 * El mismo recorrido que las horas extra: el sistema detecta, la persona
 * explica y alguien decide. Ninguno de los cuatro estados descuenta nada.
 */
public enum ScheduleIncidentStatus {

    /** Recién detectada. La única que el barrido puede retirar si deja de proceder. */
    PENDIENTE,

    /** Quien la tiene ha dado su explicación y falta que alguien la revise. */
    JUSTIFICADA,

    /** Revisada: la explicación vale, o no hacía falta ninguna. */
    ACEPTADA,

    /** Revisada: no hay explicación que la cubra. Tampoco descuenta nada. */
    RECHAZADA;

    public boolean resuelta() {
        return this == ACEPTADA || this == RECHAZADA;
    }
}
