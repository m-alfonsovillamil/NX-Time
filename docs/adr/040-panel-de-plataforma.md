# 40. El panel de plataforma: ver la instalación entera sin ser de ninguna empresa

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

Todo lo que se puede ver en NX Time es de **una empresa**. Los cuatro roles
(EMPLEADO, GESTOR, RRHH, ADMIN) son de una empresa, cada consulta se recorta por
la empresa de quien pregunta, y no había nadie que pudiera ver la instalación
entera: cuántas empresas hay dadas de alta, cuánta gente tienen, si usan el
servicio o se registraron y no volvieron.

Quien mantiene el servicio lo necesita, y hasta ahora la única forma era abrir
la base de datos a mano.

Hay además una cosa que ya se le había prometido a ese alguien y no tenía dónde
leerla. Desde octubre de 2026 a cada empresa se le dan solo sus cifras de la
traza de auditoría, y si la cadena se rompe en una fila ajena se le dice que
falla pero no dónde: «avisa a quien administra el servicio». Quien administra
el servicio solo lo tenía en el log.

## Decisión

### Un permiso que no es de ningún rol

`plataforma:ver` no lo concede ningún rol. Lo tienen las cuentas cuyo correo
esté en la variable de entorno **`PLATAFORMA_OPERADORES`** (separados por
comas). Vacía, que es como viene, no lo tiene nadie.

Por qué no un rol más:

- **Los roles se conceden desde dentro.** Un ADMIN nombra a otro ADMIN, y
  registrar una empresa es público: quien la registra es su ADMIN. Un rol que
  cruzase todas las empresas tendría que ser el único que no se pudiera
  conceder por el camino por el que se conceden todos, y esa excepción viviría
  en cada pantalla y cada endpoint que tocan roles.
- **Un rol es de una empresa.** El operador no pertenece a ninguna en
  particular; su cuenta está en una porque todas las cuentas lo están, y puede
  ser una empleada rasa.

Por qué una variable de entorno y no una tabla: quien mantiene el servicio ya
gestiona ahí las demás, cambiarla no pide tocar la base a mano, y **no hay
ningún endpoint que la pueda escribir**: lo que no existe no se puede atacar.

Por qué fiarse del correo: una cuenta solo la usa quien tiene su buzón (quien
registra una empresa confirma el correo antes de entrar, a quien dan de alta le
llega un código para elegir contraseña, y por SSO solo se entra con el correo
que el proveedor garantiza), y el correo de una cuenta no se puede cambiar. Aun
así se exige además que la cuenta esté activa y con el correo confirmado.

Los permisos se resolvían en cuatro sitios, todos a partir del rol: el principal
de cada petición, las dos respuestas de sesión y el perfil. Ahora los cuatro
pasan por `OperadoresDePlataforma`, de modo que lo que autoriza el servidor y lo
que se le enseña al cliente salen de la misma pieza.

### Qué se ve

Cuatro rutas de solo lectura bajo `/api/v1/plataforma/`:

- **`/resumen`**: empresas y cuentas en total, cuántas empresas han fichado en
  los últimos treinta días, altas por semana, fichajes de hoy, el estado de las
  tareas nocturnas (lo mismo que `/estado/tareas`) y qué dejó dicho la última
  comprobación automática de la traza.
- **`/empresas`**: la lista, paginada, con búsqueda por nombre y cuatro órdenes
  (nombre, alta, plantilla, actividad).
- **`/empresas/{id}`**: una empresa. Plantilla por rol, fichajes, sesiones desde
  la web y desde la app, cuentas con Google o Microsoft, kioscos, dispositivos
  con push, departamentos, proyectos, lo que ocupan sus adjuntos, borrados de
  datos pendientes y movimientos de auditoría.
- **`/integridad`**: la comprobación completa de la traza, con las cifras de
  toda la instalación y, si está rota, **la fila y de qué empresa es**. Es el
  mismo recorrido compartido del ADR 039, con sus mismos límites.

### Qué no se ve

- **Ni un fichaje ni un dato de ningún empleado.** Son cifras agregadas.
- La única excepción son **el nombre y el correo de los ADMIN** de cada empresa:
  quien presta el servicio tiene que poder dirigirse a quien lo contrató.
