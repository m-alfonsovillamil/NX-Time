package com.nxtime.nxtime.domain;

/**
 * Estado de aprobación de una solicitud de ausencia.
 *
 * Constantes en español a propósito (ver Role.java): son el valor real
 * del campo "estado" en el JSON y en el CHECK constraint de la BD.
 */
public enum AbsenceStatus {
    PENDIENTE,
    APROBADA,
    RECHAZADA,
    /**
     * Retirada por quien la pidió, o anulada después de aprobarse (ADR 058).
     * Como una rechazada, ya no ocupa el calendario ni gasta vacaciones; a
     * diferencia de ella, nadie la ha denegado, y dice quién la canceló.
     */
    CANCELADA;

    /** Pendiente o aprobada: la que cuenta para el calendario y los solapes. */
    public boolean estaViva() {
        return this == PENDIENTE || this == APROBADA;
    }
}
