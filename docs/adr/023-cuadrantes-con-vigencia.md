# 23. Cuadrantes: plantilla con vigencia, minutos desde medianoche, y la jornada contratada sigue siendo el contrato

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Hasta la Fase B1 el sistema sabía **cuánto** tenía que trabajar cada persona
—`usuarios.horas_semanales`, la jornada contratada— pero no **cuándo**. Sin eso
no se puede saber si alguien llegó tarde, ni enseñarle a qué hora le toca
entrar, ni medir sus horas extra contra su turno real en vez de contra un
reparto de lunes a viernes.

Había cuatro preguntas de modelado, y cada una tenía una respuesta tentadora
que era un error.

## Decisión

### Una plantilla semanal reutilizable, con excepciones por día

Una empresa tiene tres o cuatro horarios («oficina», «turno de mañana»,
«intensiva de verano»), no uno por persona. Así que el cuadrante es una
**plantilla** de tramos por día de la semana que se **asigna** a las personas, y
los días que se salen de ella van como **excepciones**: un día libre pactado, o
un horario distinto solo ese día.

Los festivos y las ausencias aprobadas **no** son excepciones. Ya los resuelve
`NonWorkingDayService`, y copiarlos al cuadrante sería tener dos verdades sobre
qué días no se trabaja. La precedencia vive en una sola pieza,
`JornadaTeoricaService`: no laborable, luego excepción, luego plantilla.

### Con vigencia, exactamente como los proyectos

Una asignación de plantilla lleva fecha de inicio y de fin, y un `EXCLUDE` sobre
`daterange` impide dos cuadrantes el mismo día, igual que en
[ADR 009](009-asignaciones-con-vigencia.md).

La consecuencia importante es la regla que atraviesa todo el servicio: **nada
puede cambiar el horario teórico de un día ya pasado.** Febrero ya está
informado y auditado. Por eso:

- una asignación no empieza antes de hoy, ni se cierra antes de ayer;
- los tramos de una plantilla que ya se aplicó a algún día pasado no se
  cambian — se crea otra y se asigna desde la fecha que sea;
- una excepción solo se pone o se quita de hoy en adelante.

Lo que salió distinto de lo previsto no se arregla reescribiendo el cuadrante.

### Minutos desde medianoche, no `TIME`

Un tramo es `inicio` y `fin` en minutos desde la medianoche del día en que
**empieza**. Un turno de 22:00 a 06:00 es `[1320, 1800)`: una sola fila.

Con `TIME` el fin sería menor que el inicio, y no habría forma de escribir ni el
`CHECK` ni el solape. Con minutos, el `EXCLUDE` de V31 compara tramos del mismo
día con `int4range`, y lo que ese `EXCLUDE` no puede ver —el turno de noche del
lunes que pisa un tramo del martes, o el del domingo que pisa el lunes
siguiente— lo comprueba `ReglasDeCuadrante` al guardar.

Los minutos son **nominales**: la noche del cambio de hora cuenta ocho horas,
como todas. Es lo que se quiere de un horario *teórico*.

### La jornada contratada no desaparece

El cuadrante **complementa** a `horas_semanales`, no la sustituye. La jornada es
el cuánto; el cuadrante, el cuándo. Un turno rotatorio puede cuadrar al mes y no
a la semana, y la jornada la fija quien lleva los contratos mientras el
cuadrante lo puede poner un gestor.

Por eso, si una plantilla se separa de la jornada contratada más de 30 minutos a
la semana, **se asigna igual**: la respuesta lo dice a quien asigna, y se avisa
a quien tiene `empleado:configurar`.

Y quien no tiene cuadrante no recibe un horario inventado: sus días son
`SIN_CUADRANTE`, sin repartir la jornada entre ellos.

## Consecuencias

**Cero regresión en horas extra, por construcción.** El barrido semanal mira
quién tiene cuadrante en la ventana (una consulta) y solo a esas personas les
mide la semana contra su horario teórico. Las demás siguen **exactamente por el
mismo código de siempre**, no por uno equivalente. Además, sin ningún día de
cuadrante, `minutosTeoricosSemana` da el mismo número que el cálculo de
siempre, y hay un test que los compara.

**Dos defectos que salieron al construirlo, no al diseñarlo:**

- Reemplazar los tramos de una plantilla borrando entidad a entidad falla contra
  el `EXCLUDE`: Hibernate vuelca los `INSERT` antes que los `DELETE`, y los
  tramos nuevos chocan con los viejos todavía sin borrar. Se borra con una
  sentencia `DELETE`, que se ejecuta en el acto. Comprobado cambiándola por
  `deleteAll()`: el test se pone rojo.
- Los `EXCLUDE` llegaban como **500**. El manejador de errores de la Fase A2 solo
  reconocía `UNIQUE` y clave ajena; un `EXCLUDE` tiene otro SQLSTATE (`23P01`).
  Y al arreglarlo, **ejecutando el backend** —no en la suite— apareció que
  Hibernate tampoco sabe sacar el nombre de la restricción de un `EXCLUDE`, así
  que los 409 salían con el mensaje genérico. Se lee ahora del campo
  estructurado del error de PostgreSQL. El test del manejador pasaba porque
  construía la excepción con el nombre ya puesto; ahora reproduce la real.

**La app solo lee.** Enseña las dos próximas semanas y la hora de entrada
prevista en *Mi jornada*. El editor de plantillas va a la web
([ADR 022](022-alcance-de-la-web.md)): montar una jornada partida en 400 dp de
ancho es trabajo tirado.

**Lo que habilita.** Las incidencias de cuadrante (retrasos, ausencias no
justificadas) y la analítica de puntualidad del bloque B se apoyan en
`JornadaTeoricaService` y no en otra cosa. El cuadrante del equipo hace hoy una
consulta por persona para que la precedencia viva en un solo sitio; el barrido
nocturno de incidencias, que recorrerá toda la plantilla, necesitará una
versión por lotes.
