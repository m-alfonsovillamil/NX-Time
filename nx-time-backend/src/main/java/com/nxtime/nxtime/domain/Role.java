package com.nxtime.nxtime.domain;

/**
 * Roles de usuario en el sistema.
 *
 * Los nombres de las constantes se mantienen en español a propósito: son
 * el valor real que viaja en el JSON (campo "rol") y el que Spring
 * Security concatena en "ROLE_" + name() para las comprobaciones de
 * autorización. Cambiarlos rompería el contrato con la app Android y las
 * reglas @PreAuthorize existentes.
 */
public enum Role {
    EMPLEADO,
    GESTOR
}
