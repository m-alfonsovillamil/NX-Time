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
    RECHAZADA
}
