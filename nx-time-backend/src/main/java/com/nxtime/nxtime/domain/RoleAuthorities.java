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
 * "calendario:leer" (Fase C) la tiene todo el mundo -- saber qué días
 * son festivos no es un privilegio -- y "calendario:gestionar" empieza
 * en GESTOR: quien ya decide si tus vacaciones se aprueban es quien
 * sabe qué días de convenio cierra el centro. Nótese que la authority
 * NO alcanza a los festivos nacionales: esos son una fila compartida
 * por todas las empresas y los siembra el sistema, así que el límite
 * ahí no lo pone el rol sino el ámbito (ver HolidayScope).
 * "proyecto:leer" (Fase D) la tiene todo el mundo, porque saber en qué
 * proyecto estás es parte de tu propia ficha; "proyecto:gestionar"
 * empieza en GESTOR, que es quien reparte el trabajo de su equipo. Va
 * con el mismo reparto que "calendario:gestionar" y aun así es una
 * authority aparte, por el motivo de siempre: nombra la OPERACIÓN, no
 * el rol.
 * "correccion:solicitar" (Fase E) la tiene TODO EL MUNDO, y es lo más
 * importante que cambia esta fase: hasta ahora un empleado no podía
 * pedir que le corrigieran un fichaje suyo, solo esperar a que alguien
 * lo hiciera. "correccion:aprobar" empieza en GESTOR, y
 * "correccion:disputa:resolver" en RRHH -- una disputa es entre el
 * empleado y quien lleva su equipo, así que la resuelve alguien por
 * encima de los dos. "fichaje:corregir" se queda con el significado que
 * siempre tuvo, ahora explícito: pedir una corrección sobre el fichaje
 * de OTRA persona.
 * "horasextra:revisar" (Fase F) empieza en GESTOR: decidir si las once
 * horas del martes fueron horas extra o una intensiva pactada es
 * justo el conocimiento que tiene quien lleva el equipo, y no hace falta
 * subir a RRHH para eso. Nótese que NO hay authority para "ver mis
 * propias horas extra": son tuyas, y pedir permiso para mirar tu propia
 * jornada sería absurdo. Lo que la authority no concede en ningún caso
 * es revisar lo de uno mismo -- eso lo prohíbe el servicio aunque el rol
 * dé el permiso, por el mismo conflicto de interés que ya obligó a
 * separarlo en las correcciones de la Fase E.
 * "denuncia:crear" (Fase G) la tiene todo el mundo, porque un canal de
 * denuncias al que no llega todo el mundo no es un canal.
 * "denuncia:instruir" es la ÚNICA authority del proyecto que empieza en
 * ADMIN y no baja de ahí, y no es un descuido de reparto: la Ley 2/2023
 * obliga a designar un Responsable del Sistema Interno de Información, y
 * darle esa lectura también a un GESTOR haría que la denuncia sobre un
 * GESTOR la leyera él. Que la jerarquía de roles solo la conceda arriba
 * es aquí el requisito, no un efecto colateral. Si algún día hiciera
 * falta un responsable que no administre la empresa, lo correcto es un
 * rol dedicado (RESPONSABLE_CANAL) y no repartir esta authority hacia
 * abajo.
 */
public final class RoleAuthorities {

    private RoleAuthorities() {
    }

    private static final Set<String> EMPLEADO = Set.of(
            "fichaje:leer",
            "fichaje:escribir",
            "ausencia:leer",
            "ausencia:escribir",
            "adjunto:subir",
            "calendario:leer",
            "proyecto:leer",
            "correccion:solicitar",
            "denuncia:crear"
    );

    private static final Set<String> GESTOR = union(EMPLEADO, Set.of(
            "fichaje:leer:equipo",
            "ausencia:aprobar",
            "ausencia:leer:equipo",
            "empleado:crear",
            "empleado:leer",
            "calendario:gestionar",
            "proyecto:gestionar",
            "correccion:aprobar",
            "horasextra:revisar"
    ));

    private static final Set<String> RRHH = union(GESTOR, Set.of(
            "empleado:gestionar",
            "empleado:configurar",
            "departamento:gestionar",
            "correccion:disputa:resolver",
            "fichaje:corregir",
            "fichaje:auditoria",
            "informe:exportar"
    ));

    private static final Set<String> ADMIN = union(RRHH, Set.of(
            "gestor:crear",
            "denuncia:instruir"
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
