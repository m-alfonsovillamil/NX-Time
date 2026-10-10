# 41. El acceso bajo carga: que entrar no tumbe lo demás

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

La prueba de carga (`docs/PRUEBA-DE-CARGA.md`) midió qué se rompe primero con
una plantilla entera llegando a las nueve. No fue lo que se esperaba, el
cerrojo global de la traza de auditoría, sino **entrar**:

- **Una oficina de más de diez personas no podía entrar a la vez.** El límite
  era de diez peticiones por minuto y por IP para todo lo de `/auth`, y una
  oficina sale a internet por una sola. De cuarenta personas, treinta se
  llevaron un 429.
- **Comprobar una contraseña se hacía con una conexión a la base cogida.** Es
  BCrypt, unos 80 ms de CPU a propósito, y el login entero iba en una
  transacción. Con cinco conexiones y poca CPU, unas cuantas entradas las
  retenían todas mientras esperaban procesador y nadie más podía ni fichar: el
  pool llegó a tener cinco ocupadas y noventa peticiones esperando.
- **Sin conexión, el servidor respondía 401.** El filtro del token trataba
  cualquier fallo al leer a la persona como «token inválido». Para un cliente
  un 401 es «vuelve a entrar», que es lo más caro que hay.
- **En la web, todo el mundo entraba con contraseña cada mañana**, porque su
  sesión duraba doce horas. Con la sesión ya abierta, las mismas cien personas
  que hundían el servicio fichaban sin un solo fallo.
- **Comprobar la traza entera tardó 46 minutos** con millón y medio de
  movimientos: cargaba cada fichaje y cada persona de uno en uno.

## Decisión

### Entrar tiene su propio cupo por IP

Dos cupos por IP en vez de uno, que no se gastan el uno al otro:

- **Entrar** (el login y las vueltas del SSO): **sesenta por minuto**.
- **Lo demás** (registrar una empresa, pedir y canjear códigos, emparejar un
  kiosco): las diez de siempre. Varias mandan correos.

Sesenta y no más por la CPU: una entrada por segundo es lo que aguanta el plan
gratuito. Se descartó subirlo a mil: sería dejar que un solo ordenador gastase
toda la CPU a base de entradas, y que probase una contraseña corriente contra
mil cuentas por minuto. Contra una cuenta concreta sigue frenando el límite por
cuenta (ADR 034), venga de la IP que venga.

### La contraseña se comprueba sin conexión, y por turnos

El login ya no va en una transacción. La persona se lee en una, corta; la
contraseña se comprueba sin ninguna; y solo lo que escribe (la sesión, o el
código de confirmación) abre la suya.

Y las contraseñas se comprueban **por turnos, tantas a la vez como
procesadores** (`TurnoDeContrasena`). Quien espera turno no tiene ninguna
conexión cogida. Si el turno no llega en veinte segundos, se responde 503 y que
vuelva a intentarlo.

El turno no estaba en el plan: salió midiendo. La transacción hacía también,
sin querer, de tope: nunca había más contraseñas comprobándose que conexiones.
Al quitarla, cien hilos se repartían el procesador y ninguno acababa.

### Sin base, 503 y no 401

Cuando no se puede leer a la persona porque la base no contesta o no queda
ninguna conexión libre, se responde **503 con `Retry-After`**: en el filtro del
token, en el login (donde además ya no cuenta como intento fallido de esa
cuenta) y en cualquier controlador.

### La sesión de la web dura siete días

Antes, doce horas. Se renueva con cada uso, así que solo caduca la de quien
deja de entrar una semana. Va en una propiedad
(`application.security.web-session.duracion`).

Lo que justificaba las doce horas (ADR 019) era que el token vivía en un
navegador, al alcance de cualquier script. Desde el ADR 030 va en una cookie
`HttpOnly`, se rota en cada uso y reutilizar uno viejo cierra la sesión entera.

### El recorrido de la traza, en una consulta por bloque

El verificador ya no carga entidades: una consulta por bloque trae lo justo
para comprobar la cadena (lo que entra en el hash, el enlace con el anterior y
de qué empresa es cada movimiento). Cada bloque coge una conexión, lee y la
suelta.

## Lo que se ha medido

Los mismos escenarios, antes y después. Cada uno, una vez; con la base en la
misma máquina.

