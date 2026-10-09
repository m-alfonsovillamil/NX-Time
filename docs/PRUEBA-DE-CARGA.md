# Prueba de carga

Qué aguanta NX Time y qué se rompe primero. Medido el 9 y 10 de octubre de 2026
sobre `main` (`e57610b`), antes de optimizar nada: es de donde sale el orden de
lo que viene después.

## Lo que ha salido

1. **Una oficina de más de diez personas no puede entrar a la vez.** El límite
   de entradas es de diez por minuto **por IP**, y una oficina sale a internet
   por una sola. De cuarenta personas entrando desde la misma IP, treinta se
   llevaron un 429. No hace falta carga: pasa con once.
2. **Lo que se queda sin fuelle es la CPU, y lo que la gasta es entrar con
   contraseña.** Con la décima de CPU del plan gratuito, cien personas entrando
   en un minuto hunden el servicio (medio minuto de espera y errores). Las
   mismas cien personas **con la sesión ya abierta** fichan sin un solo fallo.
3. **Cuando la base no da abasto, el servidor contesta «tu sesión no vale».**
   Si no consigue una conexión para comprobar el token, responde 401 en vez de
   503, y los clientes entienden un 401 como «vuelve a entrar»: justo lo más
   caro, en el peor momento.
4. **Comprobar la traza entera no escala.** Con un año de fichajes de tres mil
   personas (1,5 millones de movimientos) el recorrido completo tardó
   **46 minutos**, con la base en la misma máquina. Lee cada fichaje y cada
   persona de uno en uno.
5. **El cerrojo global al fichar no es el problema que se esperaba.** Con mil
   personas fichando en diez segundos, como mucho tres esperaban el cerrojo.
6. **La base crece más que el plan gratuito.** Ese año de tres mil personas
   ocupa 1,3 GB, y el plan gratuito de Neon da 0,5.

Los listados y el uso normal no dieron ningún problema: con un año de historial,
todo por debajo de 40 ms.

## Cómo se ha medido

**Los datos** (`scripts/carga/sembrar.sql`): sobre la demo, 100 empresas de 30
personas, una jornada por persona y día laborable durante un año, y los dos
movimientos de auditoría de cada jornada.

| | |
|---|---|
| Empresas | 102 |
| Personas | 3.011 |
| Jornadas | 783.439 |
| Movimientos de auditoría | 1.566.000 |
| Tamaño de la base | 1.282 MB (la traza, 1.080 MB; las jornadas, 190 MB) |

**La carga** (`scripts/carga/carga.mjs`): un guion de Node sin dependencias.
Cada persona va con su propia IP, salvo en el escenario de la oficina.

**El servidor**: el jar que se despliega, con lo que lleva producción: cinco
conexiones a la base, diez entradas por minuto e IP, treinta peticiones caras
por minuto y cuenta, y 30 s de tope por consulta. En dos sitios:

- **Sin límite de CPU**, en el portátil (16 núcleos). Quita la CPU de en medio
  y deja ver la base, las conexiones y los cerrojos.
- **En un contenedor con 512 MB y la CPU limitada** a 0,1 (lo que da el plan
  gratuito de Render) y a 0,5.

PostgreSQL 16 en Docker, en la misma máquina.

## «Las 9:00»: todos fichando la entrada

Cada persona entra, mira si tiene la jornada abierta, ficha y pide su día: cuatro
peticiones. Los tiempos son de **fichar**, en milisegundos.

### Sin límite de CPU

| Personas | Cómo llegan | Entran con | Fichar: mediana | p95 | Fallos |
|---:|---|---|---:|---:|---:|
| 100 | todas a la vez | contraseña | 910 | 1.318 | 0 |
| 300 | todas a la vez | contraseña | 1.658 | 2.546 | 0 |
| 500 | en un minuto | contraseña | 26 | 32 | 0 |
| 500 | en un minuto | sesión abierta | 25 | 31 | 0 |
| 1.000 | en diez segundos | sesión abierta | 159 | 266 | 0 |

Con CPU de sobra, las cinco conexiones dan para unas 400 peticiones por segundo
contra una base que está al lado. Mientras mil personas fichaban en diez
segundos se miró la base dos veces por segundo: de media había menos de una
consulta en marcha, y como mucho tres sesiones esperando el cerrojo de la
traza. **El cerrojo global, que era la hipótesis de partida, no es lo que
frena.**

