# 43. Jackson 3: un solo serializador en el código propio, y lo que se firma escrito igual que antes

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

Spring Boot 4 usa Jackson 3, que es otra librería a efectos prácticos: otro
paquete (`tools.jackson` en vez de `com.fasterxml.jackson`) y otros valores
por defecto. Al subir a Spring Boot 4 se dejó puesto `spring-boot-jackson2`, el
puente con el que Spring sigue publicando el `ObjectMapper` de Jackson 2, para
no cambiar de versión de Spring y de serializador a la vez. Spring lo da por
transitorio.

Al ir a quitarlo salió que el cuadro no era el que se creía. Se pensaba que
«la API sigue en Jackson 2»; no era así:

- **Las respuestas de la API ya salían por Jackson 3** desde la subida a
  Spring Boot 4. Con las dos versiones en el classpath, Spring MVC prefiere la
  3 (`JacksonJsonHttpMessageConverter`). El contrato lleva en producción con
  ella desde entonces, y `ApiContractTest` y los e2e lo han estado probando
  así.
- **El puente solo servía al código propio que pide un `ObjectMapper`**: los
  filtros que escriben a mano un cuerpo de error (401, 403, 429 y 503), el
  serializador de las instantáneas de auditoría, el estado firmado del SSO y
  el que lee la hora de una instantánea para invalidar firmas.
- **Aparte está `Canonico`**, que tiene su propio mapper a propósito: escribe
  la forma canónica (claves ordenadas, sin espacios) de lo que se firma, que
  son las instantáneas de la traza de auditoría (ADR 018) y el resumen que
  firma cada persona al firmar su mes (ADR 025).

Lo delicado es lo último. La traza es de solo inserción y cada fila lleva un
hash sobre esa forma canónica; el verificador recalcula con el código de hoy.
Si Jackson 3 escribe distinto un solo carácter para alguna fila ya guardada,
esa fila pasa a «no cuadra» sin que nadie la haya tocado, y con ella todas las
firmas mensuales que dependan de lo mismo.

## Decisión

**El código propio usa solo Jackson 3, y se quita el puente.** Las nueve clases
que inyectaban el `ObjectMapper` de la 2 pasan a pedir el de la 3, que es el
mismo que ya escribía las respuestas de la API.

**`Canonico` también pasa a Jackson 3, con la definición de antes guardada en
un test.** Se valoró dejarlo en Jackson 2 para siempre, que es la forma más
barata de no mover nada. No se hizo porque obliga a declarar como dependencia
propia una versión mayor que ya solo llega de rebote, y porque el problema
volvería el día que Jackson 2 deje de mantenerse. Lo que protege la traza no es
quedarse quieto, es poder demostrar que el texto no cambia:

- `CanonicoFrenteAJackson2Test` tiene copiada la definición vieja tal cual y
  compara las dos **texto a texto** sobre 468 casos: instantáneas como las
  escribe el serializador y como las devuelve `jsonb`, fechas como número,
  enteros que no caben en un `long`, decimales al límite y 10.000 números al
  azar con semilla fija, barras, tildes, emoji, caracteres de control, claves
  repetidas, y una cincuentena de textos que no son JSON o lo son a medias.
  Jackson 2 se queda como dependencia **solo de los tests** para poder
  ejecutarla.
- La comparación encontró **una** diferencia: Jackson 3 rechaza por defecto lo
  que sobre detrás de un JSON completo (`{"a":1} sobra`), y la 2 lo ignoraba.
  De una columna `jsonb` no puede venir, pero la definición tiene que ser la
  misma, así que `Canonico` lo desactiva (`FAIL_ON_TRAILING_TOKENS`). Todo lo
  demás sale idéntico sin tocar nada.
- `HuellaDeAuditoriaTest` sigue fijando dos hashes literales, calculados con
  el código original.

## Lo que se ha comprobado en vivo

Con el jar de antes (Jackson 2) contra una base nueva: tres jornadas con
pausa, tres correcciones aprobadas (una de ellas, una pausa añadida a
posteriori) y la firma de un mes. Con el jar nuevo **sobre esa misma base**:

- La traza: 15 movimientos escritos por la versión anterior, los 15
  recalculados y cuadran, desde la empresa y desde el panel de plataforma.
  Después de escribir seis más con la versión nueva, los 21.
- La firma mensual: el hash recalculado es el mismo que se firmó.
- 204 respuestas de la API guardadas antes y después (todas las rutas `GET`
  sin parámetros con tres cuentas, más nueve errores provocados): 186
  idénticas byte a byte; ocho solo cambian en una hora que es «ahora».
- **Las otras diez son lo único que cambia de cara a un cliente**: los 401 y
  403 que escriben los filtros llevan los mismos campos con los mismos
  valores, en otro orden (`detail`, `status`, `title` en vez de `title`,
  `status`, `detail`). Es el orden que ya tenían todos los demás errores de la
  API, que salen de Spring MVC. La web y la app los leen por nombre.

La base de la prueba de carga no se ha usado para esto: su millón y medio de
movimientos son de los que solo se comprueba el enlace con la fila anterior
(`version_hash = 1`) y no pasan por la forma canónica.

## Lo que esto no arregla

- **Jackson 2 sigue en el classpath.** Lo traen `jjwt-jackson`, swagger y el
  cliente HTTP de Google que usa Firebase. No lo usa ninguna clase de
  `src/main`, pero el compilador no lo impide: como sigue ahí, un `import` de
  `com.fasterxml.jackson.databind` compila. Lo que falla es el arranque, porque
  ya no hay ningún `ObjectMapper` de la 2 que inyectar.
- **Subir a una versión mayor de Jackson pide repetir la comparación.** El
  test compara contra la 2, que es con la que se firmó casi todo lo que hay.
  El día que llegue la 4, la referencia tendrá que ser la 3.
- **Las filas con fechas como número**, si quedara alguna muy antigua, pasan
  por un `double` al canonizarse, igual que antes. Las dos versiones dan el
  mismo texto en los casos probados; no se ha buscado en producción si existe
  alguna fila así.

## Consecuencias

- Una dependencia transitoria menos, y un solo serializador que entender en el
  código propio.
- Un test más lento que los demás de su paquete (468 casos, décimas de
  segundo) y una dependencia de test que no es evidente: está explicada en
  `build.gradle.kts`.
