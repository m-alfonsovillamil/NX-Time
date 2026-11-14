# 57. Paleta arena

**Estado:** aceptada · **Fecha:** noviembre 2026 · Cambia los colores de la línea visual «Fichaje» ([ADR 035](035-sistema-visual-de-la-web.md)); las formas, la tipografía y las reglas de uso siguen igual

## Contexto

La aplicación iba en verde azulado (`#0E7C86`) sobre un fondo blanco azulado, con
índigo para la zona de gestión: correcta, pero igual que casi cualquier
aplicación de empresa. La propuesta fue jugar con el **arena**, que es el reloj de
arena, el tiempo, en una paleta de tres colores que siguiera siendo profesional.

Se compararon tres variantes sobre las pantallas reales (arena con petróleo, con
azul noche y con grafito), en claro y en oscuro, y tres formas de pintar las
tarjetas (blancas, marfil y arena). Las tarjetas blancas sobre arena se
descartaron: el blanco puro queda duro, como recuadros vacíos.

## Decisión

**Arena, azul noche y petróleo, con tarjetas marfil.**

| Papel | Claro | Oscuro |
|---|---|---|
| Fondo (degradado) | arena, `#F6EFE1` → `#E6D9C1` | negro cálido, `#100E0B` → `#2A2419` |
| Tarjetas (`surface`) | marfil, `#FFFAF1` | `#201D18` |
| Acciones (`primary`) | azul noche, `#1F3A5F` | `#A9C7F2` |
| Gestión (`tertiary`) | petróleo, `#0A6570` | `#5FD4DE` |
| Botones secundarios (`secondaryContainer`) | arena tostada, `#D3C096` | `#4A412C` |
| Texto | tinta, `#1C2327` | `#ECE6DA` |

- **El arena no vale para un botón principal.** Es claro y poco saturado: un
  botón arena con texto blanco no se lee y parece desactivado. Los botones
  secundarios sí van en arena, pero **tostada** (`#D3C096`) y con texto oscuro:
  una arena más clara no se distinguía del fondo cuando el botón va fuera de
  una tarjeta («Solicitar ausencia», «Entrar con Microsoft»).
- **El petróleo pasa a la zona de gestión** y el índigo desaparece: con arena y
  azul, un tercer tono frío más eran demasiados.
- **Los colores de estado no cambian** (`ColoresJornada`: verde, ámbar, rojo), ni
  los de error. El color del botón de fichar sigue siendo el estado de la jornada.
- **La fuente sigue siendo `Color.kt`.** La web genera `tokens.css` con
  `npm run tokens`, y el backend copia lo que pinta él (correos, PDF y Excel) en
  `Marca`, que `MarcaTest` compara con `Color.kt`.
- **El icono** cambia de fondo, del verde azulado al azul noche, en la app y en
  la web instalable. El dibujo es el mismo.

## Los documentos que se descargan

Los PDF y el Excel son lo que acaba en una gestoría o en una inspección, así que
se tratan como documentos de la casa y no como volcados de datos:

- **PDF (registro mensual y «Tus datos»):** un membrete común (`Membrete`) en
  cada página, con una franja del color de la marca, el logotipo dibujado en
  vectores (el mismo trazo que el icono), qué documento es y de qué empresa; y un
  pie con lo mismo y «Página X de Y». La cabecera de la tabla se repite en cada
  hoja y las firmas no se parten. El registro abre con un recuadro arena con la
  empresa, la persona y el mes en letra. Las tablas llevan solo rayas
  horizontales y filas alternas en arena. Un mes normal, con sus firmas, cabe en
  una hoja.
- **«Tus datos»** dice los estados como se dicen («En investigación», no
  `EN_INVESTIGACION`); el JSON de la misma exportación los conserva tal cual.
- **Excel:** el título en una banda del color de la marca, el periodo y la fecha
  de generación sobre arena, filas alternas, las incidencias en rojo, sin la
  cuadrícula de Excel, la cabecera y el nombre fijos al desplazarse y con filtro,
  y preparado para imprimir (apaisado, a lo ancho de una hoja, cabecera repetida,
  encabezado con la empresa y «Página X de Y»). **Las posiciones no cambian:** el
  título en la fila 1, las cabeceras en la 4 y los datos desde la 5, con las
  mismas columnas, para quien lo lea por posición.
- **CSV:** no admiten formato. Siguen con punto y coma y BOM para que Excel los
  abra con las tildes bien.

## Contraste

Todos los pares texto/fondo llegan a 4,5:1, comprobados uno a uno. El más justo
es el petróleo sobre el final del degradado claro (4,84:1). El azul noche sobre el
fondo da 8,2:1 en el peor punto, así que la regla de «no poner el color de la
marca sobre el fondo» ya no es por contraste; se mantiene por estilo.

## Consecuencias

- En oscuro el arena casi no se ve: queda un negro cálido. El carácter de la
  paleta lo da el tema claro.
- La insignia ámbar («Pendiente») y los botones secundarios en arena tostada
  quedan de un tono parecido. Se distinguen por la forma y el texto; si en uso
  se confunden, se oscurece la insignia.
- Quien tenga la app instalada verá cambiar el icono al actualizar.
- Las capturas de `docs/capturas/` y del README son de la paleta anterior: se
  rehacen una sola vez, con el cambio de nombre.
- Los comentarios de código que justifican una regla con cifras de contraste
  del verde azulado se han dejado como estaban: explican por qué existe la regla.

## Alternativas descartadas

- **Arena con petróleo** (el verde azulado de antes, más oscuro): la más
  continuista, pero cambiaba poco la imagen.
- **Arena con grafito:** muy sobria; la aplicación se quedaba casi sin color propio.
- **Tarjetas blancas:** chocan con el fondo. **Tarjetas arena** sobre un fondo
  más claro: con más carácter, pero la insignia ámbar y los botones secundarios
  se perdían sobre ellas.
- **Esperar al cambio de nombre:** el color sale de un solo sitio, así que no
  cuesta hacerlo dos veces; lo caro (icono de tienda, capturas) sí espera.
