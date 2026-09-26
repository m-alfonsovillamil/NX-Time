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
| [007](007-binarios-en-postgresql.md) | El CV y la foto en PostgreSQL, y en una tabla aparte |
| [008](008-festivos-calculados-y-compartidos.md) | Festivos nacionales calculados, compartidos y no editables |
| [009](009-asignaciones-con-vigencia.md) | Asignaciones a proyecto con vigencia, y el solape impedido por la base |
| [010](010-correcciones-con-aprobacion.md) | Ninguna corrección de fichaje se aplica sola |
| [011](011-horas-extra-detectadas-no-imputadas.md) | Las horas extra se detectan, no se imputan |
| [012](012-anonimato-estructural-en-el-canal-de-denuncias.md) | El anonimato del canal de denuncias es estructural, no una promesa |
| [013](013-la-candidatura-congela-el-cv.md) | La candidatura congela el CV que se presentó |
| [014](014-acceso-por-codigo.md) | Nadie teclea la contraseña de otro: acceso por código |
| [015](015-pausas-anadidas-a-posteriori.md) | Añadir una pausa después: un libro al lado del contador |
| [016](016-borrar-sin-borrar-el-registro-horario.md) | Borrar los datos de alguien sin borrar su registro horario |
| [017](017-imputacion-por-jornada.md) | Las horas de un proyecto se imputan por jornada, no se deducen por día |
| [018](018-la-cadena-de-auditoria-se-serializa-en-postgres.md) | La cadena de auditoría se serializa en PostgreSQL, no en la JVM |
| [019](019-el-refresh-token-se-hashea-y-se-rota.md) | El refresh token se hashea, se rota, y reutilizarlo revoca la familia |
| [020](020-tokens-en-el-navegador.md) | Los tokens de la web viven en memoria, y la cookie espera al dominio propio |
| [021](021-la-web-es-un-proyecto-aparte.md) | La web es un proyecto aparte, y lo que comparte con el resto se genera |
| [022](022-alcance-de-la-web.md) | La web empieza por los cimientos, y lo que queda fuera tiene nombre |
| [023](023-cuadrantes-con-vigencia.md) | Cuadrantes: plantilla con vigencia, minutos desde medianoche, y la jornada contratada sigue siendo el contrato |
| [024](024-incidencias-de-cuadrante.md) | Incidencias de cuadrante: se detectan, no se imputan, y se avisa una vez por noche |
| [025](025-la-firma-no-bloquea-la-correccion.md) | La firma mensual no bloquea la corrección: la corrección invalida la firma |
| [026](026-analitica-en-dias-y-por-alcance.md) | La analítica cuenta días, no horas, y el alcance lo decide quién mira |
| [027](027-listas-por-paginas.md) | Las listas que crecen sin límite van por páginas, con un DTO propio |
| [028](028-push-generico-colgado-del-aviso.md) | Push con FCM: cuelga del aviso, dice solo de qué va y se enciende en el móvil |
