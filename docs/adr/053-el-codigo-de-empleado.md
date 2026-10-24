# 53. El código de empleado

**Estado:** aceptada · **Fecha:** octubre 2026 · Completa el [ADR 052](052-importar-la-plantilla-y-los-roles.md)

## Contexto

Una empresa de cien personas ya tiene un ERP o un programa de nóminas (el
caso que salió el 24/10/2026 fue Dynamics 365), y lo que pide de un sistema de
fichaje es poder llevarse las horas allí. Para eso cada persona de NX Time
tiene que casar con su ficha en ese sistema, y no había con qué:

- **el nombre** se repite y se escribe de mil maneras («M.ª José», «Mª Jose»);
- **el correo** cambia (una boda, un cambio de dominio) y en muchos ERP ni
  siquiera está;
- **el id de NX Time** no lo conoce el ERP.

Lo que sí tienen todos es un número de personal propio. Hacer ya la API de
integración entera sería adivinar qué necesita un cliente que todavía no la ha
pedido; el código, en cambio, lo va a necesitar cualquier integración, también
la manual de bajarse un Excel e importarlo.

## Decisión

- **`usuarios.codigo_empleado`** (V49): opcional, hasta 50 caracteres, **único
  dentro de la empresa sin distinguir mayúsculas** (un índice único parcial sobre
  `(empresa_id, upper(codigo_empleado))`). Entre empresas se puede repetir: cada
  una numera a su gente.
- **Formato** (`CodigosDeEmpleado`): letras, números y `. _ / -`, sin espacios,
  empezando por letra o número. Cabe lo que usan los ERP («000123», «EMP-0042»,
  «MAD/17») y deja fuera `= + - @`, con los que una hoja de cálculo tomaría la
  celda por una fórmula al abrir una exportación.
- **Lo ponen RRHH y ADMIN** (`empleado:configurar`), no la propia persona: es un
  dato de la empresa sobre ella.
  - En la **ficha**: `PATCH /gestor/empleados/{id}/ficha` con `codigoEmpleado`.
    Aquí, a diferencia de la jornada, null es «no tocar» y la cadena vacía **lo
    quita**, porque «sin código» sí significa algo. Repetido da 409 diciendo
    quién lo tiene.
  - En la **importación** de plantilla: una columna más, que se reconoce como
    «código», «nº empleado», «número de personal», «matrícula», «personnel
    number»… y no puede repetirse ni en la empresa ni en el fichero.
- **Sale** en la lista de la plantilla (columna y buscador, solo si alguien de
  la empresa tiene código), en el **Excel de horas** y en la exportación de
  «mis datos» (es un dato personal más).
- En el Excel va **la última columna, como texto**: así no se mueven las que ya
  había (por si alguien ya lee ese Excel por posición) y «000123» no pierde los
  ceros.

## Consecuencias

- Es el primer paso de cualquier integración con un ERP. La API de integración
  (acceso para programas, horas por periodo, altas y bajas por API) queda para
  cuando un cliente concreto diga qué parte de su ERP recibe las horas.
- Un Excel guardado con el código como **número** pierde los ceros a la
  izquierda antes de llegar aquí; NX Time ve «123». La ayuda del ejemplo de
  importación lo lleva como texto.
- La comprobación previa da un mensaje claro, pero lo que garantiza que no haya
  dos iguales es el índice: dos peticiones a la vez con el mismo código
  terminan en un 409, no en dos personas con el mismo número.