- **No se puede hacer nada.** Suspender o borrar una empresa no está aquí.

Cada consulta al detalle de una empresa deja una línea en el log, con quién la
hizo y a cuál.

### La fecha de alta

Una empresa no tenía fecha de alta. La V41 añade `empresas.creada_en`, que
**admite nulos**: las nuevas la llevan siempre, y a las que ya existían se les
pone el rastro más antiguo que quedaba de ellas (su primer fichaje, la primera
sesión de alguien suyo o el primer código de acceso). Para esas es una
estimación; las que no habían dejado ninguno se quedan sin fecha, que es mejor
que una inventada.

### Que no cueste más de lo que vale

Las cifras de la lista salen de **una consulta por métrica para toda la página**
(`GROUP BY`), no de una por empresa: con el tope de treinta segundos por
consulta del ADR 039, una lista hecha a base de consultas por empresa dejaría de
responder en cuanto hubiera volumen. Todas entran a los fichajes por empresa y
rango de fechas, que es el índice que ya existe, y las rutas están bajo el cupo
por cuenta de lo caro.

## Consecuencias

- Hay por primera vez código que mira a través de las empresas. Está en un solo
  repositorio (`PlatformRepository`), que solo usa un servicio, detrás de un
  permiso que no sale de ningún rol. Hay un test que comprueba, ruta por ruta,
  que un ADMIN recibe 403 aunque pregunte por su propia empresa, y otro que
  falla si el permiso entrase en algún rol.
- `RoleAuthorities` deja de ser «lo que tiene cada rol» para ser «todas las
  authorities que existen», con una sección para lo que está fuera de los
  roles. Es lo que leen los tests que cruzan los permisos con los endpoints y
  con las secciones de la web.
- Quitar a alguien de la lista le quita el permiso en su siguiente petición: el
  principal se arma en cada una. Lo que no cambia hasta que vuelva a entrar es
  lo que su cliente pinta, que le llegó con la sesión.
- Cambiar la variable reinicia el servicio en Render.

## Protección de datos

Quien presta el servicio trata los datos de las empresas por cuenta de ellas: es
**encargado del tratamiento**, y las empresas son las responsables. Este panel
es coherente con ese papel en lo que enseña (cifras de uso y el contacto de
quien contrató), y deja constancia de cada consulta al detalle de una empresa.

Lo que falta no es código: el contrato de encargo que diga qué puede mirar el
operador y para qué. Depende de una decisión que sigue abierta, si NX Time se
ofrece como servicio o se instala en casa de cada cliente. En una instalación
propia el operador y la empresa son la misma organización y esto no aplica.

## Lo que no se ha hecho

- **Suspender o borrar una empresa.** Hoy sigue siendo a mano.
- **Un registro de consultas en la base.** Las del detalle van al log, que en
  Render se conserva unos días. Si el panel lo usa más de una persona, hará
  falta una tabla.
- **El detalle de por qué falló una tarea nocturna.** Se enseña si corrió y
  cómo acabó; el motivo sigue en `ejecuciones_tarea.detalle`.
- **Distinguir en pantalla una fecha de alta estimada de una exacta.** Son
  estimadas todas las anteriores a la V41.

## Alternativas descartadas

- **Un quinto rol, `OPERADOR`.** Por lo dicho arriba: sería el único rol que no
  se puede conceder desde la aplicación y que no es de la empresa en la que
  está.
- **Una columna `es_operador` en `usuarios`.** Obliga a tocar la base a mano
  para darlo y para quitarlo, y deja el permiso a un `UPDATE` de distancia de
  cualquier fallo que permita escribir en esa tabla.
- **Una aplicación de administración aparte, con su propio acceso.** Es lo que
  haría un servicio grande. Aquí serían otro despliegue, otra sesión y otro
  conjunto de credenciales que cuidar para cuatro pantallas de solo lectura.
- **Consultar la base a mano.** Es lo que había. No deja rastro de quién miró
  qué, y pide la contraseña del propietario de la base para una consulta.
