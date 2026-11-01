# 56. La ubicación al fichar

**Estado:** aceptada · **Fecha:** noviembre 2026 · Junto a los ADR [054](054-fichar-sin-conexion.md) y [055](055-presencial-o-a-distancia.md)

## Contexto

Logroot, el sistema que usa hoy IIDD, permite fichar con geolocalización puntual,
y 74 de sus técnicos trabajan en la calle. Hasta ahora los textos legales de NX
Time prometían por escrito que no se registraba la ubicación. La ubicación de un
trabajador es un dato delicado: el art. 90 de la LOPDGDD obliga a informar antes a
la plantilla y a sus representantes, y un seguimiento continuo sería difícil de
justificar para llevar un registro de jornada.

## Decisión

- **Opcional por empresa.** `empresas.ubicacion_al_fichar` (V52), apagado por
  defecto. Lo enciende un ADMIN en Ajustes de empresa, y al encenderlo se le
  recuerda el art. 90 y la evaluación de impacto. Es **por empresa, no por
  persona**: quien trabaja en la oficina también pasa por ello.
- **Solo al entrar y al salir.** Ni en las pausas ni durante la jornada.
- **Sin ubicación se ficha igual.** Sin permiso, sin GPS o pasados 8 segundos, el
  fichaje queda marcado (DENEGADA o NO_DISPONIBLE). Un cliente que no la mande
  (una app anterior) deja el campo a null: no se sabe, que no es lo mismo que «no
  disponible».
- **En un kiosco no se pide:** el kiosco ya dice dónde se fichó.
- **Redondeada a 4 decimales (unos 11 m)** al guardarla (`PuntoDeFichaje`).
- **Fuera de la cadena de auditoría, del snapshot y de la huella del mes**, para
  poder borrarla sin romper la cadena ni anular firmas. Una corrección la copia a
  la jornada nueva.
- **La ven la persona, su gestor y RRHH/ADMIN**: en los historiales, «± 25 m · Ver
  en el mapa», y la persona también en «Mis datos». No sale en el Excel de horas,
  ni en el PDF, ni en el ZIP de la Inspección.
- **El mapa es un enlace a OpenStreetMap**, no un mapa dentro de la página: las
  coordenadas solo salen de NX Time si alguien pulsa, sin teselas de terceros y
  sin tocar la CSP.
- **Se borra a los 12 meses.** `ConservacionDeDatos`, en la tarea de las 3:45, pone
  a null las coordenadas y deja el estado. `PersonalDataEraser` también las borra.
- **En la web**, la primera vez que se va a tomar, un diálogo dice para qué es
  antes de que pregunte el navegador. Se recuerda **por navegador**, no por persona.
  `render.yaml` pasa a `geolocation=(self)`; la API sigue con `geolocation=()`.

## Consecuencias

- Los textos legales cambian (`Privacidad`, `Encargo`, las plantillas para
  clientes y el registro de actividades), y con ellos la versión de las
  condiciones (`2026-11-01`): los ADMIN las vuelven a aceptar. Recoge también la
  modalidad y el origen del fichaje (ADR 054 y 055).
- Fichar puede tardar hasta 8 segundos más si el GPS no responde. La hora la sigue
  poniendo el servidor al recibir el fichaje, así que queda hasta 8 segundos por
  detrás del momento de pulsar.
- En un ordenador compartido, solo la primera persona ve el diálogo informativo.
  El deber de informar es de la empresa (art. 90), que no se sustituye con este
  diálogo; es un límite conocido.
- La posición la da el dispositivo y se puede falsear. Sirve para orientar, no como
  prueba.
