package com.nxtime.nxtime.domain;

/**
 * Tipo de cambio registrado en {@link TimeEntryAudit}.
 *
 * Constantes en español a propósito (ver Role.java): son el valor real
 * del campo "accion" en el JSON y en el CHECK constraint de la BD.
 */
public enum AuditAction {
    /** Se ha abierto una jornada nueva (fichaje INICIO). */
    CREACION,
    /** Cambio dentro del ciclo de vida normal (FIN, PAUSA_INICIO, PAUSA_FIN). */
    MODIFICACION,
    /** Un RRHH/ADMIN ha corregido horaEntrada/horaSalida a posteriori. */
    CORRECCION,
    /** El fichaje original queda anulado (sustituido por su corrección). */
    ANULACION
}
