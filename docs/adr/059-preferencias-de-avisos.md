# 59. Cómo le llegan los avisos a cada persona

**Estado:** aceptada · **Fecha:** noviembre 2026 · Junto a los ADR [028](028-push-generico-colgado-del-aviso.md) (push colgado del aviso) y [031](031-push-en-la-web.md) (push en la web)

## Contexto

Cada aviso de NX Time sale por tres sitios: la campana de la aplicación, el
correo y el push al móvil o al navegador. Hasta ahora solo se podía apagar el
push entero de un dispositivo; el correo llegaba siempre. Con una plantilla de
~120 personas, un gestor recibe un correo por cada ausencia que le piden, cada
corrección y cada resumen nocturno, y la reacción habitual a eso es crear un
filtro que lo manda todo a una carpeta que nadie lee, incluidos los avisos que
sí importan.

## Decisión

**Cada persona elige, por categoría, si le llega por correo y si le llega al
móvil.** La campana no se elige: siempre recibe todo, y es donde está el detalle.

- **Las categorías** agrupan los tipos de aviso por familias: ausencias,
  correcciones, horas extra, incidencias de cuadrante, ofertas internas y
  equipo. Se deciden por el nombre del tipo (`AUSENCIA_*`, `CORRECCION_*`...), así
  que un tipo nuevo de una familia conocida cae en su sitio solo, y uno que no
  encaje va a obligatorias, que llegan siempre. Un test lista los tipos uno a
  uno para que nadie añada uno sin decidir dónde va.
- **Obligatorias, que no se apagan:** lo de la cuenta y su seguridad, la firma
  mensual, el canal de denuncias y el borrado de datos. Son pocas, raras, y
  alguien tiene que leerlas seguro; en algunos casos lo exige la ley (los plazos
  del canal de denuncias, ADR 048).
- **Por defecto, todo llega**, como hasta ahora. Solo se guarda lo que alguien
  cambia (`preferencias_avisos`, V55): una fila por persona y categoría tocada.
- **Se filtra en dos sitios, no en cada evento:** el envío de correo
  (`EmailSender.enviar`, que reconoce la categoría por el aviso al que lleva el
  botón del correo) y el envío del push (`PushSender`), por el que pasan todos los
  avisos. Lo que tiene que salir pase lo que pase (los códigos de acceso, el alta)
  va por `enviarObligatorio` y no pregunta.
- En la web y en la app, una tarjeta en Ajustes con dos interruptores por
  categoría. Las obligatorias se enseñan sin interruptores, con «siempre».

## Consecuencias

- Mirar la preferencia cuesta una consulta pequeña por correo o push enviado (por
  clave primaria, o por el índice de `lower(email)`). Es lo que cuesta enviar.
- Al borrar los datos de una persona se borran también sus preferencias.
- El push de un dispositivo sigue pudiéndose apagar entero, como antes; estas
  preferencias deciden qué llega a los dispositivos que lo tienen encendido.

## Alternativas descartadas

- **Elegir tipo a tipo:** veinticinco interruptores no los configura nadie.
- **Un resumen diario por correo en vez de un correo por aviso:** es mejor
  solución para quien recibe muchos, pero necesita otra tarea nocturna, y la
  cuota de Neon pide no añadir tareas sin necesidad. Queda para cuando haya
  quejas concretas.
