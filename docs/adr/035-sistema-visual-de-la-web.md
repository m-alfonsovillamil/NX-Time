# 35. El sistema visual de la web: el ancho, el acento de cada zona y piezas compartidas

**Estado:** aceptada · **Fecha:** octubre 2026 · Plan de estética y
optimización del 5/10/2026

## Contexto

Con el plan de la web (W0-W9) la web tenía ya todas las pantallas de la app,
pero vista en un escritorio parecía a medio hacer:

- **Columnas estrechas en pantallas anchas.** `.nx-pagina` limitaba el
  contenido a 560 px, y once páginas lo usaban así (Integridad, Informes,
  Ajustes, Denuncias…). En una pantalla de 1700 px quedaban dos tarjetas en el
  centro y dos tercios de fondo vacío.
- **Un menú lateral plano.** Había 14 iconos para unas 30 secciones:
  «documento» se repetía en nueve y «calendario» en seis, y los subapartados no
  llevaban ninguno. El icono dejaba de servir para encontrar nada.
- **La web se iba separando de la app sin que nadie lo decidiera:**
  - Los radios estaban escritos a mano (12, 24, 8…) y ninguno era de la escala
    de `Shape.kt`.
  - El índigo, que la app reserva a la gestión, casi no aparecía.
  - Había tres copias de «cifra con nombre» con dos estilos distintos. Una de
    ellas era invisible dentro de una tarjeta blanca, porque su fondo,
    `surface-container`, también es blanco en el tema claro.

## Decisión

### Las formas también salen de Android

`scripts/tokens.mjs` lee ahora `Shape.kt` además de `Color.kt` y `Type.kt`, y
genera:

- `--nx-radio-xs/s/m/l/xl`, con los valores 8 / 16 / 20 / 28 / 36.
- `--nx-radio-tarjeta`.
- `--nx-sombra-tarjeta`, sacada de la elevación de 3 dp con la equivalencia de
  siempre, que da `0 1px 3px`.

En `base.css` ya no queda ningún radio que no sea de la escala, salvo las
pastillas (999px) y las barras de los gráficos. Si se cambia una esquina en la
app, la web la sigue igual que sigue un color.

### El acento de cada zona

Las variables `--nx-acento`, `--nx-acento-contenedor` y sus `on-*` valen
`primary` en «Lo mío» y `tertiary` (índigo) en «Gestión». El marco pone
`data-zona` en el `<main>` según el grupo de la sección de la URL, y en cada
grupo del menú.

Las piezas que llevan el color de la zona usan el acento y no `primary`. Son
el icono de la cabecera, el elemento activo del menú, las cifras destacadas,
el estado vacío y las iniciales.

Hay una regla de contraste que hay que respetar al añadir piezas: **sobre un
contenedor, el texto es siempre su `on-*-contenedor`**. El teal sobre su
contenedor da 3,9:1 y el índigo sobre el suyo 4,1:1, los dos por debajo de AA.
Por eso la insignia informativa no usa el tinte de su color, como las demás,
sino el contenedor del acento.

### Cada sección del menú tiene su icono

`Icono.tsx` pasa a unos 50 trazados de Material. Los nombres dicen lo que
representan, y al lado se anota el nombre que tienen en Material. Dos tests
de `secciones.test.ts` impiden que vuelvan a repetirse:

- Dos entradas del menú no pueden compartir icono.
- Un apartado no puede usar el icono de una de sus secciones.

### Piezas compartidas en lugar de copias

- **`CabeceraDePagina`**: el icono de la sección en el recuadro del acento,
  la miga de pan (grupo › apartado), el título, una descripción y los botones.
  El icono y la miga salen del catálogo a partir de la URL.
- **`Cifras` / `Cifra`**: sustituyen a las tres copias.
  - La cifra va delante en el HTML, así que se oye «2 Ausencias por aprobar».
    El CSS la pinta debajo de su nombre.
  - La rejilla **nunca deja tres y una sola debajo**. El número de columnas
    sale de cuántas cifras hay y del ancho de la tarjeta, con una consulta de
    contenedor, no del ancho de la pantalla.
- **`Vacio`** lleva un icono. Sustituye a los catorce párrafos sueltos que
  hacían de estado vacío.
- **`Insignia`** va rellena con un tinte del 14 % de su color sobre la
  superficie, en lugar de solo con borde.
- **`Iniciales`**.
- **`Esqueleto`** con forma (`lineas`, `tabla`, `recuadros`) y al ancho de su
  contenedor, más `EsqueletoDePagina` para la carga de una página. Antes era
  una columna de 560 px en todas partes, y al llegar los datos la página
  saltaba.

