package com.nxtime.nxtime.dto;

/**
 * Alguien de la empresa para quien se puede registrar una solicitud de
 * borrado: sin otra pendiente y sin un borrado ya ejecutado. Incluye a quien
 * está de baja, que es justo el caso para el que existe (ADR 016).
 */
public record DeletionCandidate(long id, String nombre, String email, boolean activo) {
}
