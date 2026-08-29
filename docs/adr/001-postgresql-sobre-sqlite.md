# 1. PostgreSQL en vez de SQLite

**Estado:** aceptada · **Fecha:** agosto 2026

## Contexto

El proyecto nació con SQLite: un fichero, cero instalación, cómodo para
desarrollar. Al revisarlo aparecieron problemas que no eran de configuración
sino consecuencia directa de esa elección:

- La base de datos **no tenía ni una clave foránea ni un índice** sobre las
  columnas por las que se filtra siempre (`usuario_id`, `empresa_id`). Borrar un
  usuario dejaba fichajes huérfanos, y cada listado era un recorrido completo de
  la tabla.
- El esquema lo generaba Hibernate con `ddl-auto: update`: sin control de
  versiones, sin poder revisar en un *pull request* qué cambiaba.
- SQLite admite **un solo escritor**. La generación de identificadores con
  `GenerationType.TABLE` abría su propia conexión y chocaba con el bloqueo de
  escritura de la transacción exterior: un **interbloqueo real**. El apaño fue
  quitar `@Transactional` de cinco métodos de negocio, así que `registerManager`
  podía dejar una empresa huérfana si fallaba a mitad.
- Se probó `GenerationType.IDENTITY`, pero `sqlite-jdbc` no implementa
  `getGeneratedKeys()`.

Es decir: para sostener SQLite había que renunciar a la atomicidad.

## Decisión

Migrar a **PostgreSQL**, con el esquema versionado en **Flyway** y escrito a
mano, no generado por Hibernate (`ddl-auto: validate`).

## Consecuencias

**A favor**

- Vuelve la atomicidad: desaparecen los cinco `Propagation.NOT_SUPPORTED`.
- Se pueden expresar en la base de datos garantías que antes solo vivían en el
  código Java y por tanto eran condiciones de carrera:
  - un **índice único parcial** que impide dos jornadas abiertas por empleado;
  - un `CHECK` que impide una ausencia con fecha de fin anterior a la de inicio;
  - claves foráneas con `ON DELETE RESTRICT`.
- Acceso a tipos que el proyecto acabó necesitando: `TIMESTAMPTZ` y `JSONB`.
- El esquema se revisa en los *pull requests* como cualquier otro código.

**En contra**

- Hace falta Docker para desarrollar y para pasar los tests. Se asume: el
  `docker-compose` lo levanta en un comando.
- El proyecto queda **atado a PostgreSQL**, y se ha aceptado explícitamente:
  hay índices parciales, `JSONB`, `GRANT`s y consultas nativas con
  `EXTRACT(EPOCH FROM ...)`. Fingir portabilidad habría sido teatro.

## Notas

Los datos que había en SQLite (94 filas de pruebas) se descartaron en vez de
migrarlos: no aportaban nada y arrastraban correos reales a un repositorio que
iba a ser público. En su lugar hay un *seed* de demo generado.
