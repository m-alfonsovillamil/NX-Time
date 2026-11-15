# 62. El justificante en las ausencias

**Estado:** aceptada · **Fecha:** noviembre 2026 · Plan N, paso N13

## Contexto

Quien falta por una visita médica o una baja suele tener que entregar un
justificante, y hoy lo manda por correo o en papel, fuera del registro. Pero un
justificante médico es un **dato de salud** (art. 9 del RGPD): categoría especial,
que solo se puede tratar con una base clara (la ley o el convenio) y con más
cuidado que el resto. La empresa decide si lo pide; NX Time no puede presumirlo.

## Decisión

- **Apagado por defecto, por empresa.** `empresas.justificantes_en_ausencias`
  (V59). Lo enciende un ADMIN en Ajustes de empresa, y al encenderlo se le recuerda
  que es un dato de salud y que lo revise antes con quien le asesore.
- **Uno por ausencia, solo de la propia persona.** Lo sube, lo cambia o lo quita
  ella (la web y la app suben; quitarlo, en la web). PDF, JPEG o PNG comprobados
  por los primeros bytes, hasta 5 MB.
- **Lo ven la persona y RRHH/ADMIN**, con la authority nueva
  `ausencia:justificante:leer`. **El gestor no**: aprueba la ausencia sin verlo, y
  la lista que pide ni siquiera le dice que existe. Cada vez que alguien abre el de
  otra persona queda anotado en `accesos_a_datos` (`JUSTIFICANTE`, ADR 047).
- **Tabla propia** (`justificantes_ausencia`, con la ausencia como clave), no
  `adjuntos`: aquellos son del perfil (un CV y una foto vigentes por persona). Sin
  entidad JPA: los bytes van en la misma fila y solo los lee la consulta de
  descargar (el motivo de las dos tablas de adjuntos, ADR 007).
- **Endpoints aparte** (`/ausencias/justificantes`, `/ausencias/{id}/justificante`,
  `/empresa/justificantes`) y no campos nuevos en la ausencia: así el gestor no
  recibe nada, y se piden solo si la empresa los usa.
- **Apagarlo no borra** los que haya: se siguen viendo, pero no se suben más.
- **Se borran** a los cuatro años de acabar la ausencia (la tarea de conservación,
  lo mismo que el registro que explican), al borrar los datos de la persona y con
  la baja de la empresa (en cascada con la ausencia). La descarga va con
  `Cache-Control: no-store`.

## Consecuencias

- Antes de encenderlo en una empresa real hay que revisarlo con un abogado: la
  base legal, el aviso a la plantilla y el registro de actividades. Los textos
  legales de NX Time no lo mencionan todavía; se actualizarán cuando la primera
  empresa lo encienda.
- En la app, la persona adjunta y ve que lo tiene; abrirlo o quitarlo, en la web.
  RRHH lo ve en la web, en las ausencias del equipo.
- Si un día el gestor necesita saber que hay justificante (sin verlo), será otro
  ADR: hoy se prefiere que no sepa nada.
