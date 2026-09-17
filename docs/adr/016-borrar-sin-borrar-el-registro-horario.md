# 16. Borrar los datos de alguien sin borrar su registro horario

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

El RGPD da a cualquier persona el derecho a pedir que se borren sus datos
personales (art. 17). Hasta aquí, lo único que existía era **dar de baja**:
desactivar la cuenta y dejarlo todo como estaba.

Pero NX Time guarda un registro de jornada, y **la empresa tiene que conservarlo
cuatro años** (art. 34.9 del Estatuto de los Trabajadores, tras el RD-ley 8/2019).
El propio art. 17.3.b del RGPD excluye del derecho de supresión lo que haya que
conservar por obligación legal. Así que "bórrame" **no puede borrar fichajes**, y
a una inspección un fichaje sin nombre tampoco le sirve de nada.

Había además tres restricciones que ya estaban en el proyecto:

1. `auditoria_fichaje` es append-only por trigger (ADR 003). No se relaja.
2. Un CV presentado a una oferta queda congelado y la base impide borrarlo
   (ADR 013).
3. Varios textos libres tienen `NOT NULL` o un `CHECK` que exige contenido
   (el motivo de una corrección, el de una pausa añadida, el autor de un mensaje
   del instructor).

## Decisión

### En dos tiempos

**Al ejecutar la solicitud** se borra ya lo que la ley **no** obliga a conservar,
y se desactiva la cuenta:

| Se borra | Se conserva |
|---|---|
| CV y foto (y sus bytes) | Nombre, apellidos, correo |
| Candidaturas a ofertas internas | Fichajes, pausas y pausas añadidas |
| Fecha de nacimiento | Ausencias y vacaciones |
| Avisos recibidos | Correcciones y horas extra |
| Sesiones (refresh tokens) y códigos de acceso | Asignaciones a proyectos, auditoría |

La contraseña se sustituye por el hash de un UUID que no se guarda.

**Cuatro años después del último fichaje** (en hora de España), una tarea nocturna
(`ANONIMIZACION`, 3:45) anonimiza lo que quedaba: nombre `Persona eliminada`,
correo `eliminado-{id}@anonimo.invalid`, apellidos, puesto y departamento a null,
y los textos libres que escribió o que se escribieron sobre ella —motivos y
comentarios de ausencias, correcciones, pausas añadidas y horas extra— pasan a
`[eliminado]`. En el canal de denuncias se desvincula como denunciante y como
autora de sus mensajes.

Busca "vencidas en o antes de hoy", no "vencidas hoy": una noche perdida la recoge
la siguiente.

### La persona lo pide; RRHH o ADMIN lo ejecuta

La empresa es la responsable del tratamiento, y ejecutar deja cosas a medias si no
se mira antes. Por eso lo ejecuta alguien con `empleado:gestionar` (los mismos que
dan de baja), y **el servidor bloquea** la ejecución, con la razón en frases, si:

- es la solicitud **propia** de quien ejecuta;
- la persona tiene una **jornada abierta** (nadie la cerraría);
- tiene **correcciones** vivas, pedidas por ella o sobre sus fichajes, o
  **ausencias** pendientes (esperarían a alguien que ya no puede entrar);
- tiene una **denuncia** identificada abierta;
- es el **único ADMIN activo** (la empresa se quedaría sin quien gestione roles).

Rechazar exige comentario, que se le manda a la persona. La bandeja no la ve un
GESTOR: saber que alguien ha pedido borrar sus datos no le corresponde.

### Quien ya no puede entrar también puede pedirlo (V21)

El derecho no se pierde por estar de baja, pero con la cuenta desactivada no hay
Ajustes desde donde pedirlo. Lo normal es que llegue por correo o por carta, así
que RRHH o ADMIN puede **registrar** la solicitud en nombre de la persona:

- Elige a alguien de la empresa, de alta o de baja, que no tenga ya una pendiente
  ni un borrado ejecutado. A uno mismo no: para eso está Ajustes, y un `CHECK`
  impide que una solicitud propia se haga pasar por recibida de fuera.
- **Cómo llegó** es obligatorio ("correo del 12/09 a rrhh@…"). Es la constancia
  de que la persona lo pidió, porque no lo pidió desde su cuenta. Queda también
  quién la registró (`registrada_por_id`).
