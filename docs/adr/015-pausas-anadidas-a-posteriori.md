# 15. Añadir una pausa después: un libro al lado del contador

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

"Se me olvidó darle a pausar para comer." Hasta aquí, una pausa solo existía si
se pulsaba el botón en el momento. Quien lo olvidaba tenía una jornada con una
hora de más, y la única salida era pedir una corrección de **horas** —que no
tenía campo para pausas— o dejarlo así, con esa hora contando como trabajo y,
llegado el caso, como hora extra.

Dos hechos condicionaban cualquier solución:

1. **No existía la noción de "una pausa".** Lo que se guarda es un contador,
   `registros.segundos_pausa_acumulados`, y el instante de la pausa en curso. De
   las pausas ya fichadas no hay inicio, ni fin, ni autor.
2. **El registro horario es de conservación obligatoria 4 años** (RD-ley 8/2019),
   y que cada uno se edite sus horas sin control es justo lo que una inspección
   mira mal.

## Decisión

### El contador se queda como está, y al lado va un libro

`segundos_pausa_acumulados` **no cambia de significado**: sigue siendo el total
que leen los seis agregados nativos, el mapper y la app. Junto a él, una tabla
`pausas_anadidas` registra **solo** las pausas que alguien metió a mano, con su
intervalo, su motivo, quién, cuándo, y la solicitud por la que entró (si entró por
aprobación).

Se descartaron las dos alternativas obvias:

- **Solo sumar al contador.** Deja un número que salta de 0 a 3600 sin intervalo y
  sin autor consultable. Y sin intervalo no hay forma de detectar un solape entre
  dos pausas añadidas.
- **Una tabla de intervalos para todas las pausas.** Obligaría a inventarse los
  intervalos de las pausas históricas, que no están en ninguna parte. En un
  registro de conservación obligatoria, rellenarlos es falsificar. Y arrastraría
  reescribir los seis agregados.

### Hoy, directo; un día pasado, con aprobación

Decide el servidor, en un solo sitio (`AddedPauseServiceImpl.esViaDirecta`):

- **Jornada abierta** → directa. Aunque empezara ayer: un turno de noche abierto
  es "mi jornada de ahora", y "solo hoy" lo mandaría a aprobación.
- **Jornada cerrada que empezó hoy** en Madrid → directa. Por la hora de *entrada*,
  porque es como los agregados asignan cada jornada a un día.
- **Cualquier otra** → se pide como corrección.

Lo que hace defendible la vía directa: **añadir una pausa solo puede bajar el
tiempo trabajado, nunca subirlo**; lleva motivo obligatorio; queda en la traza con
la acción `PAUSA_ANADIDA`; y nunca aplica sobre el fichaje de otra persona.

### La vía de aprobación reutiliza las correcciones, no las copia

`solicitudes_correccion` gana dos columnas nullable, `pausa_inicio_propuesta` y
`pausa_fin_propuesta`. Una solicitud puede proponer horas, pausa o las dos.

Una tabla paralela de "solicitudes de pausa" habría duplicado la parte más
delicada del proyecto —aprobar, rechazar, disputar, avisar, auto-aprobar a quien
puede— y, sobre todo, **habría dejado convivir una petición de pausa y una de horas
sobre el mismo fichaje**, que es justo lo que `uq_correcciones_una_viva_por_registro`
existe para impedir (ADR 010).

### Sin tope de duración, con motivo siempre

Se pidió así. Lo que sí se exige no es política sino aritmética
(`ReglasDePausa`, compartida por las dos vías): la pausa cabe en la jornada, no
está en el futuro, no se solapa con otra añadida ni con la pausa en curso, y **la
pausa total no puede igualar o superar la jornada**.

Esa última no es redundante. Las pausas fichadas con el botón no tienen intervalo,
así que su solape con una añadida es invisible: el total es la única red contra un
tiempo neto negativo, y los agregados restan en SQL sin proteger el resultado.

### Deshacer solo sobre la jornada abierta

`DELETE .../pausas/{id}` marca la pausa como anulada —no la borra; la tabla no
tiene `GRANT DELETE`— y solo sobre la jornada abierta. Una jornada abierta no ha
llegado a ningún informe ni al cálculo de horas extra; una cerrada sí, y quitarle
una pausa **sube** el tiempo trabajado. Eso ya no es autoservicio.

## Consecuencias

- **Las pausas añadidas se mudan al corregir.** Una corrección no edita el fichaje:
  lo anula y crea otro. Los segundos viajan en `copiaCorregidaDe`, y las filas del
  libro se re-apuntan a la versión nueva; si no, dejarían de verse.
- **Las horas extra no se recalculan hacia atrás.** Una pausa aprobada sobre un día
  pasado puede dejar mal un aviso ya emitido, incluso uno ya ACEPTADO que cuenta en
  la bolsa anual. El detector nocturno solo reprocesa avisos ABIERTO de los últimos
  14 días. Se acepta en esta versión; es el primer candidato si se revisa.
- **El doble conteo no se puede detectar.** Si alguien pulsó pausa de verdad y
  además la añade a mano, las dos cuentan. La pausa fichada no tiene intervalo con
  el que comparar. Se mitiga enseñando en la pantalla las pausas que ya hay.
- **Queda abierta una decisión**: un límite de antigüedad para pedir pausas sobre
  días pasados. Es de otro eje que el tope de duración: protege un informe ya
  entregado a una inspección de cambiar meses después. Hoy no existe.
- `V19` va detrás de `V17` y `V18`. Flyway no aplica una migración más antigua que
  la última aplicada, así que llegar a producción en otro orden rompe el arranque.
