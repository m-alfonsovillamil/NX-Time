# 25. La firma mensual no bloquea la corrección: la corrección invalida la firma

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

El PDF del registro mensual tenía, desde la Fase 10, dos celdas en blanco:
«Firma del trabajador» y «Firma de la empresa». Se firmaban a mano, o nada. La
Fase B3 permite firmar desde la aplicación: la persona, con el mes ya terminado,
acepta que su registro es correcto.

La pregunta difícil no es cómo firmar, sino qué pasa cuando un mes ya firmado se
corrige. Y se corrige: la corrección aprobada del día 14, que llega el día 3 del
mes siguiente, es rutina.

## Decisión

### La corrección invalida la firma, no al revés

Una firma **no bloquea** nada. Si se corrige un fichaje de un mes firmado, la
firma pasa a `INVALIDADA` **en la misma transacción que la corrección**, se
avisa a la persona con el porqué, y el mes vuelve a pedir firma.

Bloquear la corrección sería ilegal y perverso a la vez. Ilegal, porque el
RD-ley 8/2019 exige que el registro sea veraz, y un error encontrado tiene que
poder corregirse. Perverso, porque si firmar congela el mes nadie firmaría: sería
renunciar a reclamar.

### Colgada del evento de auditoría, no de cada camino

Todo cambio de un fichaje ya publica `TimeEntryAuditEvent`, porque la auditoría
es obligatoria: iniciar, cerrar, corregir, añadir una pausa y el cierre
automático. `SignatureInvalidationListener` escucha **ese** evento, en
`BEFORE_COMMIT`, y no uno por camino. Así, un camino nuevo que cambie fichajes
invalida la firma sin tener que acordarse de hacerlo.

Y no invalida a ciegas: **recalcula la huella del mes y solo tumba la firma si
cambió**. Cambiar el proyecto de una jornada, o anular un fichaje y recrearlo con
las mismas horas, no cambia lo firmado. Tumbar la firma por eso sería ruido y
enseñaría a la gente a no hacer caso del aviso. Las acciones que se anotan sin
cambiar nada (pedir, rechazar o disputar una corrección) ni siquiera se miran.

### Qué se firma: una huella, calculada en un solo sitio

Se guarda el SHA-256 de un **resumen canónico del mes**: cada jornada viva con
su entrada, su salida, su pausa y si la cerró el sistema. Lo calcula
`HuellaDelMes`, con el mismo canonicalizador que la cadena de auditoría
(`Canonico`, extraído de `HuellaDeAuditoria`). Dos sitios donde se decide qué se
firma serían dos verdades, y la que fallaría sería la de comprobar.

- **No entra el id del fichaje**: una corrección anula el original y crea otro, y
  si deja las mismas horas, lo firmado sigue siendo verdad.
- **Las horas van truncadas a segundos**: un fichaje recién escrito lleva
  nanosegundos en memoria y microsegundos al releerlo, y la misma jornada daría
  dos huellas según de dónde se leyera.

### Tabla aparte, no dentro de la auditoría

`firmas_mensuales` y no `auditoria_fichaje`. La auditoría es append-only, así
que una firma no podría pasar a invalidada. Su cadena es global a todas las
empresas, y verificar una firma no puede obligar a recorrerla entera. Además, lo
que se firma es un resumen del mes, no una fila. Las firmas invalidadas **no se
borran**: un índice único **parcial** (`WHERE estado = 'VIGENTE'`) deja una sola
vigente por persona y mes, y conserva el histórico.

### Qué impide firmar

Se firma un mes **terminado**, que no esté ya firmado, con alguna jornada, sin
jornadas abiertas y **sin jornadas que cerró el sistema**. Una jornada cerrada a
las 3:00 por el sistema lleva una salida que nadie fichó: firmarla sería firmar
un dato que el propio sistema marca como inventado. Primero se corrige.

### Es una firma de aceptación, no eIDAS

Lo dicen la pantalla, el correo y el PDF: «firma de aceptación, no firma
electrónica cualificada». Demuestra que la persona aceptó ese registro, con esa
huella, en ese momento, desde su sesión. No es un certificado.

## Consecuencias

**El PDF sin firma sale exactamente igual que antes**, y hay un test que lo
comprueba. Con firma, la celda del trabajador dice quién firmó, cuándo, el
principio de la huella y que no es cualificada. Con visado, la de la empresa
dice quién lo dio.

**El visado de la empresa** (`firma:visar`, RRHH) está en el backend, pero su
pantalla va a la web ([ADR 022](022-alcance-de-la-web.md)). Nadie visa su propia
firma, y una firma invalidada no se visa.

**El recordatorio corre a diario, no el día 1.** Si ese día Render está dormido,
el día 2 se pone al día, y el servicio no manda dos recordatorios el mismo mes a
la misma persona. Además, el vigilante de `/estado/tareas` solo sabe de tareas
que corren al menos cada 48 horas: un cron mensual lo habría puesto en rojo hasta
el primer día 1 después del despliegue.

**Cuesta una consulta por fichaje.** Cada cambio de un fichaje busca firmas
vigentes de esa persona en el mes afectado. El mes en curso nunca está firmado,
así que al fichar la consulta vuelve vacía, y usa el índice parcial.

**Lo que salió al construirlo:**

- Extraer `Canonico` no podía cambiar ni un hash de la auditoría histórica. Antes
  de moverlo se fijaron dos valores calculados con el código de entonces
  (`HuellaDeAuditoriaTest`), y siguen dando lo mismo.
- La primera pantalla enseñaba «— de trabajo efectivo» en la confirmación de un
  mes sin firmar, porque las horas solo venían con la firma. Quien firma tiene
  que ver qué horas acepta, así que ahora el servidor manda el neto de cada mes.
- Dos sabotajes comprueban que los tests muerden. Sin el listener, tres tests se
  ponen rojos; invalidando a ciegas, se pone rojo el de «una corrección que deja
  las mismas horas no toca la firma».
