# 27. Las listas que crecen sin límite van por páginas, con un DTO propio

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Hasta la Fase A7 casi todas las listas de la API devolvían un array entero.
Algunas tenían un tope fijo y **no decían que lo tenían**:

- `/fichaje/historial` y `/fichaje/gestor/historial` servían las 200 últimas,
  sin forma de pedir las anteriores.
- `/avisos` servía los 50 últimos. Los anteriores no se podían ver desde ningún
  sitio, y la app calculaba el número de no leídos contando esa lista cortada.
- La bandeja de incidencias (B2) cortaba en 200.

Otras no tenían tope ninguno. `/gestor/ausencias-historial` devolvía todas las
ausencias resueltas de la empresa desde siempre, calculando los días hábiles de
cada una. `/correcciones/mias` hacía una consulta por fila (el reparto por
proyecto) de todas las correcciones que alguien hubiera pedido nunca.

## Decisión

### Siete listas, no todas

Van por páginas las que **crecen con el tiempo sin que nadie haga nada**: los dos
historiales de fichajes, los avisos, mis correcciones, mis ausencias, el
historial de ausencias del equipo y la bandeja de incidencias.

No van por páginas, a propósito:

- **Las bandejas de pendientes** (correcciones, ausencias). Su tamaño es el
  trabajo que queda por hacer, y esconder la mitad detrás de un «cargar más»
  sería esconder trabajo. Además, en correcciones el filtro de quién resuelve
  qué se aplica en Java (`puedeResolver`), y paginarlo en SQL obligaría a copiar
  esa regla en JPQL.
- **La auditoría de un fichaje**: son unas pocas filas por jornada.
- **Las horas extra y mis incidencias**: ya van por año.

### Un DTO propio, no el `Page` de Spring

`PaginaDTO<T>(contenido, pagina, tamano, totalElementos, totalPaginas, hayMas)`.
El `Page` de Spring serializa una docena de campos internos (`pageable`,
`sort`…), su formato no es estable entre versiones de Spring Data, y springdoc lo
documenta mal. Con un record propio, springdoc genera un esquema tipado por lista
(`PaginaDTONoticeResponse`…) y la web recibe tipos buenos de `openapi-typescript`.

### Parámetros y límites

`pagina` desde 0 y `tamano` de 1 a 200, con 50 por defecto (`Paginacion.pedir`).
Fuera de rango es un **400, no un recorte**: un cliente que pide 500 y recibe 200
sin saberlo creería que no hay más.

Cada consulta ordena con un **desempate por id** (`ORDER BY fecha DESC, id DESC`).
Sin él, dos filas con el mismo instante no tienen orden definido y una puede
salir en dos páginas o en ninguna. Pasa de verdad: un barrido nocturno crea los
avisos en ráfaga. Las que llevan `JOIN FETCH` declaran su `countQuery`, porque
Hibernate no sabe contar un fetch.

### Las pantallas que necesitan el periodo entero piden todas las páginas

El historial de un mes suma sus horas, y la pantalla de ausencias filtra en local
por año. Sumar o filtrar solo la primera página daría un resultado falso sin
avisar. Esas pantallas piden páginas de 200 hasta que `hayMas` es falso
(`Paginas.todas`, con un tope de 20 páginas por si el servidor mintiera). El
servidor ya limita esos periodos a un año, así que son pocas peticiones.

Las demás cargan la página siguiente cuando el final de la lista asoma
(`finDeLista`). Al juntar páginas se quitan los repetidos por id: entre una
petición y la siguiente pueden llegar filas nuevas arriba, que empujan una hacia
abajo y la harían salir dos veces.

### Es un cambio incompatible, y se asume

La API no tiene más clientes que la app y la web. La web no usaba ninguna de
estas listas. La app se actualiza en el mismo cambio (versión 1.6, versionCode 7).

## Consecuencias

- **El APK 1.5 deja de cargar esas siete pantallas** en cuanto el backend nuevo
  se despliega: espera un array y recibe un objeto. Desplegar el backend y
  publicar el APK tienen que ir juntos.
- El contador de avisos no leídos sale siempre de `/avisos/no-leidos`, nunca de
  contar la lista.
- El historial del equipo sigue filtrando por empleado en local. Si el filtro deja
  la pantalla corta, el final de la lista sigue a la vista y va pidiendo páginas.
  Funciona, pero lo correcto sería filtrar en el servidor.
