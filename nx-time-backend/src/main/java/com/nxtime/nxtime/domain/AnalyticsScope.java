package com.nxtime.nxtime.domain;

/**
 * Qué parte de la empresa abarca una respuesta de la analítica (Fase B4).
 *
 * No lo elige quien pregunta: sale de quién es. Quien gestiona la plantilla
 * ({@code empleado:gestionar}: RRHH y ADMIN) ve la empresa; un GESTOR, su
 * departamento. Es un filtro de datos y no un permiso de operación, por eso lo
 * aplica el servicio y no un {@code @PreAuthorize}.
 */
public enum AnalyticsScope {
    EMPRESA,
    DEPARTAMENTO
}
