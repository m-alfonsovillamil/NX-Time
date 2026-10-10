package com.nxtime.nxtime.domain;

/**
 * Cómo se ordena la lista de empresas del panel de plataforma (ADR 040).
 * NOMBRE es alfabético; las demás, de más a menos: las últimas en darse de
 * alta, las de más plantilla, las que han fichado más recientemente.
 */
public enum PlatformOrder {
    NOMBRE,
    ALTA,
    EMPLEADOS,
    ACTIVIDAD
}
