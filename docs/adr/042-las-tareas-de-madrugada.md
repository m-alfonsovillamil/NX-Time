# 42. Las tareas de madrugada: una vez por turno, jornada a jornada y la traza entera cada semana

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

Seis tareas corren solas cada día, casi todas de madrugada: cierran las
jornadas que nadie cerró, buscan horas extra e incidencias de cuadrante,
anonimizan a quien lo pidió, comprueban la traza de auditoría y recuerdan la
firma mensual. Quedaban tres cosas pendientes de la lista de escalabilidad
(ADR 039 y 041), las tres de aquí:

- **Con dos instancias del servicio, cada tarea correría dos veces.** Son un
  `@Scheduled` dentro del proceso, y nada les decía que otra instancia ya la
  estaba haciendo. Hoy hay una sola instancia, pero durante un despliegue
  conviven la vieja y la nueva, y varias tareas mandan correos.
- **El cierre de jornadas iba en una sola transacción.** Una jornada que no se
  pudiera cerrar deshacía el cierre de todas las demás, esa noche y todas las
  siguientes, hasta que alguien la arreglase a mano. Y las escrituras de todas
  en la traza iban seguidas con el cerrojo de la cadena cogido (ADR 018).
- **Una fila de la traza ya comprobada no se volvía a mirar nunca.** La
  comprobación de cada noche arranca del último punto de control y solo mira lo
  nuevo. Quien cambiase un movimiento de hace un mes no saldría en ninguna
  noche; solo si alguien pulsaba «Comprobar» en la pantalla de integridad.

Al tocar el verificador salieron dos fallos que ya estaban:

- **El punto de control podía firmar una fila que nadie había mirado.** Al
  acabar el recorrido se releía la última fila de la tabla. Si alguien fichaba
  entre el final del recorrido y esa lectura, el punto de control apuntaba a su
  movimiento, y la noche siguiente se empezaba después de él.
- **Quitarle el final a la traza no se notaba.** Una cadena recortada sigue
  cuadrando: cada fila que queda enlaza con la anterior. Lo único que dice que
  había más es el punto de control, y nadie lo miraba.

## Decisión

### Una tarea, una vez por turno

Cada ejecución lleva apuntada la instancia que la hace: un identificador al
azar por arranque (columna `instancia`, V42). Antes de empezar una tarea se
mira si **otra** instancia la ha empezado ya en el turno que toca, y entonces
no se hace ni se registra nada.

Mirar y apuntar van en una transacción corta con un cerrojo de PostgreSQL
(`pg_advisory_xact_lock`, uno por tarea). Sin él, dos instancias que disparan
la tarea en la misma milésima miran las dos antes de que ninguna apunte. El
cerrojo dura esas dos consultas, no la tarea.

Tres detalles que no son obvios:

- **El cerrojo es de transacción, no de sesión.** Producción llega a la base
  por el pooler de Neon, que reparte las conexiones por transacción: un cerrojo
  de sesión se quedaría en una conexión que ya está sirviendo a otro.
- **El turno tiene un minuto de holgura.** Si una tarea se dispara unas
  milésimas antes de las 3:00 según su reloj, el turno «anterior a ahora» sería
  el de ayer; la ejecución de ayer, tras un despliegue, es de otra instancia, y
  la de hoy no correría. La misma holgura cubre relojes que no van a la par.
- **Si la otra instancia se queda a medias, el turno se pierde.** No se repite
  porque no se sabe hasta dónde llegó. Se ve: `/estado/tareas` la da por no
  hecha y el aviso de cada mañana salta.

La misma instancia sí puede repetir una tarea. En producción no hay camino que
lo haga; es lo que deja a los tests lanzarla varias veces.

### El cierre de jornadas, cada una en su transacción

Se saca la lista de las olvidadas y cada una se cierra en su propia
transacción, que la vuelve a cargar y a mirar: entre la lista y su turno, su
dueña ha podido fichar, y el fichaje ya la cierra.

Si alguna no se puede cerrar, se sigue con las demás y **la tarea acaba en
ERROR diciendo cuáles**. En error a propósito aunque las demás estén cerradas:
una jornada que no se deja cerrar hay que mirarla.

### La traza entera, la madrugada del domingo

La misma tarea de las 3:50 recorre la traza desde el principio los domingos, y
el resto de noches sigue mirando solo lo nuevo.