Las dos primeras filas son más lentas porque entrar con contraseña cuesta unos
80 ms de CPU por persona (BCrypt), y trescientas a la vez son 24 segundos de
CPU.

### Con la CPU limitada

| CPU | Personas en un minuto | Entran con | Entrar: mediana | Fichar: mediana | Fichar: p95 | Fallos |
|---:|---:|---|---:|---:|---:|---|
| 0,1 | 100 | contraseña | 31.628 | 20.599 | 51.100 | 11 entradas con 500; después, 401 y esperas agotadas |
| 0,1 | 100 | sesión abierta | 564 | 1.399 | 2.494 | 0 |
| 0,5 | 100 | contraseña | 132 | 49 | 178 | 0 |
| 0,5 | 300 | contraseña | 527 | 206 | 617 | 0 |
| 0,5 | 600 | contraseña | 15.328 | 8.002 | 35.502 | 0 (pero hasta 54 s de espera) |

Esto es lo que se rompe primero. Con 0,1 de CPU el servicio no llega a dos
entradas con contraseña por segundo; con 0,5 aguanta cinco y no diez. Por encima se
forma cola, y la cola arrastra a todo lo demás por dos motivos que salen en el
log:

- **La contraseña se comprueba con una conexión cogida.** El `login` va entero
  en una transacción, así que cada entrada retiene una de las cinco conexiones
  mientras espera su turno de CPU. El pool llegó a tener 5 ocupadas y 90
  peticiones esperando (`total=5, active=5, waiting=90`), y a los 45 segundos
  se rinden con un 500.
- **Sin conexión, el filtro del token responde 401.** Trata cualquier fallo al
  cargar a la persona como «token inválido». Doce peticiones de gente con la
  sesión en regla se llevaron un 401 por eso.

Y explica por qué importa tanto la diferencia entre la web y la app: **en la
web la sesión dura doce horas**, así que todo el mundo entra con contraseña
cada mañana. En la app dura treinta días, y a las nueve solo se renueva.

A 0,1 de CPU el contenedor tardó además **seis minutos en arrancar** y usaba
443 de sus 512 MB.

## La oficina

Cuarenta personas, una detrás de otra, todas desde la misma IP:

| Entradas | Bien | Rechazadas (429) |
|---:|---:|---:|
| 40 | 10 | 30 |

El cupo se rellena a razón de una entrada cada seis segundos. Una oficina de
cincuenta personas tarda cinco minutos en entrar entera, y eso si nadie se
equivoca con la contraseña.

## Uso normal

Trescientas personas durante un minuto, cada una mirando algo cada dos o tres
segundos (su jornada, su historial, sus avisos, su resumen), y treinta gestoras
con el historial del equipo y el panel de la empresa. Sin límite de CPU, unas
120 peticiones por segundo:

| | Mediana | p95 | Máximo |
|---|---:|---:|---:|
| Lo de cualquiera (cinco rutas) | 9–13 | 21–26 | 134 |
| Lo de una gestora (tres rutas) | 9–18 | 23–30 | 163 |

Ni un fallo. Con un año de historial por persona, ningún listado se resiente.

A 0,1 de CPU, treinta personas (nueve peticiones por segundo) ya están en el
límite: medianas de medio segundo y p95 de tres.

## La comprobación de la traza

El mismo uso normal, con una cuenta de RRHH pidiendo a la vez la comprobación
completa de la traza. **Los demás no lo notaron** (mismos tiempos que sin
ella): el ADR 039 hace lo que dice, y el recorrido ocupa una sola conexión.

Lo que no funciona es el recorrido en sí: **46 minutos** (2.754 segundos) para 1,5 millones de
movimientos. Va en una sola transacción y, por cada bloque de mil movimientos,
carga de uno en uno los fichajes y las personas a los que apuntan (se ve en
la base mientras corre): al menos una consulta por jornada, más de setecientas
mil para leer una tabla. Contra Neon, con la red por medio,
será varias veces más.

No afecta a la comprobación de cada noche, que solo mira lo nuevo. Afecta al
botón de «comprobar ahora», a la primera comprobación de una instalación y a
la pasada completa periódica que se quería añadir.

