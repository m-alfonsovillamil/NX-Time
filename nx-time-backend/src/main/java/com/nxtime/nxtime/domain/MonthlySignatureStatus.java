package com.nxtime.nxtime.domain;

/** En qué punto está una firma mensual (Fase B3). */
public enum MonthlySignatureStatus {

    /** Lo firmado sigue siendo lo que hay. Como mucho una por persona y mes. */
    VIGENTE,

    /** Una corrección posterior cambió el mes: lo firmado ya no es lo que hay. */
    INVALIDADA
}