- A la persona le llega un **acuse de recibo** por correo, con un "si no lo has
  pedido tú, avisa". Los demás que pueden ejecutar reciben el aviso habitual.
- Después sigue el camino de siempre: los mismos bloqueos y la misma ejecución.
  Quien la registró puede ejecutarla; exigir a otra persona dejaría sin salida a
  una empresa con un solo responsable de RRHH.

### SQL explícito, en un solo sitio

Lo que borra y anonimiza está en `PersonalDataEraser`, sentencia a sentencia, y no
repartido por las entidades. Es lo único irreversible del proyecto y tiene que
poder leerse entero de una vez. Corre en la transacción del servicio: si algo
falla, no queda nada a medias.

### Por qué el borrado pasa por encima del ADR 013

El ADR 013 congela el CV presentado para que el expediente de una vacante no
cambie a posteriori. Eso protege a la persona frente a la empresa; no es una
obligación legal de conservarlo. Cuando la persona pide que se borre, **manda
ella**: se borran primero las candidaturas y después los adjuntos, y el `RESTRICT`
de la base sigue impidiendo cualquier otro borrado del CV.

### La solicitud no se borra nunca

`solicitudes_borrado` no tiene `GRANT DELETE`: es la prueba de que el derecho se
ejerció y se atendió. Un `CHECK` exige a cada estado lo suyo (una rechazada sin
comentario, o una ejecutada sin fecha de anonimización, no se pueden guardar).
Solo el motivo, que escribió la persona, se borra al anonimizar.

## Consecuencias

- **Los motivos de `auditoria_fichaje` no se anonimizan, y es una decisión, no un
  olvido.** Cada línea guarda el hash SHA-256 de sus campos —el motivo incluido—
  encadenado con el de la anterior (ADR 003). Cambiar un motivo, aunque fuera a
  `[eliminado]`, rompería la verificación de toda la traza posterior de la
  empresa: justo la prueba de que el registro horario no se ha manipulado, que es
  lo que el art. 17.3.b permite conservar. Sin nombre ni correo en `usuarios`,
  esos textos dejan de apuntar a alguien identificable; lo que queda es el caso de
  alguien que escribiera un nombre dentro de un motivo.

  Se descartaron dos alternativas: relajar el trigger y recalcular la cadena (la
  traza dejaría de ser append-only justo cuando más importa), y cifrar cada motivo
  con una clave por persona que se destruye al anonimizar (*crypto-shredding*):
  es la solución correcta a gran escala, pero añade una clave maestra que, si se
  pierde, deja ilegible toda la auditoría, y no arregla las líneas ya escritas.
- **Los correos que ya salieron no se pueden recuperar.** Por eso el aviso de "nueva
  solicitud" no lleva el nombre (se guarda en `avisos` y sobreviviría a la
  anonimización) y el correo sí; ninguno de los dos lleva el motivo.
- **El access token deja de valer en el acto.** Al probarlo apareció que un JWT
  emitido antes de ejecutar seguía sirviendo 15 minutos, y en ese rato se podía
  volver a subir la foto recién borrada. El filtro JWT ya cargaba el usuario de la
  base en cada petición; ahora además comprueba que sigue activo. Arregla lo mismo
  para las bajas, que tenían el mismo hueco.
- **Una solicitud registrada se apoya en la palabra de quien la registra.** La app
  no puede comprobar que el correo o la carta existen; por eso queda quién la
  registró y cómo dice que llegó, y la persona recibe el acuse de recibo para
  poder protestar si no la pidió.
- **La anonimización hace que la persona desaparezca de los informes con su
  nombre**, que es justo lo que se busca, pero un informe regenerado de hace cinco
  años ya no dirá quién fichó.
- `V20` va detrás de `V19`. Amplía dos `CHECK` que había que reescribir enteros: el
  de los tipos de aviso y el de las tareas nocturnas. Sin el segundo, la tarea nueva
  habría fallado cada noche al registrar su propia ejecución.
- `V21` añade `registrada_por_id` y su `CHECK`. El motivo obligatorio de una
  solicitud registrada lo exige el servicio y no la base, porque la anonimización
  lo pone a NULL.
