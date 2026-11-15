package com.nxtime.nxtime.domain;

/**
 * Cómo se compensan unas horas extra aceptadas (ADR 061): se pagan, o se
 * descansan dentro de los cuatro meses siguientes, y entonces no cuentan para el
 * tope de 80 horas al año (art. 35.2 ET).
 */
public enum CompensacionDeHorasExtra {
    PAGO,
    DESCANSO;

    /** Cuánto hay para descansarlas: los cuatro meses del art. 35.2 ET. */
    public static final int MESES_PARA_DESCANSAR = 4;
}
