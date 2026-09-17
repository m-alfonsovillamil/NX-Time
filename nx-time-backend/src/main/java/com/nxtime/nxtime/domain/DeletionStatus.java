package com.nxtime.nxtime.domain;

/** Estado de una solicitud de borrado de datos (ADR 016). */
public enum DeletionStatus {
    /** La persona lo ha pedido y espera a RRHH/ADMIN. */
    PENDIENTE,
    /** Se purgó lo prescindible y la cuenta se desactivó. Falta anonimizar. */
    EJECUTADA,
    /** RRHH/ADMIN no la ejecutó, y dijo por qué. */
    RECHAZADA,
    /** La propia persona la retiró antes de que se resolviera. */
    CANCELADA
}
