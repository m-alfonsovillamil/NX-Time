# 32. La zona horaria es de cada empresa, y los días se cuentan al leer

**Estado:** aceptada · **Fecha:** septiembre 2026 · Completa el
[ADR 002](002-instant-vs-localdatetime.md), que guardó los fichajes como
instantes pero dio por hecho que todos los días eran de Madrid

## Contexto

Desde el ADR 002 los fichajes son instantes (`TIMESTAMPTZ`) y lo que depende de
la zona es **a qué día pertenece cada instante**: el día de una jornada, las
horas extra diarias, el mes de un informe o de una firma, los días de la
analítica, el «hoy» de la pantalla de fichar. Esa zona era `Europe/Madrid`,
escrita a mano en unas treinta constantes, en seis consultas SQL
(`AT TIME ZONE 'Europe/Madrid'`) y en los clientes.

No era un error, pero sí una deuda: una empresa de Canarias habría visto sus
jornadas de después de las 23:00 en el día siguiente, los umbrales diarios de
horas extra partidos por la medianoche equivocada y los informes mensuales con
la primera hora del mes en el mes anterior. Y es el primer requisito para
vender fuera de la península.

## Decisión

### La zona es una columna de la empresa

`empresas.zona_horaria` (V35), un nombre IANA (`Europe/Madrid`,
`Atlantic/Canary`…), **Madrid por defecto**: las empresas que ya existían quedan
exactamente igual. La valida `ZoneId` al guardarla; la base no repite la lista
de zonas, que cambia con cada versión de tzdata.

Todo cálculo de «qué día es» pasa por la empresa: `Company.zona()`,
`User.zona()` y `TimeEntry.dia()` (el día de su entrada, en la zona de su
empresa). No hay ninguna constante de zona en el código de negocio.

### Los días se calculan al leer, no se guardan

Ningún fichaje guarda «su día». Se calcula cada vez con la zona **actual** de la
empresa, igual que antes se calculaba con Madrid.

La consecuencia es deliberada: **cambiar la zona reinterpreta el histórico**.
Un fichaje de las 23:30 UTC pasa a ser del día siguiente o del mismo día según
la zona que la empresa tenga hoy. Guardar el día de cada fichaje evitaría eso,
pero a cambio habría dos fuentes de verdad (el instante y el día) que se pueden
contradecir, y una corrección tendría que mantenerlas a la par. Cambiar de zona
es algo que una empresa hace una vez, al darse de alta, así que se permite con
un aviso, y la firma mensual se revisa como tras cualquier otro cambio: si la
huella de un mes firmado cambia, la firma se invalida (ADR 025).

La cambia solo ADMIN (`empresa:configurar`), desde **Ajustes de la empresa** en
la web (`PUT /api/v1/empresa/ajustes`). La web pide confirmación explicando lo
de arriba; el servidor guarda la zona, revisa todas las firmas vigentes de la
empresa con la zona nueva ya puesta (`revisarTrasCambioDeZona`) y responde
cuántas cayeron. Se aceptan solo nombres de región IANA: un desfase fijo
(`+01:00`) no sabe del horario de verano y dejaría medio año desplazado una
hora.

### Las consultas que cruzan empresas van zona por zona

- El SQL nativo deja de nombrar la zona: se une con `empresas` y usa
  `AT TIME ZONE e.zona_horaria`.
- El detector de horas extra, que mira todas las empresas a la vez, pide un día
  de más por cada lado y descarta los días que se salen de lo pedido: los días
  ya salen contados en la zona de cada empresa.
- El recordatorio de firma y la detección de incidencias de cuadrante van
  **zona por zona** (`CompanyRepository.findZonasEnUso`), porque «el mes
  pasado» o «los fichajes del martes» no empiezan en el mismo instante en todas.
  Casi siempre es una sola vuelta.

### Las tareas nocturnas siguen con el reloj de Madrid

El `cron` de las seis tareas sigue en `Europe/Madrid` (`ScheduledTask.ZONA`): es
la zona del **reloj** de las tareas, a qué hora arrancan y cuándo las da por
perdidas el vigilante de `/estado/tareas`, no la de los datos. Cada tarea calcula
los días por empresa.

Límite conocido: arrancando a las 3:00 de Madrid, el día anterior ya está
cerrado en cualquier zona entre UTC-3 y UTC+3. Más allá (América, Asia) la
tarea iría un día tarde. No se resuelve ahora: hacerlo exige tareas por zona, y
eso son más arranques de la base (la cuota de Neon, septiembre de 2026).

### Los clientes reciben la zona en la sesión

`AuthenticationResponse.zonaHoraria` viaja en el login y en cada refresco, como
las authorities: la web y la app dejan de suponer Madrid y un cambio de zona
llega sin volver a entrar. La exportación de datos personales la lleva también
(`persona.zonaHoraria`): los instantes del JSON van en UTC, y el PDF los pinta en
esa zona.

## Consecuencias

- Una empresa de Canarias ve sus días, horas extra, informes, firmas, analítica
  e incidencias en su hora. `ZonaHorariaIT` lo comprueba con dos empresas que
  fichan en el mismo instante y lo ven caer en días distintos.
- Las claves de caché del panel usan la fecha UTC, y no la de la JVM: solo
  sirven para que una entrada de un minuto no cruce de un día a otro.
- Los tests que dan por hecho Madrid siguen valiendo sin tocarlos: su empresa se
  crea con la zona por defecto.
- La web cuenta los días y pinta las horas en la zona de la sesión
  (`util/fechas.ts`, `fijarZona`); al cambiarla en los ajustes se aplica en el
  acto y se vuelve a pedir todo lo que había en caché.
- Queda pendiente que la app Android la lea de la sesión (Z3).
