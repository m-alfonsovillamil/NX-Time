package com.nxtime.nxtime.domain;

/**
 * En qué punto está una solicitud de corrección (Fase E).
 *
 * {@link #PENDIENTE} y {@link #EN_DISPUTA} son los dos estados VIVOS: la
 * solicitud sigue esperando a alguien, y por eso las dos ocupan el hueco
 * único por fichaje ({@code uq_correcciones_una_viva_por_registro}). Lo
 * que cambia entre ellas es <b>quién</b> tiene que resolver, no si hay
 * algo que resolver.
 */
public enum CorrectionStatus {

    /** Esperando a que la resuelva quien corresponda. */
    PENDIENTE,

    /**
     * Aprobada: la corrección se ha aplicado y el fichaje original queda
     * anulado. Es el único estado en el que se toca el registro.
     */
    APROBADA,

    /** Rechazada, siempre con un comentario que explica por qué. */
    RECHAZADA,

    /**
     * El dueño del fichaje no acepta la corrección que le han propuesto.
     *
     * No es un rechazo: un rechazo lo cierra, y esto lo escala. Pasa a
     * decidirlo RRHH o ADMIN en firme, porque la discrepancia es entre
     * el empleado y quien gestiona su equipo, y dejársela a cualquiera
     * de los dos sería dejar que una de las partes se dé la razón.
     */
    EN_DISPUTA;

    /** Si sigue esperando resolución, y por tanto ocupa el hueco del fichaje. */
    public boolean estaViva() {
        return this == PENDIENTE || this == EN_DISPUTA;
    }
}
