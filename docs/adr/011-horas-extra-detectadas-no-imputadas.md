# 11. Las horas extra se detectan, no se imputan

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

El proyecto llevaba nueve fases midiendo jornadas sin decir nunca nada sobre su
duración. Un fichaje de once horas se guardaba, se auditaba y salía en el
informe mensual exactamente igual que uno de ocho.

Eso deja fuera dos límites que el Estatuto de los Trabajadores da por
supuestos: la jornada ordinaria no pasa de **9 h efectivas** (art. 34.3) y las
horas extra no pasan de **80 al año** (art. 35.2). Una aplicación de registro
horario que no sabe decir si se han superado no sirve para lo que existe el
registro horario.

## Decisión

Un proceso nocturno recorre los fichajes recientes y anota los excesos. Lo que
anota es un **aviso**, no una imputación de horas extra.

Esa distinción es toda la fase. El reloj sabe que el martes duró once horas; no
sabe si fue una jornada intensiva pactada, un turno partido mal fichado o una
guardia. Un aviso nace `ABIERTO` y **solo cuenta para la bolsa anual cuando una
persona lo `ACEPTA`**. Justificarlo (`JUSTIFICADO`) lo archiva sin consumir
bolsa, y entonces la explicación es obligatoria: aceptar unas horas que el reloj
ya ha medido no necesita motivo, pero decidir que once horas trabajadas *no*
cuentan es la decisión que un inspector querría ver motivada.

### El umbral semanal se prorratea por los días hábiles reales

El diario es fijo porque es legal: 9 h efectivas para todo el mundo, también
para quien está a media jornada. Es un límite de salud laboral, no de contrato.

El semanal es la jornada contratada, pero **prorrateada por los días hábiles que
esa semana tuvo de verdad** — descontando los festivos del calendario (ADR 008)
y las ausencias aprobadas. Dividir entre cinco a ciegas convertiría cada puente
en una falsa alarma en las dos direcciones: contra un objetivo fijo de 40 h,
quien trabaja sus ocho horas de lunes a jueves parecería estar por debajo y
nunca saltaría nada; contra un número más bajo calculado a ojo, saltaría
cualquier día que se alargara un poco.

Hay además una **tolerancia de 30 minutos**. Pasarse cinco minutos no es una
hora extra: es la diferencia entre fichar al llegar a la mesa y fichar al entrar
por la puerta. Sin ella el proceso generaría un aviso casi diario para casi todo
el mundo, y una bandeja llena de avisos que nadie mira es peor que no tener
avisos. La tolerancia decide **si** se avisa, no cuánto: 31 minutos de más son
un exceso de 31 minutos, no de uno.

### La bolsa anual no se guarda: se calcula al leer

Lo natural sería un contador por usuario y año. Sería un error, y por una razón
concreta que esta fase hereda de la anterior: **desde la Fase E las correcciones
de fichaje son rutina**. Un contador denormalizado se desincroniza en cuanto una
corrección aprobada cambia una jornada del mes pasado, y a partir de ahí miente
en silencio.

La bolsa se deriva sumando los avisos `ACEPTADO` del año, igual que el saldo de
vacaciones se deriva de las ausencias aprobadas. Una sola fuente de verdad.

### Nadie revisa lo suyo propio

`horasextra:revisar` empieza en GESTOR — decidir si las once horas del martes
fueron horas extra o una intensiva pactada es justo el conocimiento que tiene
quien lleva el equipo. Pero **ni siquiera esa authority permite revisar los
avisos de uno mismo**: eso lo corta el servicio, no el `@PreAuthorize`, porque
quien revisa tiene la authority precisamente. Aceptar las horas extra que
hiciste ayer es cobrarlas, y justificarlas es hacerlas desaparecer. Es el mismo
conflicto de interés que ya obligó a separar quién aprueba en el ADR 010.

Ver los avisos propios, en cambio, no pide nada: es mirar tu propia jornada.

## El proceso nocturno no deshace decisiones humanas

El barrido corre a las **3:30**, media hora después del que cierra las jornadas
olvidadas: una jornada abierta no tiene hora de salida, así que no puede
producir un aviso, y si los dos se cruzaran media hora de fichajes olvidados se
quedaría sin mirar hasta la noche siguiente.

Mira **catorce días atrás**, no solo el día anterior, porque una corrección
aprobada el jueves puede cambiar el lunes pasado. Eso obliga a que repasar sea
seguro, y de ahí las tres reglas del detector:

