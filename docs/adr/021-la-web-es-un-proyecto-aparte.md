# 21. La web es un proyecto aparte, y lo que comparte con el resto se genera

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

En septiembre de 2026 el objetivo pasó a ser que NX Time corra en Android, en
navegador y, más adelante, en iOS. Hasta entonces había un backend en Spring
Boot y una app Android nativa en Kotlin, en un monorepo de Gradle con dos
módulos.

Había que decidir tres cosas a la vez, y cada una condiciona a las otras: con
qué se escribe la web, qué comparte con los demás clientes, y cómo se evita que
lo compartido se desincronice.

## Con qué se escribe

Se pesaron tres opciones:

| Opción | Lo que da | Lo que cuesta |
|---|---|---|
| **React + TypeScript + Vite** | Lo estándar; una SPA que consume la API REST con tipos generados del OpenAPI. iOS, después, en SwiftUI nativo | Las reglas que viven en el cliente Android se reescriben en TypeScript |
| Kotlin Multiplatform (módulo `shared`) | DTOs, cliente HTTP y reglas compartidas entre Android e iOS | La web **sigue** siendo React; obliga a pasar la capa de datos de Android de Retrofit a Ktor |
| Compose Multiplatform | Android, iOS y web (Wasm) desde el mismo código de UI | El bundle web pesa megas, no hay SEO, la accesibilidad en Wasm es floja, y obliga a rehacer la navegación y el tema actuales |

**Se eligió React + TypeScript + Vite**, con iOS en SwiftUI nativo cuando toque.

Kotlin Multiplatform no se descartó por malo sino porque **no resolvía la web**:
compartía lógica entre Android e iOS y dejaba la web exactamente donde estaba, a
cambio de refactorizar una capa de Android que funciona. Compose Multiplatform sí
la resolvía, pero con una web que es un lienzo pintado: sin SEO, con
accesibilidad por debajo de un HTML normal y con megas de descarga para una
pantalla de fichar.

## Qué se comparte, y cómo

La respuesta de este proyecto no es una biblioteca compartida sino otra: **lo
que dos clientes tendrían que calcular igual, lo calcula el servidor o se
genera de una sola fuente.**

- **El contrato** (`docs/openapi.json`) lo produce el backend, y un test de
  snapshot (`OpenApiSnapshotTest`) falla si deja de coincidir con la API real.
  Se descartó el `springdoc-openapi-gradle-plugin`: arranca una aplicación
  aparte, que aquí necesitaría base de datos y perfil propio, mientras que la
  suite de tests ya levanta el contexto contra Postgres real.
- **Los tipos de la web** (`src/api/schema.d.ts`) salen de ese contrato con
  `openapi-typescript`: solo tipos, cero código en tiempo de ejecución. Se
  descartaron `orval` y `openapi-generator`, que escupen miles de líneas de
  clases y un runtime propio; para ~110 operaciones eso envejece peor que un
  `.d.ts`. Efecto inmediato: el campo `contrasena` —que con `password` daba un
  400 y ya costó tiempo una vez— es ahora un error de compilación.
- **Los colores y la tipografía** (`src/estilos/tokens.css`) salen del tema de
  la app Android (`Color.kt`, `Type.kt`) con un script sin dependencias. El
  color de un botón no puede tener dos verdades.
- **Los permisos** no se copian: el servidor manda la lista resuelta en
  `authorities` (ver la actualización de septiembre del
  [ADR 005](005-authorities-granulares.md)). Es el mismo criterio que ya seguía
  `ProfileResponse` al mandar `nombreCompleto` e `iniciales` hechos, «para que
  no los arme cada cliente a su manera».

**Los ficheros generados se versionan**, y `npm run verificar:generado` los
regenera y falla si hay diferencias. Así, cambiar el contrato o el tema de
Android **rompe el build de la web** en vez de desincronizarla en silencio.

## Dónde vive

**Fuera del build de Gradle.** `nx-time-frontend-web/` no aparece en
`settings.gradle.kts` ni en `settings-docker.gradle.kts`, tiene su propio `npm`
y su propio workflow (`.github/workflows/web.yml`). Quien solo toque el backend
no necesita Node, y la web no espera siete minutos de tests de Postgres para
saber si compila: su CI tarda menos de un minuto.

**Desplegada como static site de Render**, no como un segundo servicio web. Un
static site no es una instancia y no gasta las 750 horas al mes del plan
gratuito; otro servicio web sí, y dejaría al backend sin margen.

## Consecuencias

**A favor**

- Tres clientes pueden coexistir sin que ninguno sea la copia de otro en lo que
  importa: contrato, permisos y aspecto tienen una sola fuente.
- La desincronización es un fallo de build, no un defecto en producción.

**En contra, y alguna ya pagada**

- **Hay reglas que sí se copian.** El formateo de fechas con la zona de España
  (`fechas.ts` ↔ `DateFormats.kt`) y la lectura de errores `ProblemDetail`
  (`errores.ts` ↔ `ApiErrorParser.kt`) existen dos veces, y con iOS existirán
  tres. Es el coste que anunciaba la opción elegida. La salida, cuando duela, es
  la misma que con los permisos: que el servidor mande el dato resuelto.
- **Lo generado depende de cosas que no son la web.** El 21/09/2026 dos PR con
  el CI en verde por separado dejaron el contrato desfasado al juntarse en
  `main`, porque quien movió `docs/openapi.json` era un PR de backend. Por eso
  el workflow de la web se dispara también con ese fichero y con el tema de
  Android, no solo con su carpeta.
- **El generador de tokens lee Kotlin con expresiones regulares**, y una regex
  falla en silencio: devuelve un color menos. Lo cubren tests que cuentan las
  declaraciones del `Color.kt` real; el primero que se escribió encontró un
  defecto — un color semitransparente habría salido opaco.
- **npm, no pnpm**, aunque el plan decía pnpm: no estaba instalado, y activarlo
  por corepack es un paso más para cualquiera que clone el repositorio.