## Lo que no se ha medido

- **La red hasta la base.** Aquí está al lado; en producción cada consulta
  cruza de Render a Neon. Todo lo que retiene una conexión pesará más de lo que
  sale aquí, y el cerrojo de la traza se sostiene durante más tiempo: que no
  frene en local no garantiza que no frene allí.
- **Cómo limita Render la CPU de verdad.** Aquí es un tope fijo; el plan
  gratuito deja ráfagas. Los seis minutos de arranque son peores que los de
  producción.
- **Cuadrantes, proyectos y ausencias.** No se sembraron: fichar con cuadrante
  o con proyecto hace más trabajo del que se ha medido.
- **Las tareas nocturnas** con este volumen, y en particular el cierre de
  jornadas olvidadas, que va en una sola transacción.
- **Repeticiones.** Cada escenario se lanzó una vez, y el guion corre en la
  misma máquina que el servidor. Las cifras dicen el orden de magnitud y dónde
  está el codo, no más.

## Qué se hace con esto

Por orden, y casi todo es poco código:

1. **El límite de entradas por IP.** Que una oficina pueda entrar: subirlo y
   apoyarse en el límite por cuenta, que ya existe y es el que de verdad frena
   a quien prueba contraseñas.
2. **Un 503, no un 401, cuando falla la base** al comprobar el token.
3. **Comprobar la contraseña sin una conexión cogida.**
4. **El recorrido de la traza**: leer solo lo que hace falta para comprobar la
   cadena (los hashes y de qué empresa es cada movimiento) en una consulta por
   bloque, y fuera de una transacción de cuarenta minutos.
5. **Decidir cuánto dura la sesión de la web.** Doce horas hacen que toda la
   plantilla entre con contraseña cada mañana. Es una decisión de seguridad
   (ADR 030), no solo de rendimiento.
6. **La cadena de auditoría por empresa** deja de ser urgente por rendimiento.
   Sigue teniendo sentido por lo demás: que una rotura no la noten todas y que
   comprobar la propia cueste lo que pesa la propia.

Y dos que no son código:

- **0,1 de CPU no da para una plantilla entrando a las nueve.** Con 0,5 entran
  trescientas personas en un minuto sin problema.
- **El almacenamiento**: 1,3 GB por año y tres mil personas, casi todo traza.

## Repetirlo

```bash
# 1. Una base desechable con los datos de demo (arrancar el backend una vez
#    con --spring.profiles.active=dev,demo contra ella, y pararlo).
# 2. El volumen, con el backend parado:
docker exec -i nxtime-postgres psql -U nxtime -d nxtime_carga \
  -v empresas=100 -v personas=30 -v dias=365 < scripts/carga/sembrar.sql

# 3. Una copia por tanda (fichar la entrada solo se puede una vez al día):
docker exec nxtime-postgres psql -U nxtime -d nxtime \
  -c "CREATE DATABASE nxtime_carga_a TEMPLATE nxtime_carga"

# 4. El backend, con lo de producción:
java -Xmx384m -jar nx-time-backend/build/libs/nx-time-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev --spring.jpa.show-sql=false \
  --spring.datasource.url=jdbc:postgresql://localhost:5433/nxtime_carga_a \
  --spring.flyway.url=jdbc:postgresql://localhost:5433/nxtime_carga_a \
  --spring.datasource.hikari.maximum-pool-size=5 \
  --spring.datasource.hikari.minimum-idle=0 \
  --spring.datasource.hikari.connection-timeout=45000 \
  --application.security.rate-limit.trusted-proxies=1 \
  --application.security.rate-limit.peticiones-por-minuto=10 \
  --application.security.rate-limit.caro-por-minuto=30

# 5. Los escenarios:
node scripts/carga/carga.mjs entrada --personas 500 --rampa 60
node scripts/carga/carga.mjs entrada --personas 500 --rampa 60 --desde 500 --con-sesion
node scripts/carga/carga.mjs oficina --personas 40 --desde 1000
node scripts/carga/carga.mjs mixto --personas 300 --duracion 60
node scripts/carga/carga.mjs integridad --personas 300 --duracion 60
```

Para limitar la CPU, el mismo jar dentro de un contenedor con
`docker run --cpus=0.1 -m 512m`, apuntando a `host.docker.internal:5433`.
