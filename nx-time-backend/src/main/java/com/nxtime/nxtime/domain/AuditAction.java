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
    ANULACION,

    // --- Fase E: la corrección deja de aplicarse sola ---
    //
    // Estas tres se anotan en la traza del fichaje aunque NO lo cambien,
    // y ahí está su valor: sin ellas, un intento de corrección rechazado
    // no dejaría rastro, y la traza solo contaría los cambios que
    // salieron adelante. Quién quiso cambiar qué, y quién dijo que no,
    // es justo lo que hay que poder enseñar en una inspección.

    /** Alguien ha PEDIDO corregir el fichaje. Todavía no se ha tocado. */
    SOLICITUD_CORRECCION,

    /** La solicitud se ha rechazado: el fichaje se queda como estaba. */
    RECHAZO_CORRECCION,

    /**
     * El dueño del fichaje no acepta la corrección que le proponen.
     * Escala a RRHH; no cierra nada por sí sola.
     */
    DISPUTA
}
