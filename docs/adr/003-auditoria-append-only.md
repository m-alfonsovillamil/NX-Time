# 3. Auditoría propia, y append-only por trigger (no solo por permisos)

**Estado:** aceptada · **Fecha:** agosto 2026

## Contexto

El RD-ley 8/2019 exige conservar los registros de jornada cuatro años y que sean
**fiables**. "Fiable" implica que una hora registrada no se pueda cambiar sin
dejar rastro, y que quede constancia de quién la cambió y por qué.

El proyecto no tenía nada de eso: un fichaje se modificaba y el valor anterior
desaparecía.

## Decisión

### Auditoría propia, no Hibernate Envers

Envers habría salido más barato en líneas de código, pero es una caja negra
sobre la que hay poco control, y aquí la auditoría es **el requisito de dominio
más característico del proyecto**. Se implementa a mano para poder explicar cada
pieza y adaptarla al caso concreto (motivo obligatorio, encadenamiento de
hashes, acciones automáticas del sistema).

### Corregir nunca sobrescribe

Corregir un fichaje **no** cambia sus horas: marca el original como anulado y
crea una fila nueva enlazada al anterior. El historial muestra la nueva; la
auditoría conserva ambas.

### Escritura antes del commit, y síncrona

La auditoría se escribe en la **misma transacción** que el fichaje
(`@TransactionalEventListener(BEFORE_COMMIT)`): si no se puede auditar, no se
ficha. Es lo contrario de las notificaciones por correo, que van *después* del
commit y en otro hilo — y la diferencia es intencionada: una traza de auditoría
es un requisito legal, un correo es una cortesía.

### Append-only **por trigger**, no solo por permisos

Aquí está la parte que se aprendió por las malas, y en dos tiempos:

1. La primera versión revocaba `UPDATE` y `DELETE` sobre `auditoria_fichaje` al
   rol de la aplicación. Al probarlo se descubrió que el rol de
   `docker-compose` era **superusuario**, y un superusuario se salta cualquier
   permiso: el `REVOKE` era decorativo. Se creó un segundo rol sin privilegios
   (`nxtime_app`) para la aplicación, dejando el rol dueño solo para las
   migraciones. Verificado: el `UPDATE` pasó a dar `permission denied`.

2. Al desplegar en **Neon**, el mismo problema reapareció por otra vía: Neon
   mete a *todos* los roles que creas en `neon_superuser`, y esa pertenencia
   **no se puede revocar**. Comprobado con una tabla de prueba: con solo
   `SELECT` e `INSERT` concedidos, el `UPDATE` respondía `UPDATE 1`.

   Es decir: la garantía más importante del módulo habría sido decorativa
   **justo en producción**.

   La solución es un **trigger**, que no depende de los privilegios del rol: se
   dispara para todo el mundo y solo el *dueño* de la tabla puede desactivarlo.
   Como las migraciones corren con el rol dueño y la aplicación con otro, la
   aplicación no puede quitárselo de encima. Hay un trigger aparte para
   `TRUNCATE`, porque un trigger de fila no se dispara con esa sentencia y
   quedaría un hueco para vaciar la tabla entera.

### Encadenamiento de hashes

Cada fila guarda el SHA-256 de la anterior además del suyo. Detecta
manipulaciones hechas **saltándose la aplicación** (con otras credenciales, o
restaurando un backup alterado): tocar una fila antigua invalida todas las
siguientes.

## Consecuencias

**A favor**

- La tabla es append-only de verdad, y está verificado con tests que ejecutan
  las sentencias prohibidas contra la base de datos real.
- Los permisos y el trigger son **dos barreras independientes**: en local salta
  primero el permiso, en Neon el trigger.
- Un fichaje corregido conserva su historia completa.

**En contra**

- Más código propio que mantener que con Envers.
- Escribir la auditoría en la misma transacción significa que un fallo al
  auditar tumba el fichaje. Es el comportamiento buscado, pero conviene tenerlo
  presente.
- **El encadenamiento de hashes no se puede verificar releyendo la base de
  datos.** El hash se calcula sobre el JSON que genera Jackson *antes* de
  guardarlo, y la columna es `jsonb`, que **normaliza** el texto al almacenarlo
  (reordena las claves, añade espacios). Un verificador ingenuo obtendría un
  hash distinto y daría **falsa alarma de manipulación**. Cuando se escriba ese
  verificador, tendrá que comparar el JSON parseado, no las cadenas.

## Lección general

Una garantía basada en permisos hay que **verificarla en cada plataforma**. Dos
veces se dio por buena una protección que no protegía nada, y las dos veces se
descubrió ejecutando la operación prohibida, no leyendo el código.
