# Registro de decisiones de arquitectura (ADR)

Un ADR documenta una decisión de diseño **no obvia**: qué problema había, qué se
decidió y qué se gana y se pierde con ello. Sirve para que, meses después, nadie
—incluido quien lo escribió— tenga que reconstruir el razonamiento leyendo el
código, ni deshaga una decisión sin saber por qué se tomó.

Aquí solo hay decisiones que **realmente se tomaron** en este proyecto, con las
consecuencias que de verdad tuvieron, incluidas las incómodas.

| # | Decisión |
|---|---|
| [001](001-postgresql-sobre-sqlite.md) | PostgreSQL en vez de SQLite |
| [002](002-instant-vs-localdatetime.md) | `Instant`/`TIMESTAMPTZ` para fichajes, `LocalDate` para ausencias |
| [003](003-auditoria-append-only.md) | Auditoría propia, y append-only por trigger (no solo por permisos) |
| [004](004-java-sobre-kotlin.md) | Migrar el backend de Kotlin a Java 21 |
| [005](005-authorities-granulares.md) | Autorización por authorities granulares, no por roles |
| [006](006-multitenant-por-discriminador.md) | Multi-tenant por discriminador |
