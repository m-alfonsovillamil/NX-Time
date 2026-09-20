# 18. La cadena de auditoría se serializa en PostgreSQL, no en la JVM

**Estado:** aceptada · **Fecha:** septiembre 2026 · **Completa a:** [ADR 003](003-auditoria-append-only.md)

## Contexto

Cada fila de `auditoria_fichaje` guarda el hash de la anterior, así que alterar
una fila ya escrita rompe el hash de todo lo que viene después (ADR 003). Para
encadenar, `TimeEntryAuditListener` leía la última fila:

```java
String hashAnterior = auditRepository.findTopByOrderByIdDesc()...
```

Sin bloqueo. El comentario de la clase decía que eso era una limitación
conocida y aceptable, porque «con varias instancias de la aplicación
escribiendo a la vez podría haber una condición de carrera al encadenar — para
una única instancia, como esta, no es un problema real».

**Era falso, y no por poco.** El aislamiento por defecto de PostgreSQL es READ
COMMITTED: dos transacciones concurrentes leen cada una la última fila
*confirmada*, que es la misma para las dos. No hacen falta dos instancias —
bastan dos hilos de Tomcat atendiendo dos fichajes a la vez, que es lo normal
en cuanto hay dos personas en la empresa. Las dos anotan el mismo
`hashAnterior` y la cadena queda bifurcada.

El `UNIQUE (hash)` que ya existía desde V3 no lo impide: los contenidos de las
dos filas son distintos, luego sus hashes también.

Comprobado ejecutando, con ocho fichajes simultáneos en una sola instancia
(`CadenaDeAuditoriaConcurrenteIT`): la bifurcación se produce.

### Por qué esto importa más que un error normal

Una cadena bifurcada no se rompe hacia fuera: se rompe hacia dentro.
`VerificadorDeAuditoria.revisar()` informa de «el enlace con la fila anterior no
cuadra» — es decir, **el sistema acusa de manipulación a un registro con valor
legal que nadie ha tocado**. Es la peor forma de fallar que tiene esta tabla,
porque miente justo en la dirección que la normativa pide poder demostrar, y
porque el daño no aparece al escribir sino años después, cuando alguien
pregunta.

## Decisión

Serializar el encadenamiento con un **advisory lock de PostgreSQL**, pedido
dentro de la misma transacción, antes de leer la última fila:

```java
auditRepository.bloquearCadena(HuellaDeAuditoria.CLAVE_DEL_LOCK_DE_CADENA);
```

```java
@Query(value = "SELECT pg_advisory_xact_lock(:clave)", nativeQuery = true)
void bloquearCadena(@Param("clave") long clave);
```

Y, como respaldo en la base (V27), un índice único que impide que dos filas
digan ir detrás de la misma:

```sql
CREATE UNIQUE INDEX uq_auditoria_hash_anterior ON auditoria_fichaje (hash_anterior);
```

`hash_anterior` es NULL en la primera fila de la cadena, y en PostgreSQL los
NULL no colisionan entre sí en un índice único, así que esa fila no estorba.

### Por qué un advisory lock y no `SELECT ... FOR UPDATE`

`SELECT ... FOR UPDATE` es lo primero que se piensa, y aquí **no se puede
usar**: PostgreSQL exige privilegio `UPDATE` sobre la tabla para bloquear una
fila así, y V3 se lo revoca a propósito al rol de la aplicación para que la
tabla sea append-only (ADR 003). Un `FOR UPDATE` sobre `auditoria_fichaje`
fallaría con *permission denied*. Bloquear en su lugar una fila centinela de
otra tabla significaría inventar una tabla que solo existe para eso.

`pg_advisory_xact_lock` no tiene ese problema:

- lo puede pedir `PUBLIC`, sin privilegios sobre ninguna tabla;
- se libera **solo** al hacer commit o rollback, así que no hay forma de
  olvidarse de soltarlo ni de dejarlo colgado si algo lanza;
- vive en PostgreSQL y no en la JVM, así que protege también el día que haya
  más de una instancia — que es justo lo que el comentario viejo daba por
  imposible de proteger.

### Por qué el lock y además el índice

El lock es la solución; el índice es el testigo. Si algún día alguien escribe
en esta tabla por otra vía, o una refactorización se salta la llamada, la base
rechaza el INSERT en lugar de dejar la cadena bifurcada en silencio. Un error
al insertar se ve; una acusación falsa dentro de cuatro años, no.

El coste es el que se ve: un serializador global para escribir auditoría. Es
aceptable porque escribir una fila de auditoría es una sentencia dentro de una
transacción que ya estaba abierta, y porque la alternativa —una cadena por
empresa, que permitiría paralelismo— haría que la traza dejara de ser una sola
y complicaría la verificación sin resolver nada que hoy duela.

## Consecuencias

- La carrera desaparece, con una instancia y con varias.
- Escribir auditoría se serializa. Medido en el IT: ocho fichajes a la vez,
  sin contención apreciable.
- **Al aplicar V27 sobre una base con historia**, si la carrera ya ocurrió
  alguna vez, la migración falla a propósito y explica qué pasa y cómo
  localizar las filas. Son filas mal encadenadas, no manipuladas, y hay que
  decidir qué hacer con ellas —documentándolo— antes de volver a aplicarla.
  La consulta:

  ```sql
  SELECT hash_anterior, COUNT(*) FROM auditoria_fichaje
  WHERE hash_anterior IS NOT NULL GROUP BY 1 HAVING COUNT(*) > 1;
  ```

- El comentario de `TimeEntryAuditListener` que afirmaba lo contrario está
  reescrito. Un comentario que tranquiliza sobre algo falso hace más daño que
  no tener comentario.
