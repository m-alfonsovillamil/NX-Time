# 4. Migrar el backend de Kotlin a Java 21

**Estado:** aceptada · **Fecha:** agosto 2026

## Contexto

El backend estaba escrito **íntegramente en Kotlin**: 41 ficheros `.kt`, ni un
solo `.java`.

Kotlin no tiene nada de malo —es un lenguaje excelente para Spring Boot— pero
este proyecto tiene un objetivo concreto: **sostener una entrevista técnica para
un puesto junior de backend Java**. Y ahí el desajuste importa:

- Los filtros automáticos de candidatos buscan Java.
- La prueba técnica y las preguntas girarán sobre Java.
- Presentar como proyecto principal uno escrito en otro lenguaje obliga a
  justificarlo en cada conversación, en vez de hablar de lo que hace.

La app Android sí se queda en Kotlin, que es lo idiomático allí.

## Decisión

Reescribir el backend en **Java 21**, y hacerlo **cuanto antes**: cada clase
nueva escrita en Kotlin sería otra clase que migrar después. Se hizo antes de
añadir nada nuevo.

No fue una traducción literal. Se aprovechó para adoptar lo idiomático de Java
moderno y corregir de paso antipatrones que venían del original:

| En Kotlin | En Java 21 |
|---|---|
| `data class` de DTO | `record` |
| `data class` con `@Entity` | clase normal con `equals`/`hashCode` **sobre el id** — el `data class` los generaba sobre todos los campos, un antipatrón conocido en JPA con relaciones perezosas |
| `when` sobre un `String` libre | `switch` con *pattern matching* sobre un **enum** (`TipoFichaje`), así un valor inválido lo rechaza Jackson antes de llegar al servicio |
| tipos anulables (`Registros?`) | `Optional<T>` en repositorios y servicios |
| funciones de extensión `toDTO()` | **MapStruct**, que es lo que se usa en empresa |

El criterio de aceptación fue objetivo: **los tests de contrato escritos antes
de migrar tenían que seguir pasando sin tocarlos**. Como atacan HTTP y no
clases, sobrevivieron intactos a la reescritura.

## Consecuencias

**A favor**

- El proyecto encaja con el puesto al que se dirige.
- Se corrigieron antipatrones que la traducción directa habría conservado.
- Java 21 aporta temas de conversación reales (`record`, *pattern matching*,
  hilos virtuales).

**En contra**

- Fue la fase de mayor volumen de todo el plan: 41 ficheros reescritos.
- Riesgo de introducir errores en una reescritura masiva. Se mitigó migrando por
  paquetes (dominio → DTO → repositorio → servicio → seguridad → controlador),
  compilando en cada paso, con los tests de contrato como red.

## Nota sobre el idioma del código

Se pasaron paquetes y clases a inglés (`controller`, `service`, `TimeEntry`,
`AbsenceRequest`), que es la convención dominante en las ofertas objetivo, pero
**los nombres de campos de DTO y los valores de los enums se quedaron en
español** (`fechaInicio`, `VACACIONES`, `EMPLEADO`): son el contrato JSON real
que consume la app Android, y cambiarlos habría sido una rotura sin ninguna
ganancia.
