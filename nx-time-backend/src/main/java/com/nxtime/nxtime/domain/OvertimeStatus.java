package com.nxtime.nxtime.domain;

/**
 * En qué punto está un aviso de horas extra (Fase F).
 *
 * La distinción entre {@link #JUSTIFICADO} y {@link #ACEPTADO} es lo que
 * hace útil esta tabla: <b>solo lo ACEPTADO cuenta para la bolsa anual
 * de 80 h</b> del art. 35.2 ET. Un exceso puede tener explicación —una
 * jornada intensiva pactada, un turno partido mal fichado, una
 * corrección que aún no se ha aprobado— y en ese caso no son horas
 * extra, aunque el reloj diga que sí.
 */
public enum OvertimeStatus {

    /** Detectado por el proceso nocturno; nadie lo ha mirado. */
    ABIERTO,

    /**
     * Revisado, y NO cuenta como hora extra: hay una explicación.
     * Se conserva en vez de borrarse porque la explicación es
     * justamente lo que hay que poder enseñar después.
     */
    JUSTIFICADO,

    /** Revisado y confirmado: cuenta para la bolsa anual. */
    ACEPTADO;

    /** Si suma a la bolsa de 80 h. */
    public boolean cuentaParaLaBolsa() {
        return this == ACEPTADO;
    }
}
