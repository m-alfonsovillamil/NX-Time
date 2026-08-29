# 5. Autorización por authorities granulares, no por roles

**Estado:** aceptada · **Fecha:** agosto 2026

## Contexto

La autorización comprobaba el rol directamente, tanto en los controladores
(`@PreAuthorize("hasRole('GESTOR')")`) como, duplicada, dentro de los servicios
(`if (gestor.rol != Rol.GESTOR) throw ...`).

Con dos roles funcionaba. Al añadir **RRHH** y **ADMIN** el problema se hizo
evidente: cada permiso nuevo obligaba a repasar todos los controladores y a
enumerar roles a mano (`hasAnyRole('GESTOR','RRHH','ADMIN')`), con el riesgo de
olvidarse de uno. Y las comprobaciones duplicadas en los servicios quedaban
desincronizadas.

## Decisión

Separar **qué se puede hacer** de **quién eres**:

- Los endpoints exigen **permisos concretos**:
  `@PreAuthorize("hasAuthority('ausencia:aprobar')")`.
- Una única clase, `RoleAuthorities`, traduce cada rol a su conjunto de
  permisos, con herencia acumulativa: **EMPLEADO < GESTOR < RRHH < ADMIN**.
- Se eliminaron las comprobaciones de rol duplicadas en los servicios.

```
EMPLEADO   fichaje:leer, fichaje:escribir, ausencia:leer, ausencia:escribir
GESTOR     + fichaje:leer:equipo, ausencia:aprobar, ausencia:leer:equipo,
             empleado:crear, empleado:leer
RRHH       + empleado:gestionar, fichaje:corregir, fichaje:auditoria,
             informe:exportar
ADMIN      + gestor:crear
```

## Consecuencias

**A favor**

- Añadir un rol es **tocar un solo fichero**; ningún controlador cambia.
- El nombre del permiso dice lo que protege: `hasAuthority('fichaje:corregir')`
  se entiende sin ir a mirar qué roles existen.
- Permitió cerrar un agujero real: `gestor:crear` la tiene **solo ADMIN**. Antes
  cualquier gestor podía crear otros gestores sin límite.
- Los tests declaran la authority que prueban, no el rol, así que no se rompen
  al cambiar la jerarquía.

**En contra**

- Una indirección más: para saber qué puede hacer un rol hay que mirar
  `RoleAuthorities` en vez de leerlo en el controlador. Se compensa con creces
  al pasar de dos roles.

## Lección aprendida

Al eliminar las comprobaciones duplicadas de los servicios se vio por qué
estorbaban: `if (rol != GESTOR)` habría **rechazado a RRHH y ADMIN**, aunque el
`@PreAuthorize` del controlador ya les hubiera dejado pasar.

El mismo error reapareció más tarde, por descuido, al decidir a quién notificar
una petición de ausencia: se filtró por `Role.GESTOR` y las empresas cuyo único
responsable era el ADMIN fundador **no recibían el aviso**. Solo se detectó
probando el envío de correos de verdad.

De ahí la regla: **quién puede hacer algo lo decide `RoleAuthorities`, nunca una
lista de roles escrita a mano** — tampoco fuera de los controladores.
