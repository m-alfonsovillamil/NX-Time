package com.nxtime.nxtime.domain;

/**
 * Tipos de ausencia que se pueden solicitar.
 *
 * Constantes en español a propósito (ver Role.java): son el valor real
 * del campo "tipo" en el JSON y en el CHECK constraint de la BD.
 */
public enum AbsenceType {
    VACACIONES,
    ASUNTOS_PROPIOS,
    MATRIMONIO,
    FALLECIMIENTO_FAMILIAR,
    HOSPITALIZACION_FAMILIAR,
    LACTANCIA,
    MATERNIDAD_PATERNIDAD,
    MEDICO,
    TRASLADO_DOMICILIO,
    VIAJE_TRABAJO,
    OTROS
}
