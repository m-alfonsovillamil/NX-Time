# 58. Cancelar una ausencia

**Estado:** aceptada · **Fecha:** noviembre 2026 · Junto a los ADR [005](005-authorities-granulares.md) (authorities) y [028](028-push-generico-colgado-del-aviso.md) (avisos)

## Contexto

Una petición de ausencia solo podía acabar aprobada o rechazada, y una aprobada no
la podía deshacer nadie. Si alguien cambiaba de planes, o volvía al trabajo antes de
tiempo, los días seguían gastados en su saldo de vacaciones y la ausencia seguía en
el calendario del equipo. Se vio en la revisión global del 15/11/2026, al buscar lo
que le falta a la aplicación para sustituir a Logroot en una empresa de ~120
personas, donde pasará cada semana.

## Decisión

**Un cuarto estado, `CANCELADA`, con su propia ruta:** `POST /api/v1/ausencias/{id}/cancelar`.

- **Quien la pidió la retira** mientras no haya empezado, esté pendiente o aprobada.
  El motivo es opcional: es la suya.
- **Una aprobada que ya ha empezado, o pasado, la anula quien aprueba ausencias**
  (`ausencia:aprobar`), y el motivo es obligatorio: la persona tiene que saber por
  qué le cambian unos días que daba por libres. Lo mismo vale para una aprobada que
  no ha empezado: quien aprueba también puede anularla.
- **Una pendiente ajena no se cancela:** se aprueba o se rechaza. Rechazar ya pide
  motivo y avisa, que es lo que corresponde.
- **No se puede deshacer.** Quien cambie de idea la vuelve a pedir; los días
  quedan libres para eso.

**Una cancelada cuenta como una rechazada:** no ocupa el calendario, no gasta
vacaciones, no cuenta en la analítica y no impide pedir esos días otra vez. Las
consultas que miraban «distinta de RECHAZADA» pasan a mirar «PENDIENTE o APROBADA»
(`AbsenceStatus.estaViva()`), que es lo que querían decir.

**Pero no es una rechazada**, por eso es un estado y no una marca:

- Nadie la ha denegado: enseñarla como «Rechazada» sería falso, y la analítica y
  el historial dirían que alguien dijo que no.
- Guarda quién la canceló, cuándo y por qué (`cancelada_por_id`, `cancelada_en`,
  `motivo_cancelacion`), aparte de quién la aprobó, que sigue constando.
- La base lo garantiza: `ck_peticiones_resolucion_coherente` se reescribe para que
  solo una `CANCELADA` lleve datos de cancelación, y la lleve siempre.

**Si se puede cancelar lo decide el servidor** y viaja en cada respuesta
(`puedoCancelar`), como `puedoResolver` en las correcciones: la web y la app
enseñan el botón cuando toca y no repiten la regla.

**A quién se avisa** (aviso nuevo `AUSENCIA_CANCELADA`, con su push):

- Si la anula otra persona, a su dueño, con aviso y correo. El correo no lleva el
  motivo, como el de la resolución: se lee en la aplicación.
- Si el dueño retira una que ya estaba aprobada, a quien aprueba ausencias, solo con
  el aviso: contaban con ella.
- Si retira una pendiente, a nadie: desaparece de la bandeja y ya está.

## Consecuencias

- **Las versiones de la app anteriores a la 1.24 no conocen el estado.** Gson deja
  a `null` un valor de enum que no conoce, y la tarjeta de ausencia fallaría al
  pintarlo. Hoy no hay usuarios reales fuera de la demo y la 1.24 sale con este
  cambio, así que se acepta; antes del piloto en IIDD, todo el mundo tiene que
  estar en la 1.24 o posterior.
- Cancelar una aprobada del pasado deja esos días sin ausencia: la detección de
  incidencias de cuadrante puede marcarlos como días sin fichar en su ventana. Es
  lo correcto: si no estuvo de vacaciones, tuvo que trabajar.
- Las horas extra ya detectadas no se recalculan: el objetivo semanal descontaba la
  ausencia. El proceso nocturno mira catorce días atrás; lo anterior se corrige a
  mano si hace falta.

## Alternativas descartadas

- **Marcar la petición como RECHAZADA con una bandera `cancelada`.** Habría
  evitado el problema de las versiones viejas de la app, a cambio de que el estado
  mintiera en todas partes.
- **Que el empleado pueda anular también una aprobada ya empezada.** Volver antes de
  unas vacaciones es algo que la empresa tiene que saber y aceptar; si lo hiciera
  solo, el registro diría que no hubo ausencia sin que nadie lo hubiera visto.
- **Un PATCH de estado, como aprobar.** Cancelar lo puede pedir el propio
  interesado, tiene otras reglas y guarda otros datos; mezclarlo en el mismo
  endpoint obligaba a que `ausencia:aprobar` dejara de bastar para entender quién
  puede llamarlo.
