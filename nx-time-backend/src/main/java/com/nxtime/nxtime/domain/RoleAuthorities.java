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
 * "adjunto:subir" (Fase B2: el propio CV y la propia foto) la tiene
 * EMPLEADO, porque son SUS ficheros. Descargar un adjunto no la pide:
 * basta con estar autenticado y que sea de la misma empresa, porque un
 * gestor necesita leer el CV de su equipo. Borrar sí la pide, y además
 * el servicio comprueba que el adjunto sea tuyo.
 * "empleado:configurar" (Fase A: fijar la jornada semanal y los días de
 * vacaciones) va con los mismos roles, y aun así es una authority
 * aparte. Hoy no restringe nada que "empleado:gestionar" no restrinja
 * ya -- igual que pasa entre "fichaje:corregir", "fichaje:auditoria" e
 * "informe:exportar", que también coinciden rol a rol. Lo que separa no
 * es quién puede hacerlo sino QUÉ se está haciendo: desactivar una
 * cuenta y fijar la jornada contractual de alguien no son la misma
 * operación, y el día que un GESTOR deba poder la segunda sobre su
 * equipo sin poder nunca la primera, ese cambio es una línea aquí y
 * ningún endpoint tocado.
 * "fichaje:corregir" y "fichaje:auditoria" (Fase 8) las tienen RRHH y
 * ADMIN: corregir un fichaje pasado y ver su línea temporal de cambios
 * es una operación de cumplimiento normativo (RD-ley 8/2019), no algo
 * que un GESTOR normal deba poder hacer sobre sus propios empleados.
 * "informe:exportar" (Fase 10) va en el mismo grupo y por la misma
 * razón: el informe mensual es el documento que se entrega ante una
 * inspección, y abarca a toda la empresa, no solo al equipo de un
 * gestor.
 */
public final class RoleAuthorities {

    private RoleAuthorities() {
    }

    private static final Set<String> EMPLEADO = Set.of(
            "fichaje:leer",
            "fichaje:escribir",
            "ausencia:leer",
            "ausencia:escribir",
            "adjunto:subir"
    );

    private static final Set<String> GESTOR = union(EMPLEADO, Set.of(
            "fichaje:leer:equipo",
            "ausencia:aprobar",
            "ausencia:leer:equipo",
            "empleado:crear",
            "empleado:leer"
    ));

    private static final Set<String> RRHH = union(GESTOR, Set.of(
            "empleado:gestionar",
            "empleado:configurar",
            "departamento:gestionar",
            "fichaje:corregir",
            "fichaje:auditoria",
            "informe:exportar"
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
