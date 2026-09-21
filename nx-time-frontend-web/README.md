# nx-time-frontend-web

Cliente web de NX Time. **Está fuera del build de Gradle a propósito**: no
aparece en `settings.gradle.kts` ni en `settings-docker.gradle.kts`, tiene su
propio `npm` y tendrá su propio job de CI. Quien solo toque el backend no
necesita Node instalado.

Tiene **login y fichar** funcionando de punta a punta contra el mismo backend
que la app Android: lo que se ficha aquí sale en el historial del móvil. Es
deliberadamente pequeño — el resto de pantallas (analítica, editor de
cuadrantes, visado de firmas, informes) llegará después.

```
src/
├── api/      schema.d.ts (generado) · cliente.ts · sesion.ts
├── rutas/    rutas.tsx
├── paginas/  Login.tsx · Fichar.tsx
├── componentes/  Basicos.tsx · ServidorDespertando.tsx
├── i18n/     es.ts          ningún texto literal en los componentes
├── estilos/  tokens.css (generado) · base.css
└── util/     fechas.ts · errores.ts
e2e/          jornada.spec.ts (Playwright, contra el backend real)
```

**Sin Redux, Zustand ni TanStack Query.** Para dos pantallas sobran `useState`
y un módulo con estado; se añadirá cuando haya una pantalla que lo pida.

## Los dos ficheros generados

Los dos se versionan aunque estén generados, y esa es la parte importante:
`npm run verificar:generado` los regenera y compara con `git diff
--exit-code`, así que **desincronizarse rompe el build en vez de pasar
desapercibido**. El job de CI que ejecuta ese comando —y que se dispara
también al tocar `ui/theme/` de Android— llega con el despliegue de la web.

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
npm run e2e         # Playwright, y necesita backend (ver abajo)
```

El servidor de desarrollo manda `/api` y `/auth` al backend de `localhost:8080`
con su proxy, así que **no hay CORS mientras se desarrolla**. El precio es que
el CORS de verdad solo se comprueba al desplegar; por eso el backend avisa al
arrancar en producción si `CORS_ALLOWED_ORIGINS` está vacío.

`typecheck` pasa `tsc` dos veces a propósito. La segunda,
`typecheck:contrato`, compila `src/api/schema.d.ts` con `--skipLibCheck false`:
el proyecto lleva `skipLibCheck: true` (lo normal, para no gastar el build en
los `.d.ts` de las dependencias), y con él puesto un contrato generado roto
pasaría sin que nadie lo viera, porque es justamente un `.d.ts`.

## Las tres decisiones que conviene conocer antes de tocar nada

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

**El estado de la jornada se pregunta, no se deduce.** `GET /fichaje/activo` y
el campo `enPausa` vienen del servidor en cada fichaje. Llevar la cuenta en el
cliente se desincroniza en cuanto alguien fiche desde el móvil con esta pestaña
abierta, que es justo el caso que esta pantalla existe para demostrar.

## El test de extremo a extremo

Uno solo, y contra el backend de verdad. Los tests de Vitest cubren lo que se
puede razonar; este cubre lo único que ninguno de ellos puede demostrar: que la
web y el backend **hablan el mismo idioma**. En este proyecto los defectos que
se han escapado salieron todos ejecutando el sistema.

Está fuera de `npm test` a propósito, porque necesita el sistema levantado:

```bash
docker compose up -d postgres
./gradlew :nx-time-backend:bootRun --args="--spring.profiles.active=dev,demo"
npm run e2e
```

Entra como EMPLEADO (el rol con menos permisos, para que se note si esta
pantalla necesitara alguno que no tiene), ficha, pausa, reanuda y sale.

## Por qué los tests están sobre `scripts/tokens.mjs`

El generador lee Kotlin con expresiones regulares, y una expresión regular
falla **en silencio**: no avisa de que ha dejado de encontrar un color,
devuelve uno menos. `scripts/tokens.test.mjs` cuenta las declaraciones del
`Color.kt` real y las compara con lo leído, comprueba que los dos temas
definan los mismos tokens, y fija la forma de la salida contra fixtures.

Ese test ya ha pagado: destapó que `aHexCss` validaba la longitud del literal
pero no el alfa, así que un `0x800E7C86` habría salido como `#0e7c86`,
**pintado opaco**, sin error.