**También se recorre entera si la última ejecución acabó en error.** Sin esto,
un fallo encontrado el domingo en una fila vieja se apagaría solo el lunes,
cuando se volviese a mirar solo lo nuevo. Así sigue en rojo hasta que alguien
lo investigue. Si el error fue otra cosa (la base caída esa noche), cuesta un
recorrido de más.

### El punto de control firma lo recorrido, y se comprueba que sigue ahí

- El punto de control guarda la última fila **recorrida** y su hash, no la
  última de la tabla.
- Antes de dar nada por bueno, las dos comprobaciones miran que la fila del
  último punto de control siga existiendo con el mismo hash. Si no, la traza se
  da por rota: le han quitado o cambiado el final.

## Lo que se ha medido

Sobre la base de la prueba de carga (`docs/PRUEBA-DE-CARGA.md`: 1.566.000
movimientos), con la base en la misma máquina y cada cosa una vez:

| | |
|---|---|
| El recorrido completo, en su transacción | 19 s |
| La comprobación de una noche sin movimientos nuevos | 7 ms |
| Cerrar tres jornadas olvidadas, cada una en su transacción | 0,45 s |
| V41 y V42 sobre esa base | 0,17 s |

**Esos 19 segundos se quedan cortos para una traza de verdad.** Los movimientos
sembrados son de los que solo se comprueba el enlace; de los que escribe la
aplicación se recalcula además el contenido. Medido con veinte mil escritos
por la aplicación: unos 16 microsegundos por movimiento, frente a 12. Millón y
medio serían unos 25 segundos. La red hasta Neon no está en ninguna de estas
cifras.

## Lo que esto no arregla

- **Lo que se escriba y se borre entre dos comprobaciones no deja rastro.** El
  punto de control solo sabe hasta dónde llegó la última.
- **Un fallo en la traza no se puede cerrar desde la aplicación.** La tabla es
  solo de añadir, también los puntos de control. Una cadena rota deja la tarea
  en rojo cada noche hasta que alguien con acceso a la base decida qué hacer.
- **Los domingos la comprobación tiene una conexión cogida mientras dura.**
  Una de cinco, de madrugada. Si la traza creciera hasta no acabar antes de
  que el aviso de cada mañana mire `/estado/tareas`, la tarea saldría como no
  hecha los domingos: es la señal para moverla de hora.

## Lo que no se ha hecho

- **Un límite general por IP para toda la API.** Seguía en la lista y se deja
  fuera a propósito:
  - La prueba de carga no lo pidió. Lo que es caro ya tiene su tope: entrar y
    lo que manda correos (ADR 041), y los informes y la comprobación de la
    traza (ADR 039).
  - Una oficina entera sale a internet por una sola IP. Un número que frene a
    alguien abusando frena también a una empresa de doscientas personas con un
    kiosco; uno que no las frene no frena nada.
  - Los cupos viven en la memoria de cada instancia. Un límite general, si
    llega a hacer falta, es trabajo de lo que haya delante del servicio (el
    proxy o una CDN), no de la aplicación.
- **El resto de lo que impide poner una segunda instancia** sigue igual: el
  límite de intentos, los cupos, las cachés, los códigos del SSO y el cerrojo
  de la comprobación manual viven en memoria (ADR 039).
- **La cadena de auditoría por empresa** (ADR 018), aplazada.

## Alternativas descartadas

- **ShedLock o una tabla de cerrojos.** Es lo habitual, y hace lo mismo que
  esto con una dependencia y una tabla más. La tabla de ejecuciones ya existía
  y ya decía quién había corrido qué; solo le faltaba la instancia.
- **Un cerrojo de sesión que dure toda la tarea.** No funciona detrás del
  pooler, y una instancia que muriese a mitad lo soltaría sin que nadie supiera
  que la tarea se quedó a medias.
- **Que la segunda instancia repita una tarea que la primera dejó en error.**
  Mandaría dos veces los correos que la primera llegó a mandar.
- **Cerrar las jornadas por bloques de cien.** Menos transacciones, pero una
  jornada mala seguiría arrastrando a noventa y nueve. De una en una son
  tantas transacciones cortas como jornadas olvidadas esa noche.
- **Recorrer la traza entera todas las noches.** Cabe hoy, pero crece con cada
  fichaje de cada empresa; una vez por semana da el mismo aviso con siete veces
  menos trabajo, y la pantalla de integridad sigue ahí para quien no quiera
  esperar.
- **Borrar los puntos de control cuando el recorrido completo encuentra algo**,
  para que la comprobación de cada noche vuelva a pasar por el tramo malo. La
  aplicación no puede borrarlos (V28), y está bien que no pueda.
