# 60. Las vacaciones que sobran pasan al año siguiente

**Estado:** aceptada · **Fecha:** noviembre 2026 · Sobre el saldo de vacaciones de la Fase 9 (consumido calculado al leer)

## Contexto

El saldo de vacaciones era de un año natural: lo que no se gastaba el 31 de
diciembre desaparecía. Muchos convenios permiten disfrutarlo en los primeros
meses del año siguiente, y sin eso RRHH tenía que subir a mano el derecho del
año nuevo, persona a persona, y acordarse de bajarlo después.

## Decisión

**Un ajuste de empresa, apagado por defecto: pasar lo que sobra hasta una fecha
tope** (31 de marzo si no se dice otra; V57). Y, como lo consumido, **se calcula
al leer el saldo**, no se guarda.

- **Lo que sobra:** el derecho del año anterior menos lo aprobado en él. Sin
  encadenar: lo que se arrastró a aquel año no vuelve a pasar a este.
- **Hasta el tope cuenta entero** en el saldo, con su fecha de caducidad.
- **Pasado el tope, solo queda lo que se usó** en vacaciones (aprobadas o
  pedidas) que **empezaban** antes del tope; el resto caduca.
- **Al pedir y al aprobar se mira con la fecha de inicio de la ausencia**, no con
  la de hoy: unas vacaciones de junio no pueden gastar días que caducan en marzo.
  Así el saldo no se queda en negativo el día que caduca lo arrastrado.
- La web lo configura en los ajustes de la empresa y lo enseña en el saldo («incluye
  N días del año anterior, que caducan el...»); la app, en el detalle del saldo.

## Consecuencias

- Cambiar el derecho o una aprobación del año anterior cambia el arrastre al
  momento, sin nada que recalcular: es la razón para no guardarlo.
- Unas vacaciones que empiezan antes del tope y acaban después pueden gastar lo
  arrastrado entero. Se acepta: es lo que dice «empezar antes de la fecha», y es
  lo que se entiende al leerlo.
- Una persona con su derecho cambiado a mitad de año arrastra según el derecho
  que quedó guardado para ese año.

## Alternativas descartadas

- **Guardar lo arrastrado en una fila nueva al cerrar el año:** otra tarea de
  madrugada (la cuota de Neon pide no añadirlas) y un contador más que mantener
  cuando se aprueba o cancela algo del año anterior.
- **Arrastrar sin tope:** pocos convenios lo permiten, y el saldo crecería sin fin
  para quien no coge vacaciones.
