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

## Actualización, septiembre 2026: las authorities viajan al cliente

La regla de arriba —nunca una lista de roles escrita a mano— se cumplía en el
backend y **se rompía en la app**. `ui/util/Permisos.kt` era un espejo de
`RoleAuthorities.java`: traducía el rol a capacidades con su propia copia de la
jerarquía. Funcionaba mientras alguien se acordara de tocar los dos sitios, y
el defecto que motivó ese fichero —ofrecerle "Crear gestor" a un GESTOR, que
recibía un 403— era exactamente lo que vuelve a pasar cuando la copia se queda
atrás. Con la web en camino habría un tercer espejo, en TypeScript.

Así que el servidor manda la lista resuelta: `authorities` va en la respuesta de
`/auth/login`, en la de `/auth/refresh` y en `GET /api/v1/perfil`, calculada con
`RoleAuthorities.enOrden(rol)` — la misma fuente que alimenta los
`@PreAuthorize`, así que no puede decir otra cosa.

Tres detalles que no son obvios:

- **Viaja también en el login, no solo en el perfil.** El cliente arma su menú
  antes de tener perfil; si hubiera que pedirlo aparte habría un hueco, el
  primero tras entrar, en el que la aplicación no sabría qué ofrecer.
- **Sin lista no se enseña nada.** Una sesión abierta con una versión anterior
  de la app no la tiene guardada. La degradación correcta es apagar el menú
  hasta que el perfil la rellene, nunca abrirlo por si acaso.
- **Esto no autoriza.** Autoriza el `@PreAuthorize` contra el token. Lo que
  viaja solo decide qué se enseña, y por eso da igual que el perfil de un
  compañero también lo lleve: son función de su `rol`, que ya se enviaba.

Lo que se gana no es una pantalla: es que añadir una authority nueva deje de
obligar a tocar cada cliente. `RoleAuthoritiesTest` comprueba además que toda
authority exigida por un `@PreAuthorize` la tenga algún rol — una errata ahí
crea un endpoint que responde 403 a todo el mundo y un botón que nunca aparece.
