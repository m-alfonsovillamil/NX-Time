package com.nxtime.nxtime.domain;

/**
 * Qué datos personales de otra persona se han consultado (V44, ADR 047). Lo
 * mismo que admite el CHECK de {@code accesos_a_datos.que}.
 */
public enum DatoConsultado {
    /** El CV que congeló una candidatura. */
    CV,
    /** El perfil de otra persona. */
    PERFIL,
    /** El registro de jornada mensual (PDF) de otra persona. */
    INFORME_MENSUAL,
    /** El Excel de horas de toda la empresa. */
    INFORME_EMPRESA,
    /** Un expediente del canal de denuncias. */
    DENUNCIA,
    /** Cualquier otra descarga con datos de varias personas. */
    EXPORTACION
}
