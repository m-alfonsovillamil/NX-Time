# 39. Límites a lo caro: que lo más pesado no sirva para tumbar el servicio

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

El servicio corre en una sola instancia con un **pool de cinco conexiones** a la
base de datos. Hasta aquí solo tenía límite de peticiones lo que se puede pedir
sin sesión (el login y poco más, ADR 034). Con sesión no había ninguno.

Y tener sesión no es una barrera: **registrar una empresa es público**, y quien
la registra es su ADMIN. Con eso se podía pedir en bucle lo más caro que hay, la
comprobación manual de la traza de auditoría, que recorría la cadena entera en
cada petición, con su hilo y su conexión mientras durase.

Medido en local, con el pool de cinco y 482 movimientos en la traza (un recorrido
de segundo y medio): **treinta peticiones a la vez contra esa ruta dejaban «mi
jornada» respondiendo en diez segundos** en vez de en treinta milisegundos. No
hacía falta más para dejar a una plantilla sin fichar.

Lo señaló alguien de fuera al ver la pantalla: los cuellos de botella son lo
primero que se ataca.

## Decisión

### Un recorrido a la vez, y compartido

La cadena es una sola para todas las empresas, así que un recorrido vale para
todas: se hace **uno a la vez**, y a cada empresa se le dan sus cifras de ese
mismo recorrido (`VerificadorDeAuditoria.recorridoCompartido`).

- Quien llega mientras otro recorre **espera y se lleva ese resultado**.
- El resultado **vale medio minuto**: pulsar el botón veinte veces es un
  recorrido.
- **Quien espera no ocupa una conexión.** Es lo que de verdad lo arregla: el
  método se llama fuera de toda transacción y abre la suya solo para recorrer.
  Esperando dentro de una, cada petición en cola retendría su conexión sin
  usarla y el pool se agotaría igual. Hay un test que lo comprueba contra el
  pool de verdad, y que falla si se quita.
- **La espera tiene tope** (veinte segundos): pasado, se responde 503. Un hilo
  esperando sin límite es otra forma de quedarse sin hilos.

Lo que se pierde: «comprobar ahora» puede enseñar un recorrido de hace hasta
medio minuto. La pantalla lo dice.

### Un cupo por cuenta para lo que cuesta

Treinta peticiones por minuto y cuenta, entre todas estas rutas
(`LimiteDeLoCaro`): informes, analítica, la exportación de mis datos y la
integridad. Pasado el cupo, 429 con `Retry-After`.

- **Por cuenta y no por IP**: aquí ya se sabe quién pide, y una oficina entera
  detrás de una IP no tiene por qué compartir el cupo.
- **Solo lo caro.** Fichar, el panel y los listados van sin tope a propósito:
  es lo que la gente usa todo el día, y un límite ahí molesta antes a quien
  trabaja que a quien ataca.
- El aviso en el log sale **una vez por cuenta y minuto**. Midiendo esto,
  treinta peticiones en bucle dejaron 26.000 líneas en quince segundos: un
  aviso que llena el log durante un ataque es parte del ataque.

### Ninguna consulta dura más de treinta segundos

`statement_timeout` en cada conexión del pool. Sin tope, una consulta lenta se
queda con su conexión lo que tarde. Cuenta también el tiempo esperando un
cerrojo: un fichaje que no consiga su turno en la cadena de auditoría falla en
vez de colgarse. No afecta a Flyway, que va con su propia conexión. Se puede
cambiar con `DB_STATEMENT_TIMEOUT`.

## Consecuencias

- La misma prueba, con el cambio: **«mi jornada» responde en 35 ms de mediana**
  (112 ms el peor caso) mientras treinta peticiones martillean la integridad.
- La batería entera pasa con el tope de treinta segundos puesto: ningún camino
  legítimo se le acerca hoy.
- Con una traza enorme, un recorrido que tarde más de veinte segundos hará que
  quien espere reciba un 503. Es lo correcto, y también la señal de que toca la
  cadena por empresa (ADR 018).
- **Todo esto vive en la memoria de la instancia**: el cerrojo del recorrido, su
  resultado y los cupos. Con dos instancias habría dos recorridos y el doble de
  cupo. Es una de las cosas que hay que mover antes de poner una segunda.

## Lo que no se ha hecho

- **Un límite general por IP para toda la API.** Una petición rechazada por el
  cupo sigue pasando por el filtro del token, que consulta la base. En la prueba
  aguantó 1.700 peticiones por segundo sin notarse, pero es un coste que no
  tiene tope.
- **Varias instancias**: las tareas programadas no tienen cerrojo entre
  instancias, y los límites, las cachés y los códigos del SSO están en memoria.
- **Una cadena de auditoría por empresa** (ADR 018): es lo que hace que
  comprobarla cueste lo que pesa la propia y no la de todas.
- **Volver a comprobar lo antiguo sin que nadie pulse.** La tarea de cada noche
  solo mira lo nuevo desde el último punto de control: una fila manipulada
  después de comprobada solo la encuentra el recorrido completo.
- **Una prueba de carga de verdad**, con el escenario de las nueve de la mañana.
  Lo de aquí mide un ataque concreto, no la capacidad del servicio.

## Alternativas descartadas

- **Que «comprobar ahora» parta del punto de control nocturno**, como la tarea
  de cada noche. Sería barato, pero dejaría de detectar una manipulación de
  filas antiguas, que es justo para lo que alguien pulsa ese botón.
- **Una cola de trabajos** con el resultado para más tarde. Es lo propio cuando
  el recorrido tarde minutos; hoy tarda segundos, y uno a la vez y compartido da
  la misma protección sin una pieza más.
- **Quitarle el botón a quien no sea de la empresa que mantiene el servicio.**
  La comprobación es de cada empresa: es su prueba ante una inspección.
