# 50. Una cadena de auditoría por empresa, y la baja de una empresa con borrado

**Estado:** aceptada · **Fecha:** octubre 2026 · Cambia el [ADR 018](018-la-cadena-de-auditoria-se-serializa-en-postgres.md) y completa el [ADR 049](049-el-registro-de-jornada-propio-y-para-la-inspeccion.md)

## Contexto

Las condiciones (punto 9) dicen que una empresa que se va se lleva sus datos y,
treinta días después, se borran. No se podía hacer: la cadena de huellas de la
auditoría era **una sola para todas las empresas** (ADR 018). Cada fila
enlazaba con la anterior de la tabla, fuera de quien fuera, así que borrar las
filas de una empresa rompía la verificación de todas las demás.

Había dos salidas: anonimizar en vez de borrar (más rápido, pero quedan datos
seudonimizados, como la IP de cada fichaje) o **una cadena por empresa**. Miguel
eligió la segunda el 18/10/2026.

## Decisión

### Una cadena por empresa, sin reescribir la de antes

- **Las filas nuevas** (`version_hash = 3`, V46) encadenan con la última fila de
  SU empresa (`auditoria_fichaje.empresa_id`, solo en estas). La primera, con una
  **génesis**, `sha256("nxtime:empresa:" + id)`: así quitarle la primera fila se
  nota, y no choca con `uq_auditoria_hash_anterior`. El hash es el de la
  versión 2 más la empresa, para que una fila no se pueda pasar de una cadena a
  otra.
- **El cerrojo es por empresa** (`pg_advisory_xact_lock(82019, empresa)`): dos
  empresas fichando a la vez ya no se esperan.
- **Las filas de antes no se tocan.** Siguen encadenadas entre sí como se
  escribieron y forman el **tramo común**. Reescribir sus hashes para repartirlas
  por empresas habría sido lo más limpio de cara al código, pero reescribir una
  cadena de integridad es exactamente lo que haría quien quisiera manipularla, y
  lo ya comprobado (puntos de control, firmas) dejaría de valer.

### Las lápidas del tramo común

Cuando se borra una empresa, cada fila suya del tramo común deja una lápida en
`eslabones_retirados`: su id, su `hash_anterior` y su `hash`, sin nada de su
contenido. El verificador las intercala en su sitio y comprueba el enlace, como
con las filas de la versión 1. Un punto de control que apunte a una fila ya
retirada sigue valiendo si la lápida tiene el mismo hash.

### El verificador

Recorre el tramo común y la cadena de cada empresa, cada una con sus puntos de
control (`puntos_control_auditoria.empresa_id`, null para el tramo común). **Una
cadena rota en una empresa no deja sin comprobar a las demás**; solo el tramo
común, que es uno, sigue avisando a todas («el problema está en un movimiento
que no es de tu empresa»).

### La baja

- La pide un ADMIN desde los ajustes de la empresa, **escribiendo su nombre**.
  Les llega un correo a los ADMIN. Durante treinta días todo sigue funcionando
  para que se lleven los datos (la exportación para la Inspección, ADR 049), y
  se puede cancelar.
- Pasados, la tarea de las 03:45 llama a **`borrar_empresa(id)`**, una función
  `SECURITY DEFINER` de la base que borra todo en orden de dependencias, deja
  las lápidas y apunta la empresa en `empresas_borradas` (nombre y fechas, nada
  de su gente).
- **La condición de los treinta días está en la función**, no solo en Java: la
  regla que protege el borrado vive donde está el borrado.
- **El trigger de la auditoría tiene una sola puerta**: deja borrar solo si
  `current_user <> session_user` (es decir, desde dentro de la función, que
  corre como su dueño) y la variable de sesión dice qué empresa se está
  borrando. Un `DELETE` de la aplicación, o del dueño sin pasar por la función,
  sigue chocando, aunque ponga la variable a mano. En Neon esto es lo que
  protege de verdad: los `REVOKE` no bastan (V5).
- `empresas_borradas` y `eslabones_retirados` son de solo inserción, y la
  aplicación solo las lee.
- `BorradoDeEmpresaIT` comprueba que, tras el borrado, **no queda nada en
  ninguna tabla** con `empresa_id` o `usuario_id` de esa empresa: una tabla
  nueva que la función no conozca haría fallar el borrado por su clave ajena, y
  este test lo vería.

## Consecuencias

- La traza de una empresa se puede comprobar sola, sin las de las demás: lo que
  hace falta si algún día se le entrega a una autoridad.
- Lo que había antes de V46 sigue mezclado en el tramo común: una rotura ahí la
  ven todas las empresas que tienen filas en él. Con el tiempo, a medida que
  esas filas cumplan sus cuatro años, el tramo común deja de importar.
- Quien pueda escribir en `empresas` como la aplicación podría adelantar la
  fecha de una baja y provocar un borrado. Es el mismo nivel de acceso que ya
  permite hacer casi cualquier cosa con los datos de la empresa; lo que no
  puede es borrar la auditoría sin dejar rastro: quedan la empresa en
  `empresas_borradas`, las lápidas y el log.
- `bloquearCadena` y su clave global desaparecen: ya no los usa nadie.