| | Antes | Después |
|---|---|---|
| Comprobar la traza (1,5 M de movimientos) | 46 minutos | 18 segundos |
| Oficina: entradas desde una sola IP | 10 de 40 | 68 de 70 |
| 300 personas a la vez, sin límite de CPU: fichar (mediana / p95) | 1.658 / 2.546 ms | 785 / 1.300 ms |
| 0,5 CPU, 300 en un minuto: entrar (mediana) | 527 ms | 2.277 ms |
| 0,5 CPU, 300 en un minuto: fichar (mediana / p95) | 206 / 617 ms | 106 / 197 ms |
| 0,5 CPU, 600 en un minuto: fichar (mediana / p95) | 8.002 / 35.502 ms | 99 / 899 ms |
| 0,5 CPU, 600 en un minuto: cuántos entran | 600, esperando hasta 54 s | 436; a 164 se les dice que vuelvan a intentarlo |
| 0,1 CPU, 100 en un minuto: fichar (mediana / p95) | 20.599 / 51.100 ms | 311 / 709 ms |
| 0,1 CPU, 100 en un minuto: errores | 11 con 500, y después 401 y esperas agotadas | 8 con 503 |

## Consecuencias

- **Entrar es más lento cuando hay cola, a propósito.** Con media CPU y
  trescientas personas en un minuto, entrar pasa de medio segundo a algo más de
  dos. A cambio, quien ya está dentro ficha más rápido que antes, y cuando
  llega más gente de la que cabe el servicio no se hunde: los que no caben
  reciben un 503 a los veinte segundos.
- **Con poca CPU sigue sin caber una plantilla entrando a la vez.** Esto no
  crea procesador: reparte el que hay. Lo que de verdad quita carga es no tener
  que entrar, y de eso se ocupa la sesión de siete días.
- **Un navegador que se queda abierto sigue dentro una semana**, no medio día.
  En un ordenador compartido hay que cerrar sesión; y si el aviso de cierre no
  llega al servidor, la cookie sigue valiendo hasta que caduque. Una
  instalación con ordenadores compartidos puede volver a las doce horas con
  `WEB_SESSION_DURACION=12h`.
- **Las sesiones abiertas no se alargan solas**: las que ya existen caducan a
  sus doce horas, y la siguiente entrada ya dura siete días.
- El turno de contraseñas y los dos cupos viven en la memoria de la instancia,
  como los demás límites (ADR 039).

## Lo que se aprendió por el camino

Tres fallos de este mismo cambio salieron **midiendo, con los tests en verde**:

1. **Una consulta declarada en un repositorio no abre transacción**, y fuera de
   una, dentro de un método marcado «sin transacción», Spring deja su conexión
   cogida hasta que el método termina. La primera versión del login retenía así
   una conexión mientras pedía otra, y con trescientas personas a la vez el
   pool se bloqueó: no entró nadie. El test preguntaba «¿hay una transacción
   abierta mientras se comprueba la contraseña?»; ahora pregunta cuántas
   conexiones hay cogidas, y hay otro con más entradas a la vez que conexiones.
2. **Lo mismo le pasaba ya a la comprobación de la traza** (ADR 039): quien la
   pedía averiguaba primero de qué empresa era y después esperaba turno con esa
   conexión cogida. Su test llamaba a otra variante del método. Lo destapó la
   detección de fugas del pool, y el test va ahora por el camino real.
3. **El tope de contraseñas a la vez**, que es lo que cuenta el apartado de
   arriba.

## Lo que no se ha hecho

- **El PIN del kiosco y los códigos de acceso** también son BCrypt y no pasan
  por el turno. Un kiosco es una tablet y va de uno en uno; los códigos tienen
  su propio límite.
- **Reintentar el cierre de sesión** si el aviso al servidor falla.
- **Una pasada completa de la traza cada semana.** Con 18 segundos ya es
  viable; queda para el siguiente cambio.
- **Un límite general por IP para toda la API**, el cierre nocturno por bloques
  y el cerrojo de las tareas programadas, que seguían en la lista.
- **La cadena de auditoría por empresa** (ADR 018): la prueba no la pide por
  rendimiento. Sigue teniendo sentido por aislamiento.

## Alternativas descartadas

- **Subir el límite por IP sin separar los cupos.** Dejaría registrar empresas
  y pedir códigos, que mandan correos, a sesenta por minuto.
- **Dejar el login en su transacción y subir el pool.** El pool de cinco es el
  del plan gratuito de Neon, y con más conexiones el problema solo se retrasa:
  lo que falta es CPU.
- **Bajar el coste de BCrypt.** Es lento a propósito; abaratarlo es abaratar
  también probar contraseñas robadas.
- **Hacer transaccional la consulta de la persona para todo el mundo.** Habría
  arreglado la conexión retenida, pero es un viaje más a la base en cada
  petición, porque el filtro del token la usa en todas.
