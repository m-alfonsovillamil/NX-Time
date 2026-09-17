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

- **Los motivos de `auditoria_fichaje` no se anonimizan.** La tabla es append-only
  y así se queda. Sin nombre ni correo en `usuarios`, esos textos dejan de apuntar
  a alguien identificable, pero si alguien escribió un nombre dentro de un motivo,
  ahí sigue.
- **Los correos que ya salieron no se pueden recuperar.** Por eso el aviso de "nueva
  solicitud" no lleva el nombre (se guarda en `avisos` y sobreviviría a la
  anonimización) y el correo sí; ninguno de los dos lleva el motivo.
- **El access token deja de valer en el acto.** Al probarlo apareció que un JWT
  emitido antes de ejecutar seguía sirviendo 15 minutos, y en ese rato se podía
  volver a subir la foto recién borrada. El filtro JWT ya cargaba el usuario de la
  base en cada petición; ahora además comprueba que sigue activo. Arregla lo mismo
  para las bajas, que tenían el mismo hueco.
- **Solo se puede pedir con la cuenta activa.** Quien ya está de baja no puede
  entrar a pedirlo; hoy tendría que hacerlo por otro canal y RRHH no tiene cómo
  registrarlo en la aplicación.
- **La anonimización hace que la persona desaparezca de los informes con su
  nombre**, que es justo lo que se busca, pero un informe regenerado de hace cinco
  años ya no dirá quién fichó.
- `V20` va detrás de `V19`. Amplía dos `CHECK` que había que reescribir enteros: el
  de los tipos de aviso y el de las tareas nocturnas. Sin el segundo, la tarea nueva
  habría fallado cada noche al registrar su propia ejecución.
