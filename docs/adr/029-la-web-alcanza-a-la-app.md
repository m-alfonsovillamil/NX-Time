# 29. La web alcanza a la app: un armazón común y la URL como destino del aviso

**Estado:** aceptada · **Fecha:** septiembre 2026 · **Sustituye** a la parte de
alcance del [ADR 022](022-alcance-de-la-web.md)

## Contexto

El [ADR 022](022-alcance-de-la-web.md) dejó la web en los cimientos: login y
fichar, y una lista de lo que quedaba fuera. Los cimientos se probaron en
producción (contrato generado, tokens, refresco serializado, arranque en frío,
CORS, CSP), y la app Android tiene ya unas treinta y tres pantallas.

Dos cosas cambian la prioridad de la web:

1. **Es el único cliente para quien tenga iPhone.** La app de iOS sigue
   aplazada, así que la web tiene que funcionar en el móvil, no solo en
   escritorio.
2. **La gestión se hace mejor sentado.** RRHH y quien lleva un equipo trabajan
   con tablas y teclado; ahí la web tiene que hacerlo mejor que el móvil.

Así que el alcance pasa a ser **todo lo que hace la app**, más las tres
pantallas que el ADR 022 reservó a la web (editor de cuadrantes, analítica
completa y visado de firmas). El plan va por fases (W0-W9); este ADR recoge las
decisiones que valen para todas.

## Decisiones

### 1. TanStack Query para los datos

Con dos pantallas, `useState` + `useEffect` a mano era razonable. Con treinta
serían treinta copias de cargar, fallar, reintentar, invalidar tras escribir y
paginar. TanStack Query (~13 kB) da eso una vez y dos cosas que aquí importan:
**volver a la pestaña vuelve a preguntar** (quien ficha en el móvil y vuelve a
la web ve la jornada real) y **una escritura invalida por clave** lo que ha
cambiado.

Se descarta Redux: el estado de esta aplicación es del servidor, no de la
interfaz. El refresco del 401 y el arranque en frío siguen en `cliente.ts`,
debajo; TanStack Query no los sustituye.

`api/consultas.ts` añade el pegamento: `pedir()` convierte la respuesta de
`openapi-fetch` en datos o en un `ErrorDeApi` con el `detail` del servidor ya
listo para enseñar; `useMutacion` invalida y confirma; `useListaPaginada` y
`todasLasPaginas` aplican el ADR 027. Para leer se usa `useQuery` tal cual:
envolverlo no aportaba nada.

Se reintenta **una vez** lo que pudo ser pasajero (sin respuesta, 5xx) y
**nunca** un 4xx. Al cerrar sesión se vacía la caché, para que quien entre
después en el mismo navegador no vea un instante los datos del anterior.

### 2. Sin librería de componentes

Componentes propios sobre `tokens.css`, que se genera de `Color.kt`: una
librería traería su propio aspecto, y el color de un botón no puede tener dos
verdades. HTML nativo donde basta (`<dialog>`, `<select>`, `<input type="date">`,
`<details>`, `<table>`): ya sabe comportarse con el teclado, con un lector de
pantalla y en el móvil.

### 3. Gráficos en SVG propio

Horas por día, absentismo por mes y puntualidad son barras y líneas sencillas.
Una librería de gráficos pesaría más que toda la web y complicaría la CSP.

### 4. La URL de la web es el destino del aviso

El backend manda con cada aviso un destino lógico (`NoticeType.java`:
`ausencias`, `correcciones/pendientes`, `firmas`…). En la web **ese destino es
la ruta**: el aviso lleva a `/ausencias`, sin la tabla de traducción que
Android necesita (`DestinoDeAviso.kt`). Un correo puede enlazar a la web con el
mismo texto, y un enlace sin sesión pasa por el login y vuelve a su página.

El catálogo de secciones (`navegacion/secciones.ts`) es la única lista de la
que salen **el menú, las rutas y los destinos**. Un test lee `NoticeType.java`
y falla si algún destino no tiene sección; otro lee `RoleAuthorities.java` y
falla si una sección pide una authority que no existe. Por eso `web.yml`
vigila esos dos ficheros del backend.

Las secciones cuya página no ha llegado están ya en el catálogo con su fase
(`llegaEn`): no salen en el menú ni tienen ruta, y un aviso que apunte a ellas
se lee pero no navega, que es la degradación que Android aplica a un destino
que su versión no conoce.

### 5. Menú y rutas por authority, no por rol

El menú de cada cuenta es el catálogo filtrado por las authorities que manda el
servidor (ADR 005). Sin la authority de una página, la web responde **403 con
explicación**, no el login ni un 404: la página existe, lo que falta es el
permiso.

### 6. Cada página, su trozo de JS, y un presupuesto que lo vigila

Las páginas se cargan con `React.lazy`: quien solo ficha no descarga el editor
de cuadrantes. `npm run presupuesto` falla en CI si el JS inicial pasa de
**150 kB comprimido** (en W0: 99,5 kB). Basta un `import` normal en el sitio
equivocado para que una página rara se meta en el trozo inicial, y sin esto
nada fallaría: la web iría más lenta en el móvil sin que nadie lo notara.

### 7. Descargas con autenticación

Excel, PDF y CSV no pueden ser un `<a href>`: el token va en la cabecera.
`descargar()` pide con `cliente`, recibe un `Blob` y toma el nombre de
`Content-Disposition` (con `filename*`, que es lo único que lleva acentos).

### 8. Responsive de verdad

Barra lateral por encima de 960 px; por debajo, cabecera y barra inferior con
cuatro secciones y «Más», el mismo límite de cinco de Material 3 que tiene la
app. Las tablas se pintan como tarjetas por debajo de 640 px con el mismo
marcado: cada celda lleva el nombre de su columna en `data-etiqueta`.

### 9. Pruebas

Vitest + Testing Library por página, con `pruebas/api.tsx`: un servidor de
mentira cuyas claves son las rutas del contrato (`'GET /api/v1/fichaje/activo'`),
así que una ruta mal escrita no compila. Playwright para los recorridos contra
el backend de verdad; en W8 pasa a CI con Postgres y el jar del backend.

## Lo que la web no hace, a propósito

- **El recordatorio de fichar.** En un navegador no hay tareas programadas sin
  push. Con Web Push (W9) podría volver como push enviado por el servidor; se
  valorará entonces.
- **Entrar con huella.** El equivalente web son las *passkeys* (WebAuthn), que
  piden su propio registro de credenciales en el backend. Sería otra fase.
- **Recordar la sesión al recargar**, hasta que haya dominio propio
  ([ADR 020](020-tokens-en-el-navegador.md)). Es la fase W1.

## Consecuencias

- **Una sección nueva son dos pasos**: su línea en el catálogo y su página. El
  menú, la ruta y el destino de aviso salen solos.
- **Un aviso nuevo en el backend obliga a la web**: su destino tiene que tener
  sección o el CI de la web se pone rojo, aunque el PR sea de backend.
- **La web depende de TanStack Query.** Es una dependencia de las que no se
  cambian fácilmente; se asume porque sustituye mucho más código propio del que
  añade.
- **El ADR 022 queda como historia** de por qué la web empezó por los
  cimientos. Lo que decía sobre las pantallas «solo de la web» sigue valiendo;
  lo que decía sobre el alcance, no.
