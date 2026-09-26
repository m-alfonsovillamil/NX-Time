# nx-time-frontend-web

Cliente web de NX Time. **Está fuera del build de Gradle a propósito**: no
aparece en `settings.gradle.kts` ni en `settings-docker.gradle.kts`, tiene su
propio `npm` y tendrá su propio job de CI. Quien solo toque el backend no
necesita Node instalado.

Tiene **login y fichar** funcionando de punta a punta contra el mismo backend
que la app Android, dentro del **armazón** en el que irán el resto de pantallas
(fase W0 del plan de la web, [ADR 029](../docs/adr/029-la-web-alcanza-a-la-app.md)):
menú por authorities, campana de avisos, rutas protegidas y los componentes
comunes. Las demás pantallas llegan por fases (W2-W7) hasta hacer todo lo que
hace la app, más el editor de cuadrantes, la analítica y el visado de firmas.

```
src/
├── api/          schema.d.ts (generado) · cliente.ts · sesion.ts · useSesion.ts
│                 consultas.ts   TanStack Query: pedir(), useMutacion, listas por páginas
├── navegacion/   secciones.ts   EL catálogo: de aquí salen menú, rutas y destinos de avisos
│                 Marco.tsx · Campana.tsx
├── rutas/        rutas.tsx      guarda por authority, 403 y 404
├── paginas/      Login.tsx · Fichar.tsx     (cada una, su trozo de JS)
├── componentes/  Basicos · Estados · Tabla · Dialogo · Pestanas · Notificaciones · Icono
├── i18n/es/      un fichero por área; ningún texto literal en los componentes
├── estilos/      tokens.css (generado) · base.css
├── util/         fechas.ts · errores.ts · descargar.ts
└── pruebas/      api.tsx        servidor de mentira tipado con el contrato
e2e/              jornada.spec.ts (Playwright, contra el backend real)
scripts/          tokens.mjs · presupuesto.mjs
```

**Una sección nueva son dos pasos**: su línea en `navegacion/secciones.ts` y su
página. El menú, la ruta y el destino de aviso salen solos, y un test compara
el catálogo con `NoticeType.java` y `RoleAuthorities.java` del backend.

## Los dos ficheros generados

Los dos se versionan aunque estén generados, y esa es la parte importante:
`npm run verificar:generado` los regenera y compara con `git diff
--exit-code`, así que **desincronizarse rompe el build en vez de pasar
desapercibido**. Lo ejecuta `.github/workflows/web.yml` en cada PR que toque
la web, **`docs/openapi.json` o el tema de Android** — esas dos últimas son lo
importante: quien deja desfasado un fichero generado casi nunca es un cambio en
la web, sino en lo que la web genera. El 21/09/2026 dos PR con el CI en verde
por separado dejaron el contrato desfasado al juntarse en `main`, porque quien
movió `docs/openapi.json` era un PR de backend.

| Fichero | Se genera desde | Comando |
|---|---|---|
| `src/api/schema.d.ts` | `../docs/openapi.json` | `npm run api:types` |
| `src/estilos/tokens.css` | `ui/theme/Color.kt` y `Type.kt` de Android | `npm run tokens` |

`npm run generar` hace los dos.

**El contrato** lo produce `openapi-typescript`: solo tipos, cero runtime. Se
descartaron `orval` y `openapi-generator` porque escupen miles de líneas de
clases y un runtime propio, y para ~110 operaciones eso envejece peor que un
`.d.ts`. `docs/openapi.json` no se edita a mano: lo genera el backend
(`./gradlew :nx-time-backend:actualizarOpenApi`) y un test de snapshot lo
comprueba en cada build.

**Los tokens** salen del tema de la app Android porque el color de un botón no
puede tener dos verdades. Para cambiar un color, se cambia en `Color.kt` y se
ejecuta `npm run tokens`; editar `tokens.css` a mano dura hasta la siguiente
regeneración. El CSS lleva copiada en la cabecera la advertencia de contraste
de `Color.kt` — el par más justo de la paleta está a 4.67:1 — para que la lea
quien vaya a «ajustar un color».

## Comandos

```bash
npm install
npm run dev         # servidor de desarrollo en :5173
npm run generar     # tipos + tokens
npm test            # Vitest
npm run typecheck   # tsc del proyecto + el contrato generado
npm run build       # bundle de produccion en dist/
npm run presupuesto # tras el build: el JS inicial no pasa de 150 kB comprimido
npm run e2e         # Playwright, y necesita backend (ver abajo)
```

La versión de Node está en `.node-version`, y la leen los tres sitios que la
usan —tu máquina con `fnm`/`nvm`, el CI y Render—, así que no pueden divergir sin
que se vea en un diff.

