# 26. La analítica cuenta días, no horas, y el alcance lo decide quién mira

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Con los cuadrantes (B1) y las incidencias (B2), el sistema ya sabe qué se debía
trabajar y qué no cuadró. La Fase B4 junta esos datos en dos cifras que RRHH
pide siempre: el **absentismo** y la **puntualidad**, por mes, trimestre o año, y
por departamento o por persona.

«Absentismo» no tiene una definición única. Hacía falta elegir una, escribirla
y que el código no dijera otra cosa.

## Decisión

### Qué es un día perdido

Para cada persona y cada día, en este orden (`ReglasDeAbsentismo`):

1. **¿Se debía trabajar?** Lo dice lo *planificado*: festivos, excepciones y
   plantilla, **sin mirar las ausencias**. Un festivo no se trabaja; con
   cuadrante, se trabaja si ese día tiene horas; sin cuadrante, de lunes a
   viernes, la misma regla que el objetivo semanal de las horas extra.
2. **Vacaciones:** el día sale de la cuenta. Contarlas haría que agosto
   pareciera un mes ejemplar.
3. **Fichó, o estaba de viaje de trabajo:** trabajado. El fichaje manda sobre
   una ausencia que no sean vacaciones: quien tenía aprobada una consulta médica
   y luego fue a trabajar, trabajó.
4. **Otra ausencia aprobada:** día perdido *con motivo*, desglosado por tipo.
5. **Una ausencia de cuadrante cuya explicación se aceptó (B2):** perdido con
   motivo.
6. Si no, **sin fichaje ni ausencia**. No se llama «injustificado»: el sistema
   no sabe por qué, solo que no consta nada, y puede ser un olvido de fichar.

El absentismo es *(con motivo + sin fichaje) / días laborables*, y se publica
también el de solo *sin fichaje*, para que nadie tenga que restar a mano las
bajas médicas.

Lo planificado se pide **sin ausencias** a propósito, con un método nuevo,
`JornadaTeoricaService.planificadoDeVarios`. Con el horario teórico de siempre,
una baja ya sale como «no laborable» y no se distingue de un sábado: o la baja
no contaría, o el sábado contaría como perdido.

### En días, no en horas

Las ausencias de este sistema son de días enteros. Contar en horas obligaría a
inventar cuántas horas «vale» un día de baja de alguien sin cuadrante, y un día
de baja a media jornada es un día perdido, no cuatro horas.

### Desde que empezó a fichar

El modelo no tiene fecha de alta. Sin ella, alguien contratado en septiembre
saldría con ocho meses de absentismo en el informe del año. **La primera jornada
registrada hace de fecha de alta**, y la baja (`fecha_baja`) cierra la cuenta.
Quien no ha fichado nunca no entra. Es un apaño con límites conocidos: si
alguien entra y se da de baja médica el segundo día, cuenta desde el primero,
que es lo correcto; pero si nunca llega a fichar, no cuenta. Se arregla el día
que haya fecha de alta en la ficha, y no justificaba una migración en esta fase.

### Hasta ayer

El día de hoy no ha terminado. Contarlo pondría a media plantilla como ausente a
las diez de la mañana. Cada respuesta dice hasta qué día contó
(`evaluadoHasta`), y el día 1 del mes no hay ningún día terminado: los
porcentajes van a **null**, no a cero. Un 0 % afirmaría que nadie faltó.

### Puntualidad solo de quien tiene cuadrante

Sin horario teórico no hay hora a la que llegar tarde. El denominador son los
días con horario en que se fichó; los retrasos, las incidencias `RETRASO` de B2
en cualquier estado (uno explicado sigue siendo un retraso). La media y la
mediana son de los retrasos, no de todas las entradas.

### El alcance no es un permiso

`analitica:leer` empieza en GESTOR. Pero un GESTOR ve **su departamento** y RRHH
y ADMIN **la empresa**. Es la misma operación sobre conjuntos distintos, así que
el recorte vive en el servicio y no en un `@PreAuthorize`. Lo decide
`empleado:gestionar`, que es quien responde de la plantilla entera.

Un GESTOR **sin departamento** recibe un 409 que dice por qué. Ni la empresa
entera, que no le corresponde, ni un cero, que afirmaría que su equipo no falta
nunca.

### Consultas nativas en lote, cruce en Java

Todo lo que se puede contar en la base se cuenta allí, en consultas nativas con
proyecciones (`AnalyticsRepository`): quién entra, los días con jornada, las
ausencias día a día (`generate_series`), las ausencias aceptadas y los retrasos,
agregados a los tres niveles de una vez (`GROUPING SETS`, `percentile_cont`).
Ninguna se monta concatenando SQL.

Lo que **no** se hace en SQL es decidir si un día se debía trabajar: eso son
plantillas con vigencia, excepciones y precedencia, y vive en un solo sitio,
`JornadaTeoricaService`. Copiarlo en SQL serían dos verdades. Así que el censo
persona × día se hace en Java, pero **sin ninguna consulta dentro del bucle**:
el número de consultas no depende de cuántas personas ni de cuántos días.

Se cachea media hora (`CacheConfig.ANALITICA`) por persona que mira y por día, y
se vacía cuando el barrido de B2 detecta o alguien decide una incidencia.

### En el móvil, solo una tarjeta

Una tabla por departamento no cabe en 400 dp, y quien la consulta trabaja
sentado. La app enseña una tarjeta en el panel de empresa con los dos
porcentajes del mes. Sin enlace «ver el detalle»: la pantalla de la web todavía
no existe, y un enlace a una página que no lo tiene es peor que no ponerlo.

## Consecuencias

- Cuatro rutas nuevas en `/api/v1/analitica` (`/resumen`, `/absentismo`,
  `/puntualidad`, `/absentismo.csv`), 119 en total. Sin migración.
- El CSV es el primero del proyecto: punto y coma, coma decimal, BOM de UTF-8 y
  nombres neutralizados contra inyección de fórmulas, porque el nombre lo
  escribe la propia persona.
- Las horas extra **no** entran en el resumen. Los avisos diarios y semanales se
  solapan (una jornada larga puede estar en los dos), y sumarlos contaría dos
  veces. Se añadirá cuando haya una cifra que no mienta.
- Los retrasos por departamento van por el departamento **actual** de cada
  persona, no por el que tenía el día del retraso: el modelo no guarda el
  histórico de departamentos.
- Queda por hacer la pantalla de la web que use `/absentismo` y `/puntualidad`.
