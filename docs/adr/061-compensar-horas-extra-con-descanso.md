# 61. Compensar las horas extra con descanso

**Estado:** aceptada · **Fecha:** noviembre 2026 · Plan N, paso N14

## Contexto

Hasta ahora, aceptar un aviso de horas extra significaba una sola cosa: esas horas
contaban para el tope de 80 al año. Pero el art. 35.2 del Estatuto de los
Trabajadores dice que **no se computan** las horas extra compensadas con descanso
dentro de los cuatro meses siguientes a su realización. Muchas empresas, y muchos
convenios, prefieren el descanso al pago, y sin poder decirlo NX Time daba la bolsa
por más gastada de lo que estaba y avisaba del límite antes de tiempo.

## Decisión

- **Al aceptar se elige**: «Se pagan» o «Se compensan con descanso».
  `avisos_horas_extra.compensacion` (V58) es `PAGO` o `DESCANSO`. Sin decir nada,
  `PAGO`, que es lo que significaba aceptar hasta ahora. Las aceptadas antes de V58
  se quedan a null y cuentan como pagadas; no se adivina nada sobre ellas.
- **Cuatro meses desde el día de las horas** (`fecha` del aviso; en una semanal, el
  lunes, que es lo más estricto). La respuesta trae `descansoAntesDe` ya calculado.
- **Quien revisa apunta el día del descanso** (`POST /horas-extra/{id}/descanso`,
  con la misma authority y las mismas reglas que revisar: de la empresa y nunca las
  propias). El día va entre el de las horas y el límite, y no en el futuro.
- **La bolsa, calculada al leer**: las de descanso no cuentan mientras estén dentro
  de sus cuatro meses o si ya se descansaron. Pasados los cuatro meses sin apuntar
  el descanso, **sí cuentan**: se tratan como pagadas, que es lo que dice la ley.
  Nada se guarda ni se recalcula de noche.
- **El aviso de «bolsa al límite»** solo puede saltar al aceptar unas que se pagan:
  las de descanso no gastan bolsa en ese momento.
- **v1 sin enlazar con ausencias.** El descanso no crea una ausencia ni se descuenta
  de un saldo; se apunta el día y ya. Si la empresa quiere que conste en el
  calendario, lo pide como ausencia aparte.
- La web deja elegir y apuntar el día; la app deja elegir y enseña cómo se
  compensan, pero el día del descanso se apunta en la web.

## Consecuencias

- La bolsa de un año puede **subir sola** cuando caducan unas de descanso sin
  descansar. Es correcto, pero el aviso de «bolsa al límite» no salta en ese
  momento (solo al aceptar): se ve en la barra.
- Una app anterior a esta versión acepta sin decir nada, y sus aceptaciones son
  `PAGO`. Ve los campos nuevos como desconocidos y no los enseña.
- El CHECK `ck_avisos_horas_extra_compensacion` impide compensación en avisos sin
  aceptar y un día de descanso en los que se pagan.
- Si más adelante el descanso tiene que salir en el cuadrante o en el saldo, habrá
  que enlazarlo con `peticiones_ausencia` y será otro ADR.
