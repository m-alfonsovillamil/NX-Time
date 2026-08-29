# 2. `Instant` y `TIMESTAMPTZ` para los fichajes, `LocalDate` para las ausencias

**Estado:** aceptada · **Fecha:** agosto 2026

## Contexto

Las horas de entrada y salida se guardaban como `LocalDateTime`, que es una
fecha-hora **sin zona**: "2026-10-25 02:30" a secas.

En España hay dos días al año en que eso es ambiguo o imposible. La madrugada
del último domingo de octubre, los relojes se atrasan y **las 02:30 ocurren dos
veces**: un `LocalDateTime` no distingue cuál de las dos. La del último domingo
de marzo, los relojes se adelantan y **las 02:30 no existen**.

Un registro de jornada con valor legal —que puede acabar ante una inspección o en
un juzgado— no puede tener horas ambiguas. Y el turno de noche que cruza
justamente esa madrugada es un caso real, no rebuscado.

Como efecto secundario, el JSON de la API tampoco decía en qué zona estaban las
horas: el cliente tenía que suponerlo.

## Decisión

- **Fichajes** (`hora_entrada`, `hora_salida`, `inicio_pausa_actual`) → `Instant`
  en Java y `TIMESTAMPTZ` en PostgreSQL. Un fichaje es **un instante concreto en
  la línea del tiempo**, no una lectura de reloj de pared.
- **Fechas de ausencia** (`fecha_inicio`, `fecha_fin`) y **festivos** →
  `LocalDate`. Un día de vacaciones es una **fecha de calendario**: "el 24 de
  diciembre" significa lo mismo en cualquier zona, y no tiene hora.

La conversión a hora española se hace **solo en los bordes**: al presentar (la
app), al derivar el día de calendario de un fichaje, al calcular "hoy / esta
semana / este mes" y al generar informes. Nunca en el almacenamiento.

## Consecuencias

**A favor**

- Deja de haber horas ambiguas: un `Instant` es un punto único en el tiempo,
  pase lo que pase con los relojes.
- El JSON viaja en ISO-8601 con sufijo `Z` (UTC explícito), sin ambigüedad.
- Cambiar la zona horaria del servidor no altera ningún dato almacenado.

**En contra**

- Fue un **cambio de contrato** que rompió la app Android: el formato pasó de
  `2026-08-24T22:25:08.236` a `2026-08-24T20:25:08.236Z` y hubo que cambiar el
  parseo. Se hizo en un único PR coordinado.
- Hay que acordarse de proyectar a `Europe/Madrid` en cada punto de
  presentación. Se centralizó en constantes en vez de repetir la zona suelta.

## Nota

La mezcla de los dos tipos es deliberada, no un descuido: son dos conceptos
temporales distintos y merecen tipos distintos. Usar `Instant` para unas
vacaciones obligaría a inventarse una hora ("¿el 24 a las 00:00 de qué zona?")
que no significa nada.