### El menú se reconoce de un vistazo, y dice lo que espera

- **Apartados y entradas.** Cada apartado lleva su icono en un recuadro del
  acento de su zona. Cada entrada lleva el suyo, más pequeño, y cuelga de una
  línea guía.
- **Dónde estás.** La entrada de la página abierta va rellena, con una barra
  sobre la guía. Su apartado lleva el recuadro lleno, así que se sabe dónde se
  está aunque el apartado esté plegado.
- **Contadores de pendientes** (`navegacion/pendientes.ts`).
  - Usan la misma consulta y la misma clave de caché que el panel de gestión.
    Resolver algo en una bandeja actualiza los dos a la vez.
  - Se piden cada cinco minutos y solo con la pestaña a la vista, como la
    campana (ADR 034). Sin `fichaje:leer:equipo` no se piden.
  - Un apartado plegado enseña la suma de lo que tiene dentro.
  - **El número no entra en el nombre del enlace.** La pastilla va con
    `aria-hidden`. El texto «3 pendientes» es la descripción del enlace
    (`aria-describedby`) y va *fuera* de él, porque dentro pasaría a formar
    parte del nombre. Un lector de pantalla dice «Ausencias del equipo,
    enlace, 3 pendientes», y el enlace se sigue buscando por el nombre de su
    sección.
- **Chip de jornada** (`ChipDeJornada`), en la barra superior.
  - Usa los colores de `ColoresJornada`: verde trabajando, ámbar en pausa.
    Sin fichar va en neutro, porque no es una alarma.
  - Comparte la consulta de «Mi jornada», así que no añade peticiones.
  - Avanza cada medio minuto, no cada segundo.
  - No sale en «Mi jornada» ni mientras no se sabe el estado.
- **Precarga.** Pasar el ratón por una entrada, o llegar a ella con el
  tabulador, pide ya el JS de su página (`perezosa` en `secciones.ts`). Para
  cuando se pulsa, ya está descargado.

### El ancho lo decide la composición, no la página

`.nx-pagina` ocupa hasta 1280 px. Lo que necesita poco ancho lo resuelve la
composición de dentro, con tres variantes de `.nx-composicion`:

- `--principal-lateral`
- `--lista-detalle`
- `--mitades`

`.nx-pagina--lectura` (760 px) queda solo para texto largo.

Las once páginas que eran una columna estrecha se rehicieron una a una:

| Página | Composición |
|---|---|
| Integridad | Estado y «comprobar ahora» a la izquierda; «cómo funciona» en puntos, al lado |
| Informes | El mes en la cabecera; cada descarga, una tarjeta con su icono |
| Borrados | Tabla a lo ancho, con el estado en una insignia |
| Calendario laboral | El mes en rejilla, con los festivos pintados, y la lista al lado |
| Denuncias recibidas | La bandeja y el expediente lado a lado |
| Ajustes de la empresa | Los datos y los kioscos en dos mitades |
| Firma mensual | Una tarjeta por mes |
| Mi cuadrante | La semana en siete columnas |
| Canal de denuncias | El formulario a la izquierda; qué es, seguir una y las mías, al lado |
| Avisos | La lista con el icono de cada sección; al lado, cuántos quedan sin leer |
| Ajustes | Un índice que se queda a la vista y las secciones al lado |

Detalles de comportamiento que cambiaron con el rediseño:

- **Calendario laboral.** Pulsar un día libre abre «nuevo festivo» con esa
  fecha ya puesta. Un festivo nacional no es un botón, porque no se puede
  cambiar.
- **Denuncias recibidas.** El detalle va al lado solo donde cabe.
  - Es la única decisión que no resuelve el CSS: lo toma `useEsAncho`
    (`util/ancho.ts`) con `matchMedia`.
  - El motivo es que se trata de *qué* se pinta: pintar el expediente dos
    veces para esconder uno duplicaría sus formularios y sus `id`.
  - En una pantalla estrecha sigue saliendo en su diálogo.

Con esto nacieron tres piezas más en `Basicos.tsx`:

- `Tarjeta` con `icono`, `descripcion` e `id`.
- `Destacado`, para un estado que tiene que verse antes que nada: bien, mal o
  neutro.
- `Puntos`, una explicación en puntos con icono.

## Consecuencias

- El JS inicial pasa de 106 a 113 kB comprimidos, casi todo por los trazados
  de los iconos. El límite sigue en 150 kB.
- Un color, un radio o una sombra nuevos se añaden en el tema de la app, no en
  `base.css`.
- Las páginas estrechas conservan su columna con `.nx-pagina--estrecha`
  mientras se rediseñan una a una (fase E2). Esa clase desaparece al terminar.