**Despliegue**: un *static site* de Render declarado en `render.yaml`, con la
CSP, la reescritura a `index.html` y las cabeceras de seguridad. Los pasos, y
el que hay que hacer a mano en el backend (CORS), están en
[`docs/DESPLIEGUE.md`](../docs/DESPLIEGUE.md#la-web).

El servidor de desarrollo manda `/api` y `/auth` al backend de `localhost:8080`
con su proxy, así que **no hay CORS mientras se desarrolla**. El precio es que
el CORS de verdad solo se comprueba al desplegar; por eso el backend avisa al
arrancar en producción si `CORS_ALLOWED_ORIGINS` está vacío.

`typecheck` pasa `tsc` dos veces a propósito. La segunda,
`typecheck:contrato`, compila `src/api/schema.d.ts` con `--skipLibCheck false`:
el proyecto lleva `skipLibCheck: true` (lo normal, para no gastar el build en
los `.d.ts` de las dependencias), y con él puesto un contrato generado roto
pasaría sin que nadie lo viera, porque es justamente un `.d.ts`.

## Las decisiones que conviene conocer antes de tocar nada

**Los tokens viven en memoria.** No hay `localStorage` ni cookie, así que
recargar la página cierra la sesión. Es una decisión, no un olvido: el porqué
está en [ADR 020](../docs/adr/020-tokens-en-el-navegador.md), con el detalle
concreto de que `onrender.com` está en la Public Suffix List y por eso la
cookie `HttpOnly` tiene que esperar a un dominio propio. **El día que se ponga
la cookie hará falta también CSRF.**

**El refresco del 401 se serializa.** El servidor rota el refresh token: dos
refrescos en paralelo significan que el segundo llega con un token ya usado, y
eso el servidor lo lee como una copia robada y revoca la sesión entera. Por eso
`cliente.ts` comparte una única promesa. No es una optimización; sin ella, tres
peticiones simultáneas echan a la gente. `cliente.test.ts` lo comprueba.

**Los datos pasan por TanStack Query y `pedir()`.** Cada `queryFn` es
`pedir(cliente.GET(...))`, que devuelve los datos o lanza un `ErrorDeApi` con el
`detail` del servidor listo para enseñar. Volver a la pestaña vuelve a
preguntar, y una escritura (`useMutacion`) invalida por clave lo que ha
cambiado. Los errores de un formulario se enseñan al lado del botón, no en una
notificación que se va sola.

**La URL es el destino del aviso.** El aviso `ausencias` lleva a `/ausencias`:
sin tabla de traducción. Una sección cuya página aún no existe está en el
catálogo con su fase (`llegaEn`); no sale en el menú y su aviso no navega.

**El estado de la jornada se pregunta, no se deduce.** `GET /fichaje/activo` y
el campo `enPausa` vienen del servidor en cada fichaje. Llevar la cuenta en el
cliente se desincroniza en cuanto alguien fiche desde el móvil con esta pestaña
abierta, que es justo el caso que esta pantalla existe para demostrar.

## Los tests de extremo a extremo

Pocos, y contra el backend de verdad. Los tests de Vitest cubren lo que se
puede razonar (con `pruebas/api.tsx`, cuyas claves son rutas del contrato: una
ruta mal escrita no compila); estos cubren lo único que ninguno de ellos puede
demostrar: que la web y el backend **hablan el mismo idioma**. En este proyecto
los defectos que se han escapado salieron todos ejecutando el sistema.

Están fuera de `npm test` a propósito, porque necesitan el sistema levantado
(pasarán a CI en la fase W8):

```bash
docker compose up -d postgres
./gradlew :nx-time-backend:bootRun --args="--spring.profiles.active=dev,demo"
npm run e2e
```

Entran como EMPLEADO (el rol con menos permisos, para que se note si una
pantalla necesitara alguno que no tiene): una jornada entera, el marco con su
menú y su campana, un enlace sin sesión que vuelve a su página tras el login, y
que recargar cierra la sesión (ADR 020, hasta la fase W1).

## Por qué los tests están sobre `scripts/tokens.mjs`

El generador lee Kotlin con expresiones regulares, y una expresión regular
falla **en silencio**: no avisa de que ha dejado de encontrar un color,
devuelve uno menos. `scripts/tokens.test.mjs` cuenta las declaraciones del
`Color.kt` real y las compara con lo leído, comprueba que los dos temas
definan los mismos tokens, y fija la forma de la salida contra fixtures.

Ese test ya ha pagado: destapó que `aHexCss` validaba la longitud del literal
pero no el alfa, así que un `0x800E7C86` habría salido como `#0e7c86`,
**pintado opaco**, sin error.
