package com.nxtime.nxtime.domain;

/**
 * Roles de usuario en el sistema.
 *
 * Los nombres de las constantes se mantienen en español a propósito: son
 * el valor real que viaja en el JSON (campo "rol"). Desde la Fase 4, la
 * autorización YA NO se basa en el rol directamente ("hasRole(...)"),
 * sino en las authorities granulares que {@link RoleAuthorities} deriva
 * de cada rol -- ver esa clase para la matriz de permisos completa.
 *
 * Jerarquía (cada uno hereda los permisos del anterior):
 * EMPLEADO &lt; GESTOR &lt; RRHH &lt; ADMIN.
 */
public enum Role {
    EMPLEADO,
    GESTOR,
    RRHH,
    ADMIN
}
