# 24. Incidencias de cuadrante: se detectan, no se imputan, y se avisa una vez por noche

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Con los cuadrantes ([ADR 023](023-cuadrantes-con-vigencia.md)) el sistema sabe a
qué hora tocaba trabajar. La Fase B2 compara eso con lo fichado y anota lo que
no cuadra: el retraso, la salida anticipada y el día sin ningún fichaje.

Es el terreno donde una aplicación de fichaje se convierte en una herramienta
de vigilancia, y las decisiones de abajo están tomadas para que no lo sea.

## Decisión

### Se detecta, no se imputa

Exactamente como las horas extra ([ADR 011](011-horas-extra-detectadas-no-imputadas.md)):
una incidencia **no descuenta nada**, ni del saldo ni de la nómina. Nace
`PENDIENTE`, la persona la explica (`JUSTIFICADA`) y alguien con
`cuadrante:incidencias:revisar` la acepta o la rechaza. Rechazar pide un
comentario; aceptar, no. Nadie decide sobre las suyas, tenga el permiso que
tenga: el mismo conflicto de interés que ya se resolvió en correcciones y horas
extra.

### Las reglas, y por qué cada una

Viven en `ReglasDeCuadrante.incidencias`, puras y sin base de datos.

- **Diez minutos de margen**, a la entrada y a la salida. Llegar a las 9:08 no
  es una incidencia; a las 9:11, sí.
- **La primera entrada contra el primer tramo, la última salida contra el
  último.** En una jornada partida, volver tarde de comer no es un retraso: el
  cuadrante dice cuándo se empieza y se acaba, no cuándo se come.
- **Ausencia es un día con jornada teórica y ningún fichaje.** Como la jornada
  teórica ya viene de `JornadaTeoricaService`, que pregunta antes que nada a
  `NonWorkingDayService`, un festivo o unas vacaciones aprobadas nunca son una
  ausencia. Esa garantía está en un solo sitio.
- **No hay salida anticipada si la jornada la cerró el sistema.** El cierre
  automático de las 3:00 pone una hora de salida que nadie fichó; acusar a
  alguien de salir pronto con ella sería mentir. Tampoco la hay si queda una
  jornada abierta: todavía no se sabe cuándo se salió.
- **Un fichaje cuenta para el día en que empieza**, en hora de España. El turno
  de noche del lunes se compara con el cuadrante del lunes.
- **La hora prevista es de reloj de pared.** El 29 de marzo, el día del cambio
  de hora, «entrada a las 9:00» son las 9:00 en el reloj, no 540 minutos
  después de medianoche (que serían las 10:00). Un test con esa fecha lo fija, y
  se pone rojo con la cuenta ingenua.

### Un barrido nocturno con ventana, idempotente

`CUMPLIMIENTO_CUADRANTE` corre a las **3:40**, después del cierre de jornadas de
las 3:00, que es requisito: la regla de la salida necesita saber qué jornadas
cerró el sistema. Revisa **los últimos catorce días**, no solo ayer, por lo mismo
que las horas extra: una corrección aprobada el jueves cambia el lunes.

Pasar dos veces por el mismo día no duplica (`UNIQUE (usuario_id, fecha, tipo)`),
y eso es lo que permite que `tareas-nocturnas.yml` la vuelva a disparar si
Render estaba dormido. Una pendiente se pone al día si la corrección le cambió
la hora, y **se retira si ya no procede**. Una que alguien ya explicó o decidió
no se toca: sobre ella ya se ha hablado, y reescribirla cambiaría lo que se
leyó.

El barrido va **por lotes**: por cada día, un puñado de consultas para toda la
plantilla (`JornadaTeoricaService.diaDeVarios`), sean diez personas o mil.

### Un aviso por persona y noche, no uno por incidencia

A quien revisa le llega un resumen por empresa y noche, sin nombres, como en
horas extra. A quien tiene las incidencias le llega **uno**: con el detalle si es
una sola, y con el recuento si son varias («Tienes 12 días que no cuadraron con
tu cuadrante: 10 retrasos, 8 salidas anticipadas y 2 días sin fichar»). El
título cuenta **días**: llegar tarde y salir pronto es un día que no cuadró, no
dos.

## Consecuencias

**Lo que salió al ejecutarlo, no al diseñarlo.** La primera versión mandaba a la
persona un aviso y un correo **por incidencia**. Los tests pasaban: comprobaban
que cada incidencia se avisaba, que era lo que se había pedido. Al levantar el
backend contra los datos de demo y lanzar el barrido de verdad, Javier recibió
**veinte correos en una noche**. El resumen a quien revisa ya estaba pensado
para evitar eso; a quien tiene las incidencias, no. El segundo intento tituló
«Tienes 20 días…» cuando eran 20 incidencias en 12 días. Las dos cosas las
fija ahora `NotificationListenerTest`.

**Datos personales.** La explicación y el comentario de quien decide son texto
libre sobre una persona: la anonimización los borra
(`PersonalDataEraser`), y la exportación RGPD los incluye. Al hacerlo salió que
el motivo de las excepciones de cuadrante de la Fase B1 tampoco se borraba; se
arregló en el mismo cambio.

**La app enseña y deja explicar**; la bandeja del equipo, también, porque
decidir sobre una incidencia cabe en una tarjeta. La analítica de puntualidad
(Fase B4) se calculará sobre esta tabla, que ya está agregada por día.

**Lo que no hace.** No hay tolerancia por empresa ni por persona: diez minutos
para todos. Es lo primero que pedirá una empresa con turnos de fábrica, y va con
los ajustes por empresa que ya están aplazados (la zona horaria por empresa).
