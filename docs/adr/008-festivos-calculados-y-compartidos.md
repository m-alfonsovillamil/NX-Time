# 8. Los festivos nacionales se calculan, se comparten y no se editan

**Estado:** aceptada · **Fecha:** septiembre 2026

> Se numera 8 porque el 7 (adjuntos en `bytea`) entra con la fase B2, que va
> delante de esta.

## Contexto

El calendario laboral decide qué días son hábiles, y de ahí cuelgan el saldo de
vacaciones, los días que consume cada ausencia y —a partir de la fase F— el
cómputo de horas extra. Hasta ahora la tabla `festivos` solo la rellenaba
`DemoDataSeeder` con siete fechas escritas a mano, así que en producción estaba
**vacía**: todos los días entre semana contaban como hábiles.

Hay que decidir tres cosas que van juntas:

1. **De dónde salen los festivos nacionales.** En España son diez: nueve fechas
   fijas del calendario laboral estatal (art. 37.2 ET) y el Viernes Santo, que se
   deduce de la Pascua.
2. **Cuándo existen.** Un año que nadie ha mirado nunca no tiene festivos.
3. **Quién puede tocarlos.**

## Decisión

**Se calculan, no se teclean.** `NationalHolidayGenerator` produce las nueve
fechas fijas más el Viernes Santo, que sale del algoritmo gregoriano anónimo
(Meeus/Jones/Butcher) para el Domingo de Resurrección. Una tabla escrita a mano
habría que rellenarla cada diciembre, y el año que nadie se acordara el
calendario aparecería vacío sin dar ningún aviso —el peor fallo posible aquí,
porque un calendario vacío no parece roto: parece un año sin fiestas.

**Se siembran al leer, no al arrancar.** `NationalHolidaySeeder.asegurarAnio` los
guarda la primera vez que alguien mira ese año. Sembrar en el arranque obligaría
a decidir cuántos años por delante, y la respuesta correcta cambia con la fecha:
un servidor levantado en noviembre y no reiniciado hasta marzo dejaría enero,
febrero y el Viernes Santo del año siguiente sin festivos.

**Un festivo nacional es UNA fila para todas las empresas** (`empresa_id IS
NULL`, como ya estaba) **y no se edita ni se borra por API.** Es la parte menos
obvia y la más importante: si un gestor pudiera borrar "Natividad del Señor"
desde su empresa, se la quitaría del calendario a todas las demás. El límite ahí
no lo pone el rol —un GESTOR tiene `calendario:gestionar`— sino el ámbito del
propio festivo, y por eso cada uno viaja al cliente con un campo `editable`.

Lo que sí se gestiona desde la aplicación son los festivos **de la empresa**, en
tres ámbitos nuevos: `AUTONOMICO`, `LOCAL` y `EMPRESA`. La columna `ambito` no
sustituye a `empresa_id`: la detalla, y un `CHECK` impide que se contradigan
(`ck_festivos_ambito_coherente`).

## Lo que este cálculo NO puede saber

Y por eso la pantalla lo dice con estas palabras, en vez de prometer "todos los
festivos":

- Cuando un festivo estatal cae en domingo, **el traslado al lunes lo decide el
  Gobierno año por año en el BOE**. El generador deja la fecha donde cae: mover
  el día por nuestra cuenta sería inventarse una norma que puede no existir ese
  año.
- Las comunidades autónomas sustituyen algunas de esas fiestas por otras propias,
  y cada municipio añade dos locales.

El generador da la base; el gestor la completa. Decirlo importa porque alguien
va a plantear sus vacaciones contando con este calendario.

## Consecuencias

**A favor**

- El calendario de cualquier año existe sin que nadie lo mantenga, incluido uno
  que se consulte tres años por delante.
- Diez festivos nacionales en una sola fila cada uno, no diez por empresa.
- La distinción de ámbitos explica en pantalla de dónde sale cada día, que es lo
  único que hace falta para revisarlos cuando cambia el año.

**En contra**

- **Una empresa no puede anular un festivo nacional que en su comunidad se
  sustituye por otro.** Es la consecuencia directa de compartir la fila, y hoy se
  acepta: el efecto práctico es un día hábil de menos, no un cálculo incorrecto
  a favor de la empresa. Si algún día hace falta, la salida es una tabla de
  exclusiones por empresa, no dar permiso de borrado sobre la fila compartida.
- Sembrar dentro de un `GET` obliga a una transacción aparte (`REQUIRES_NEW`,
  porque la lectura es `readOnly`) y a tragarse la violación de unicidad que
  produce que dos peticiones estrenen el mismo año a la vez.
- El rango de años consultables está limitado (2000–2100) precisamente porque
  mirar un año lo crea: sin límite, un bucle de peticiones dejaría millones de
  filas.
