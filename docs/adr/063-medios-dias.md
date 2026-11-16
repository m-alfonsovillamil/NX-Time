# 63. Medios días en las ausencias

**Estado:** aceptada · **Fecha:** noviembre 2026 · Plan N, paso N11

## Contexto

Las ausencias eran de días enteros. Quien necesitaba una tarde para una gestión, o
quería gastar las vacaciones de media jornada en media jornada, tenía que pedir el
día entero o arreglarlo de palabra con su gestor, y entonces el saldo ya no decía
la verdad. En las empresas a las que va dirigido esto, el medio día de vacaciones o
de asuntos propios es de lo más pedido.

El problema no es guardar la marca, sino que una ausencia cambia muchas cuentas:
el saldo, lo que se espera trabajar ese día y esa semana (y por tanto las horas
extra), las incidencias de cuadrante, el panel «Ahora mismo», los informes. Un
medio día tiene que contar lo mismo en todas.

## Decisión

- **Solo de un único día, y sin distinguir mañana de tarde.**
  `peticiones_ausencia.medio_dia` (V60), con un CHECK que exige
  `fecha_inicio = fecha_fin`. Medio día de un rango no significa nada, y saber qué
  mitad es no hace falta para contar: sería un dato más que pedir y mantener.
  Vale para cualquier tipo de ausencia.
- **Cuenta 0,5**, y la cuenta está en un sitio (`AbsenceRequest.diasQueCuenta`):
  en la petición (`diasHabiles`), en el saldo de vacaciones y en el Excel de la
  empresa. Por eso **los días del contrato pasan a llevar decimales**:
  `diasHabiles` y todo `VacationBalanceResponse` son `number`, no `integer`.
  Siempre múltiplos de 0,5, que en coma flotante se suman sin error.
- **Ese día se trabaja.** Un medio día aprobado **no** es un día «no laborable»:
  la app no pregunta antes de fichar, no se avisa al gestor de que alguien ficha
  en un día libre y el recordatorio de fichar sigue sonando. Lo que cambia es
  **cuánto se espera**: la mitad. `NonWorkingDayService` no lo devuelve como
  motivo, y quien necesita saberlo lo pregunta aparte (`mediosDiasEnRango`,
  `mediosDiasDelDia`).
- **La mitad de lo esperado, en todas las cuentas:**
  - el gráfico de inicio espera media jornada y dice por qué («Vacaciones (medio
    día)»);
  - la semana contra la que se miden las horas extra se cuenta en **medios días**
    (`OvertimeCalculator.objetivoSemanalDeMedios`): cinco hábiles con un medio día
    son nueve medios, 36 h de 40;
  - con cuadrante, el día teórico trae la mitad de sus minutos y los tramos
    enteros, porque no se sabe qué mitad se libra.
- **Ese día no genera incidencias de cuadrante.** Medir un turno entero contra
  quien solo trabaja la mitad daría un retraso o una salida anticipada cada vez.
  En «Ahora mismo» tampoco se le espera a una hora ni se le marca retraso, pero se
  dice que tiene medio día.
- **Para el absentismo no existe.** Se mide en días enteros (ADR 026): un día con
  medio día aprobado es un día laborable más, trabajado si se fichó.
- **Dos medios días el mismo día no se pueden pedir**: se solapan, como dos
  ausencias cualesquiera. Quien quiere el día entero pide el día entero.
- **Pendiente no cuenta para lo esperado**, igual que cualquier ausencia: puede
  acabar rechazada. Para el saldo sí, como siempre.

## Consecuencias

- **Una app anterior a la 1.24 falla al leer una lista con un medio día**: espera
  enteros y Gson no convierte `0.5`. Los enteros (`22.0`) sí los lee, así que solo
  se rompe donde alguien haya pedido un medio día. Es la misma situación que el
  estado `CANCELADA` del ADR 058 y se resuelve igual: publicando la 1.24 antes de
  que la funcionalidad se use.
- `medioDia` es opcional al pedir: una app o un cliente que no lo manda pide días
  enteros, como hasta ahora.
- Los informes en PDF y la exportación de datos personales dicen «(medio día)»; el
  Excel de la empresa lleva los días de ausencia con decimales.
- Si algún día hace falta saber qué mitad (para el cuadrante, o para avisar de un
  retraso por la tarde), será una columna más y otro ADR; hoy sobra.
- Las horas sueltas («salgo dos horas antes») siguen fuera: no son ausencias sino
  correcciones de jornada o permisos por horas, que es otra funcionalidad.
