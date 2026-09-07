# 10. Ninguna corrección de fichaje se aplica sola

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Hasta aquí, `PATCH /api/v1/fichaje/{id}` corregía **en el acto**: quien tuviera
`fichaje:corregir` (RRHH y ADMIN) cambiaba las horas de cualquier fichaje, el
original quedaba anulado y se creaba el corregido. Su dueño se enteraba, como
mucho, mirando la auditoría — si se le ocurría mirarla.

Y el empleado no tenía la operación contraria: **no podía pedir que le
corrigieran un fichaje suyo**. Solo esperar a que alguien lo hiciera.

Eso vacía de contenido lo que la auditoría inmutable (ADR 003) pretende
garantizar. De poco sirve que la traza no se pueda alterar si el dato que traza
se puede cambiar sin que el interesado lo sepa ni pueda oponerse: la traza
demuestra fielmente que alguien cambió el registro, que es justo lo que no se
discutía.

## Decisión

Una corrección es una **solicitud** que alguien tiene que aprobar. El registro
no se toca mientras está pendiente.

**Quién aprueba depende de quién pide**, y esa es la regla que ordena la fase:

| Quién la pide | Quién la resuelve |
|---|---|
| El dueño, sobre su fichaje | Alguien con `correccion:aprobar` (GESTOR+) |
| Otra persona, sobre el fichaje del dueño | **El dueño**, a quien le cambian sus horas |
| Cualquiera, tras una disputa | `correccion:disputa:resolver` (RRHH+) |
| El dueño teniendo `correccion:aprobar` | Se auto-aprueba, y la traza lo dice |

La regla vive **en un solo sitio** (`CorrectionServiceImpl.puedeResolver`) y
viaja al cliente ya resuelta, en los campos `puedoResolver` y `puedoDisputar` de
cada solicitud. No son propiedades de la solicitud sino de la relación entre
ella y quien la mira: la misma fila le sale resoluble a una persona y no a otra.
Si la app la reimplementara, habría dos copias de lo más delicado de la fase.

**Disputar no es rechazar.** Si al dueño le proponen una corrección que no
acepta, no la cierra: la escala a `EN_DISPUTA`, y decide RRHH. Dejar que la
rechace sería que una de las dos partes del desacuerdo se dé la razón a sí
misma; dejar que el gestor insista, lo mismo al revés.

**La auto-aprobación no es un atajo.** Sin ella, un ADMIN —que no tiene a nadie
por encima— no podría corregir jamás su propio fichaje: la solicitud esperaría
para siempre a alguien que no existe. Lo que la hace aceptable es que queda
escrita como tal en la traza, con su motivo, igual que cualquier otra.

## El endpoint viejo desaparece, no cambia de significado

`PATCH /api/v1/fichaje/{id}` se sustituye por
`POST /api/v1/fichaje/{id}/correcciones`. Se valoró conservar la ruta cambiando
lo que hace por debajo, y se descartó: un cliente antiguo seguiría llamándola y
creería que ha corregido cuando solo ha pedido. Con la ruta retirada se lleva un
404 y se entera. **Un cambio de contrato silencioso es peor que uno ruidoso.**

La respuesta distingue los dos desenlaces: **202** si queda pendiente (el
fichaje sigue igual) y **200** si se ha aplicado. Y el cuerpo lleva `estado`,
para que el cliente no dependa del código HTTP.

## La traza recoge lo que NO salió adelante

`AuditAction` gana `SOLICITUD_CORRECCION`, `RECHAZO_CORRECCION` y `DISPUTA`, y
se anotan en la traza **del fichaje**, aunque no lo cambien. Ahí está su valor:
sin ellas, un intento de corrección rechazado no dejaría rastro y la traza solo
contaría los cambios que prosperaron. Quién quiso cambiar qué, y quién dijo que
no, es justo lo que hay que poder enseñar en una inspección.

Detalle que hubo que arreglar y no era evidente: `auditoria_fichaje.accion`
tenía un `CHECK` con los cuatro valores antiguos y era `VARCHAR(20)`. Sin
ampliar los dos, el primer INSERT con un valor nuevo habría reventado dentro del
listener de auditoría. `SOLICITUD_CORRECCION` mide exactamente 20 caracteres:
cabía por los pelos, así que se sube a 30 para no repetir el problema.

## Consecuencias

**A favor**

- Nadie cambia las horas de otra persona sin que esa persona lo sepa y pueda
  oponerse. Es lo que la auditoría inmutable daba por hecho y no era cierto.
- El empleado gana una operación que no tenía: pedir que le corrijan su fichaje.
- El desacuerdo tiene un camino previsto (la disputa) en vez de resolverse por
  quien tenga más permisos.

**En contra**

- **Una corrección urgente ya no es inmediata**: hay que esperar a que alguien
  la apruebe. Es el precio de la decisión, y es deliberado; para el caso en que
  quien corrige es el propio interesado con permiso, la auto-aprobación lo evita.
- Un fichaje solo admite **una solicitud viva a la vez**
  (`uq_correcciones_una_viva_por_registro`). Es lo que impide dos correcciones
  contradictorias esperando a la vez, pero obliga a resolver la primera antes de
  proponer otra.
- Entre pedir y aprobar puede pasar tiempo: si el fichaje se corrigió por otra
  vía mientras tanto, la aprobación falla con 409 en vez de crear una segunda
  versión "buena" del mismo día.
- Es un **cambio de contrato**: Android va en el mismo commit y los tests que
  daban por hecho `corregir → 200 aplicado` cambian de significado.
