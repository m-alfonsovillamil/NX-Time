# 9. Las asignaciones a proyecto llevan vigencia, y el solape lo impide la base

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Hay que saber a qué proyecto se imputan las horas de cada persona. La forma
obvia es un campo `proyecto_id` en `usuarios`: una columna, un JOIN trivial, y
la pregunta "¿en qué proyecto está Ana?" se responde sola.

El problema aparece con la segunda pregunta, que es la que de verdad se hace:
**"¿cuántas horas se han imputado a NX-CORE en julio?"**. Con un campo en
`usuarios`, esa consulta une las jornadas con el proyecto que la persona tiene
**hoy**. Así que el día que Ana cambia de proyecto, todas sus horas pasadas
cambian de proyecto con ella —incluidas las de meses ya informados—, y un
informe de enero deja de decir lo que decía en enero.

## Decisión

Una tabla `asignaciones_proyecto` con `fecha_inicio` y `fecha_fin`
(`NULL` = sigue asignado). Las horas de un día van al proyecto que esa persona
tenía asignado **ese día**, y la consulta agregada une por rango de fechas en
vez de por igualdad.

Dos consecuencias que hacen falta para que esto funcione de verdad:

- **Sacar a alguien de un proyecto NO borra su asignación**: le pone fecha de
  fin. Borrarla haría desaparecer sus horas pasadas de ese proyecto, que es
  exactamente lo que este diseño existe para evitar. El endpoint se llama
  "finalizar", no "eliminar", y la pantalla lo dice con esas palabras.
- **Un proyecto terminado se cierra (`activo = false`), no se borra.** Borrarlo
  con asignaciones dentro devuelve 409 y sugiere cerrarlo.

## Una persona no puede estar en dos proyectos el mismo día, y lo impone PostgreSQL

```sql
EXCLUDE USING gist (
    usuario_id WITH =,
    daterange(fecha_inicio, COALESCE(fecha_fin, 'infinity'), '[]') WITH &&
)
```

Va en la base y no en el servicio porque comprobarlo en Java es *leer y luego
escribir*: dos asignaciones que llegan a la vez pasan las dos la lectura. Es el
mismo defecto que la Fase 3 quitó del resto del esquema, cuando todas las
restricciones vivían como comprobaciones en código.

Necesita la extensión `btree_gist` (para mezclar un entero B-tree con un rango
GiST en la misma restricción). **Comprobado antes de escribir la migración** —era
la condición que dejó anotada el plan— en los tres sitios donde tiene que
funcionar: Neon (`btree_gist` 1.8 disponible), el PostgreSQL de
`docker-compose`, y la imagen `postgres:18-alpine` que levanta el CI, donde
además se probó la restricción con INSERT reales.

El servicio **también** comprueba el solape antes de insertar, y no es
redundante: sirve para poder decir **en qué proyecto** está ya la persona. El
`EXCLUDE` es quien lo garantiza, y su rechazo se traduce a un 409 con
explicación en vez de un 500 —incluido el caso que la lectura previa no ve, como
asignar un rango que engloba una asignación futura.

## Detalles que se comprobaron y no se supusieron

- **`daterange(..., '[]')` normaliza el límite superior a exclusivo**: un rango
  que acaba el 30/6 se guarda como `[1/1, 1/7)`, así que una asignación que
  termina el 30/6 y otra que empieza el 1/7 **no** se solapan. Es lo que se
  quiere (el relevo es al día siguiente), y está verificado con un INSERT, no
  deducido de la documentación.
- **El día de una jornada es el día ESPAÑOL.** `hora_entrada` es `TIMESTAMPTZ`
  en UTC (ADR 002), así que la consulta la proyecta a `Europe/Madrid` antes de
  quedarse con la fecha. Sin eso, una jornada que empieza a las 00:30 hora
  española es todavía del día anterior en UTC, y en un relevo a fin de mes se
  imputaría al proyecto equivocado.
- Se toma la fecha de la **entrada**, no de la salida, igual que los informes:
  una jornada nocturna pertenece al día en que empieza.

## Consecuencias

**A favor**

- Cambiar a alguien de proyecto no reescribe su pasado. Verificado con datos de
  demo: en el mes del relevo, las horas de una misma persona aparecen repartidas
  entre los dos proyectos.
- La regla "nadie en dos proyectos a la vez" no se puede saltar, ni por una
  carrera ni por un endpoint que se olvide de comprobarla.
- El histórico completo queda disponible para el perfil y para auditar.

**En contra**

- La consulta de horas es un JOIN por rango, más caro que uno por igualdad. Se
  hace nativa y agregada en la base, como el resto de agregados del panel.
- **Una persona no puede repartir su jornada entre dos proyectos el mismo día.**
  Es una limitación real y consciente: NX Time registra jornada, no imputación
  por tarea, y permitir el reparto exigiría fichar contra un proyecto, que es
  otro producto. Si algún día hace falta, la salida es una tabla de imputación
  por fichaje, no relajar el `EXCLUDE`.
- Depende de una extensión de PostgreSQL. Comprobada en los tres entornos, pero
  es una atadura más a este motor —coherente con el ADR 001, que ya la asumió.
