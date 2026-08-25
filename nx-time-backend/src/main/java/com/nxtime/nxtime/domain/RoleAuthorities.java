package com.nxtime.nxtime.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Traduce cada {@link Role} a un conjunto de authorities granulares
 * ("fichaje:leer", "ausencia:aprobar"...), en vez de comprobar el rol
 * directamente en cada endpoint ("hasRole('GESTOR')"). Es el enfoque
 * que se usa en producción: separa "qué puede hacer" (la authority, lo
 * que de verdad importa en un @PreAuthorize) de "qué rol tiene" (una
 * forma de asignar permisos entre varias posibles).
 *
 * Cada rol hereda las authorities del anterior en la jerarquía
 * EMPLEADO &lt; GESTOR &lt; RRHH &lt; ADMIN, así que un ADMIN puede hacer
 * todo lo que puede hacer un EMPLEADO.
 *
 * "gestor:crear" (dar de alta a otro GESTOR/RRHH/ADMIN) solo la tiene
 * ADMIN: antes cualquier GESTOR podía crear otro GESTOR sin límite (ver
 * auditoría, defectos de diseño) -- ahora solo quien administra la
 * empresa puede conceder poder de gestión a otra persona.
 * "empleado:gestionar" (dar de baja/alta a un empleado) la tienen RRHH
 * y ADMIN: es la autoridad que usa el nuevo endpoint de desactivación
 * de usuarios.
 */
public final class RoleAuthorities {

    private RoleAuthorities() {
    }

    private static final Set<String> EMPLEADO = Set.of(
            "fichaje:leer",
            "fichaje:escribir",
            "ausencia:leer",
            "ausencia:escribir"
    );

    private static final Set<String> GESTOR = union(EMPLEADO, Set.of(
            "fichaje:leer:equipo",
            "ausencia:aprobar",
            "ausencia:leer:equipo",
            "empleado:crear",
            "empleado:leer"
    ));

    private static final Set<String> RRHH = union(GESTOR, Set.of(
            "empleado:gestionar"
    ));

    private static final Set<String> ADMIN = union(RRHH, Set.of(
            "gestor:crear"
    ));

    public static Set<String> forRole(Role role) {
        return switch (role) {
            case EMPLEADO -> EMPLEADO;
            case GESTOR -> GESTOR;
            case RRHH -> RRHH;
            case ADMIN -> ADMIN;
        };
    }

    private static Set<String> union(Set<String> base, Set<String> extra) {
        Set<String> result = new LinkedHashSet<>(base);
        result.addAll(extra);
        return Set.copyOf(result);
    }
}
