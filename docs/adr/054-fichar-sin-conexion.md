# 54. Fichar sin conexión

**Estado:** aceptada · **Fecha:** octubre 2026 · Cambia lo que decían el [ADR 022](022-alcance-de-la-web.md) y el [ADR 033](033-fichaje-en-kiosco.md) solo para la app

## Contexto

La primera empresa real (IIDD, plan K) tiene 74 técnicos que trabajan en
cámaras frigoríficas, sótanos de supermercados y obras, y muchas veces sin
cobertura. Hoy usan Logroot, que permite fichar en movilidad. En NX Time, sin
red, el botón de fichar falla y el fichaje se pierde: no queda nada del momento
en que entraron.

Hasta ahora, en NX Time la hora la ponía siempre el servidor (`Instant.now()`),
y eso tenía su porqué: la hora del móvil se cambia en un ajuste. Fichar sin
conexión obliga a aceptar una hora que llega del móvil, horas después.

## Decisión

**Solo en la app Android.** La web y el kiosco siguen sin modo sin conexión: una
web que enseña datos viejos sin avisar es peor que una que dice «sin conexión»
(ADR 022), y el kiosco está fijo en un sitio con red (ADR 033).

**La app guarda el fichaje en una cola y lo manda al volver la red**, a
`POST /api/v1/fichaje/sin-conexion`: hasta 50 por envío, en el orden en que se
hicieron. Cada uno lleva un `idCliente` (UUID) que genera el móvil.

**La hora.** El móvil manda dos cosas:
- `msDesdeElFichaje`: cuánto ha pasado desde que se fichó, medido con
  `elapsedRealtime`, el reloj que cuenta desde que arrancó el móvil y que no se
  cambia tocando la hora. Con eso, la hora es **la de llegada al servidor menos
  ese tiempo**, y no depende de que el reloj del móvil esté bien.
- `horaDelDispositivo`: la hora del reloj del móvil. Solo se usa si el móvil se
  reinició entre el fichaje y el envío, porque entonces la cuenta anterior se
  pierde. La auditoría lo dice («hora del reloj del móvil, que se reinició»).

**Lo que se acepta:**
- **Hasta 72 h** (decisión de Miguel): se aplica con su hora, sin más.
- **Pasadas 72 h**: se aplica con su hora, pero la jornada queda marcada
  (`registros.revisar_por_llegar_tarde`). Funciona como `jornada_incompleta`:
  cuenta como incidencia abierta y no deja firmar el mes. La quita quien aprueba
  correcciones, con «Dar por bueno» (`POST /fichaje/{id}/dar-por-bueno`, acción
  de auditoría nueva `DADO_POR_BUENO`, que no invalida firmas porque no cambia
  horas), o con una corrección, que no hereda la marca.
- **No se acepta:** lo del futuro (con 2 minutos de margen entre relojes), lo de
  hace más de un mes, ni lo que cae antes de lo que ya consta (una pausa
  anterior a la entrada, una entrada que pisa la jornada anterior). Se rechaza
  con el motivo, y la app lo enseña.

**Las mismas reglas que con conexión, con la hora del fichaje.** `registrar()`
recibe el momento en vez de usar el reloj. La regla de las 16 h (jornada
olvidada), el proyecto del día y el aviso de día no laborable se calculan con
esa hora, no con la de llegada.

**Si la jornada la cerró el sistema mientras tanto.** El caso típico: entrada
con red a las 8:00, pausa y salida sin red, y el envío llega al día siguiente,
después de que el proceso de las 3:00 haya cerrado la jornada en su tope de 16
h. Ese cierre era un tope a falta de dato, así que si el fichaje sin conexión cae
dentro de la jornada cerrada por el sistema, **la jornada se reabre** (el último
tramo de proyecto vuelve a quedar en curso) y se aplica encima. Queda en la
auditoría como acción del sistema, igual que el cierre.

**Idempotencia.** Cada resultado, también el rechazo, se guarda en
`fichajes_recibidos` (único por persona e `idCliente`). Si la respuesta se
pierde y la app reenvía, se le contesta lo mismo con `yaRecibido`. Se borra a
los 30 días. Cada fichaje va en su propia transacción: un rechazo no deshace
los anteriores.

**Desde dónde se fichó.** `registros.origen`: WEB, APP, KIOSCO o
APP_SIN_CONEXION. La web y la app lo dicen con la cabecera `X-Cliente`
(«web», «android»). Lo anterior a V50 queda a null, salvo lo del kiosco. Entra
en la instantánea de auditoría y lo hereda una corrección.

**La auditoría** dice las dos horas en el motivo («Fichado sin conexión el
26/10/2026 08:01 (hora medida en el móvil), recibido el 26/10/2026 10:15»), que
entra en la huella de la cadena. La cadena encadena por orden de inserción y el
verificador no exige que las horas crezcan, así que un fichaje que llega tarde
con hora pasada se encadena bien. La firma mensual no bloquea: un fichaje tardío
sobre un mes firmado la revisa sola, como cualquier cambio.

## Consecuencias

- **La hora la pone el móvil.** Quien manipule la app podría mandar la hora que
  quiera. No hay forma de evitarlo sin red: es lo mismo que acepta Logroot. Lo
  que sí hay: cada jornada dice que se fichó sin conexión, la auditoría guarda
  cuándo llegó, y lo que llega tarde lo revisa una persona. Un gestor que vea a
  alguien que «nunca tiene cobertura» tiene con qué preguntar.
- Las horas extra y las incidencias de cuadrante revisan 14 días hacia atrás:
  lo que llega en 72 h se tiene en cuenta solo.
- `TimeEntryServiceImpl` ya no usa `Instant.now()`: tiene un `Clock`, y los
  tests pueden fijar la hora.
- La API de integración con Dynamics que se planteó el 24/10 queda descartada
  (26/10): en IIDD las horas de las OT van por Field Service y la nómina no
  está enlazada; no hay a dónde mandarlas.