- Un aviso `ABIERTO` se **actualiza** si el cálculo cambia, y se **retira** si el
  exceso desaparece.
- Un aviso `JUSTIFICADO` o `ACEPTADO` **no se toca nunca**, aunque los fichajes
  de debajo hayan cambiado. Si alguien miró ese martes y decidió, esa decisión
  manda.
- La retirada necesita un paso propio: un día que se queda sin fichajes válidos
  desaparece del agregado, así que el bucle normal no vuelve a pasar por él y el
  aviso se quedaría acusando de unas horas que ya no constan en ningún sitio.

Un índice único `(usuario, fecha, tipo)` sostiene todo esto desde la base de
datos: el estado queda deliberadamente **fuera** de la clave, para que
justificar el exceso del martes no permita que la siguiente pasada lo vuelva a
abrir.

## Al empleado se le avisa uno a uno; a quien revisa, con una cola

Un exceso detectado genera notificación **solo para quien hizo las horas**. Para
él no es recurrente: es su martes, y es el único que sabe si fue una intensiva
pactada o un fichaje mal cerrado. Avisarle antes de que nadie decida es lo que
le da tiempo a pedir la corrección.

A los gestores no. Una empresa mediana genera decenas de excesos al mes, y un
correo por cada uno a cada gestor es spam por diseño — un buzón que se ignora es
peor que no avisar. Quien revisa trabaja desde el contador de avisos abiertos
del panel y la bandeja que hay detrás, que es como se lleva un trabajo
recurrente.

La excepción es la **bolsa al límite**, que sí va a los dos lados: pasa como
mucho una vez al año por persona y cambia lo que la empresa puede seguir
pidiéndole el resto del ejercicio. Salta al **80 %** y no al agotarse, porque
enterarse de que se ha superado el tope cuando ya se ha superado no le sirve a
nadie: el aviso existe para poder no llegar.

## Detalles que costaron tiempo

`avisos.tipo` tiene un `CHECK (tipo IN (...))` que la migración **reemplaza
entero**. Al añadir los dos tipos de esta fase había que volver a listar los seis
anteriores; listar solo los de las fases A y F habría dejado fuera los tres de
correcciones que añade la Fase E, y los avisos de corrección habrían empezado a
fallar dentro de un listener `@Async`, en silencio y lejos de la causa.

El sembrador de demo llama al detector **de verdad** en vez de inventar avisos.
Cuesta lo mismo y da dos cosas: los datos de demo no pueden contradecir a la
lógica, y cada arranque en modo demo es una prueba de humo del proceso nocturno.
Eso sí, obliga a un `flush()` explícito antes: el detector lee con una consulta
nativa, y Hibernate no garantiza volcar antes de una consulta así lo que tiene
pendiente en el contexto de persistencia. Sin él, el detector miraría una tabla
vacía — un fallo que no deja ningún error, solo un resultado vacío.

## Consecuencias

**A favor**

- La aplicación sabe decir si se han superado los dos límites del Estatuto, que
  es para lo que existe el registro horario.
- El prorrateo por días hábiles reales aprovecha el calendario de la Fase C: los
  festivos dejan de ser decorativos y pasan a cambiar un cálculo.
- La bolsa derivada no puede mentir después de una corrección, que es
  exactamente el escenario que la Fase E volvió rutinario.
- Los avisos son revisables y reversibles mientras estén abiertos: el proceso
  nocturno se puede equivocar sin dejar rastro permanente.

**En contra**

- **Un exceso no se detecta hasta la noche siguiente.** No hay aviso en el
  momento de fichar la salida, y podría haberlo. Se descartó porque el umbral
  semanal necesita la semana entera y porque avisar sobre una jornada que
  todavía se puede corregir generaría avisos que se retirarían solos.
- El aviso semanal no apunta a ningún fichaje concreto — el exceso es de la suma
  de varias jornadas y señalar a una cualquiera sería arbitrario —, así que
  desde él no se puede saltar a corregir nada.
- **Un exceso diario y el semanal que lo contiene se cuentan los dos** si ambos
  se aceptan. No es doble contabilidad accidental sino el criterio elegido: son
  dos límites legales distintos y pasarse de los dos es peor que pasarse de uno.
  Quien revisa puede justificar el que sobre, y el contador respeta esa decisión.
- La jornada semanal contratada es un único número por persona
  (`usuarios.horas_semanales`). Quien tenga jornada irregular repartida por
  convenio saldrá mal medido, y arreglarlo pide un calendario de jornada que
  esta fase no aborda.
