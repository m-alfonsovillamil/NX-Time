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
| [020](020-tokens-en-el-navegador.md) | Los tokens de la web viven en memoria, y la cookie espera al dominio propio (sustituido por el 030) |
| [021](021-la-web-es-un-proyecto-aparte.md) | La web es un proyecto aparte, y lo que comparte con el resto se genera |
| [022](022-alcance-de-la-web.md) | La web empieza por los cimientos, y lo que queda fuera tiene nombre (alcance sustituido por el 029) |
| [023](023-cuadrantes-con-vigencia.md) | Cuadrantes: plantilla con vigencia, minutos desde medianoche, y la jornada contratada sigue siendo el contrato |
| [024](024-incidencias-de-cuadrante.md) | Incidencias de cuadrante: se detectan, no se imputan, y se avisa una vez por noche |
| [025](025-la-firma-no-bloquea-la-correccion.md) | La firma mensual no bloquea la corrección: la corrección invalida la firma |
| [026](026-analitica-en-dias-y-por-alcance.md) | La analítica cuenta días, no horas, y el alcance lo decide quién mira |
| [027](027-listas-por-paginas.md) | Las listas que crecen sin límite van por páginas, con un DTO propio |
| [028](028-push-generico-colgado-del-aviso.md) | Push con FCM: cuelga del aviso, dice solo de qué va y se enciende en el móvil |
| [029](029-la-web-alcanza-a-la-app.md) | La web alcanza a la app: un armazón común y la URL como destino del aviso |
| [030](030-la-sesion-web-en-cookie.md) | La sesión de la web va en una cookie, con dominio propio y CSRF de doble envío |
| [031](031-push-en-la-web.md) | Push en la web: service worker propio, SDK solo para el token y web instalable |
| [032](032-zona-horaria-por-empresa.md) | La zona horaria es de cada empresa, y los días se cuentan al leer |
| [033](033-fichaje-en-kiosco.md) | Fichaje en kiosco: un dispositivo que solo ficha, tarjeta QR firmada y PIN por persona |
| [034](034-endurecimiento-de-octubre.md) | Endurecimiento de octubre: confirmar el correo al registrarse y topes donde no los había |
| [035](035-sistema-visual-de-la-web.md) | El sistema visual de la web: el ancho, el acento de cada zona y piezas compartidas |
| [036](036-entrar-con-google-o-microsoft.md) | Entrar con Google o con Microsoft: solo para quien ya tiene cuenta, y a quién se le cree el correo |
| [037](037-ya-tienes-cuenta-por-correo.md) | Registrarse con un correo que ya tiene cuenta: se le dice a su dueño, por correo |
