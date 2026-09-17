# 17. Las horas de un proyecto se imputan por jornada, no se deducen por día

**Estado:** aceptada · **Fecha:** septiembre 2026 · **Sustituye en parte a:** [ADR 009](009-asignaciones-con-vigencia.md)

## Contexto

El ADR 009 resolvía "¿cuántas horas se han imputado a NX-CORE en julio?" sin
guardar imputaciones: cada jornada se unía con la **única** asignación que esa
persona tenía vigente ese día, y la base garantizaba que no hubiera dos
(`EXCLUDE` sobre persona y rango de fechas).

Ahora una persona puede trabajar en **varios proyectos a la vez**:

- elige proyecto al iniciar la jornada;
- lo cambia durante el día;
- reparte las horas después si se le olvidó cambiarlo.

Con dos asignaciones vigentes el mismo día, "la asignación de ese día" deja de
existir y la unión por fecha no tiene respuesta.

## Decisión

### Las horas se guardan

Dos tablas nuevas (V23):

- **`tramos_proyecto`**: lo que pasó. Un tramo al iniciar con proyecto y otro
  cada vez que se cambia, con su inicio, su fin y las pausas **fichadas** durante
  él. No se puede cambiar de proyecto en pausa, así que cada pausa cae entera en
  un tramo.
- **`imputaciones_proyecto`**: lo que cuenta. Segundos de cada jornada para cada
  proyecto, con su origen:
  - `TRAMOS` si salió de los tramos;
  - `MANUAL` si alguien lo repartió a mano o una corrección cambió las horas.

Los informes de horas por proyecto **suman imputaciones**. La asignación con
vigencia sigue existiendo y sigue mandando en qué proyectos puede fichar alguien,
pero ya no decide adónde van las horas.

La restricción de la base cambia: ya no impide estar en dos proyectos el mismo
día, **sí** impide estar dos veces en el mismo proyecto a la vez
(`ex_asignaciones_sin_solape_mismo_proyecto`).

### Un invariante, en un solo sitio

En una jornada cerrada con imputaciones, **la suma es el neto** (duración menos
pausas). Solo `ProjectAllocationService` escribe imputaciones, y hay que avisarle
en los tres momentos en que el neto cambia:

| Momento | Qué hace |
|---|---|
| Se cierra la jornada (salida fichada o cierre nocturno) | Cierra el tramo abierto y calcula |
| Cambian las pausas (añadida o deshecha) | Recalcula |
| Una corrección sustituye la jornada | **Muda** tramos e imputaciones a la versión nueva y reescala |

Cómo se calcula, por orden:

1. Si hay un reparto `MANUAL`, se respeta y se reescala en proporción al neto
   nuevo.
2. Si hay tramos, cada tramo aporta su duración menos sus pausas fichadas y
   menos lo que le solapen las pausas añadidas a mano, que sí tienen intervalo
   (ADR 015).
3. Si no hay tramos y la persona tenía **un solo** proyecto ese día, todo el neto
   va a ese proyecto. Es exactamente lo que hacían los informes antes, y lo que
   sigue pasando con quien ficha desde una app que no pregunta.
4. Con varios proyectos y sin tramos, **no se imputa nada**. Inventarse un
   reparto sería peor que dejar esas horas sin proyecto.

Al final se ajusta la línea mayor para que la suma cuadre al segundo con el neto:
los redondeos, o datos incoherentes de una edición a mano, no pueden descuadrar un
informe.

### Las horas de antes no se mueven

V23 crea una imputación por cada jornada cerrada y viva que tenía asignación
vigente ese día, con todo su neto. Un test migra una base hasta V22, siembra
jornadas en los bordes (medianoche en Madrid, relevo de proyecto, anuladas,
abiertas), saca las horas con la consulta de antes, migra a V23 y **comprueba que
las nuevas son idénticas**. Los datos de demo pasan por la misma regla al
sembrarse.

## Consecuencias

- **Corregir ya no puede perder horas de un proyecto.** Una corrección anula la
  jornada y crea otra; sin mudar las imputaciones, las horas desaparecerían del
  informe. Está cubierto por un test, y roto a propósito falla.
- **Tras corregir, el reparto pasa a `MANUAL`.** Los tramos describen la jornada
  vieja y sus horas ya no encajan en la nueva, así que se conserva la proporción.
- **Horas sin proyecto.** Con varios proyectos y sin tramos (por ejemplo, fichando
  con la app 1.3), la jornada queda sin imputar hasta que alguien la reparta.
- **Los informes ya no se pueden reconstruir solo con fichajes y asignaciones.**
  Es el precio de que una persona pueda estar en dos proyectos: la información de
  en cuál trabajó cada hora no está en ninguna otra parte.
- `V23` va detrás de `V22`.
