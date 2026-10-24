# 52. Importar la plantilla, invitaciones en tandas y cambiar el rol

**Estado:** aceptada · **Fecha:** octubre 2026 · Completa el [ADR 014](014-acceso-por-codigo.md) y el [ADR 034](034-endurecimiento-de-octubre.md)

## Contexto

En la revisión del 24/10/2026 («¿se le puede ofrecer a una empresa de 100
personas?») salieron dos huecos que no eran de infraestructura:

- **Dar de alta a la plantilla era de una en una.** Cien formularios, y después
  la jornada, el departamento y las vacaciones de cada uno por separado.
- **No había forma de nombrar a nadie RRHH ni a un segundo ADMIN.** Solo
  existían el ADMIN que registró la empresa, los GESTOR que él creaba y los
  EMPLEADO. Ningún endpoint cambiaba un rol. Una empresa de cien personas
  necesita RRHH, y un segundo ADMIN por si el primero se va.

Y dos límites que ya existían y que una importación tiene que respetar:

- **50 altas al día por empresa** (ADR 034), porque cada alta manda un correo y
  una cuenta de ADMIN robada no tiene que servir para mandar correo a granel.
- **El tope diario del proveedor de correo**: 300 en el plan gratuito de Brevo,
  para toda la instalación.

## Decisión

### Importar desde un CSV o un Excel

- `POST /api/v1/gestor/plantilla/importar` con el fichero, en **dos pasos**:
  `simular=true` lee y comprueba sin crear nada, y devuelve cada fila con sus
  errores; `simular=false` crea a todas. **O todas o ninguna**: con una fila
  mala no se crea a nadie, para no dejar media plantilla dentro y tener que
  adivinar cuál falta.
- **Lo que sale de Excel, tal cual** (`LectorDePlantilla`):
  - CSV en UTF-8 o en Windows-1252, que es como lo guarda Excel en español;
  - separado por punto y coma, coma o tabulador;
  - o `.xlsx` (la primera hoja);
  - cabeceras sin mayúsculas ni tildes y con sinónimos («correo», «jornada»...);
  - como mucho 500 personas por fichero.
- **Las mismas reglas que el alta a mano**, con los permisos de quien sube el
  fichero:
  - rol por encima de EMPLEADO: `gestor:crear`;
  - departamento: `departamento:gestionar`, y tiene que existir ya;
  - jornada y vacaciones: `empleado:configurar`, con los límites de la ficha;
  - correo sin cuenta en ninguna empresa, y sin repetirse en el fichero.
- Una sola contraseña inutilizable para todas las cuentas creadas (el hash de
  un UUID que no se guarda): cien BCrypt seguidos con 0,1 de CPU son más de un
  minuto.

### Las invitaciones, en tandas

- Las cuentas se crean todas; los **correos de alta** se encolan en
  `invitaciones_pendientes` (V48).
- Salen en segundo plano, una a una y cada una en su transacción, con el mismo
  `emitirCodigoDeAlta` de siempre. El cupo de hoy es lo menor entre:
  - lo que le queda a la empresa de sus 50 altas en 24 horas;
  - lo que le queda a la instalación de `application.mail.altas-por-dia`
    (`CORREO_ALTAS_POR_DIA`, 250 por defecto, por debajo de los 300 de Brevo).
- Si un correo no sale, ese queda en la cola con el error y la tanda para:
  insistir no arreglaría nada.
- Lo que no cabe espera. La plantilla marca a esas personas («Sin invitar
  todavía») y un aviso ofrece **mandar las invitaciones** cuando vuelve a haber
  cupo (`POST .../invitaciones/enviar`). No hay tarea programada: una más
  despertaría la base a horas en las que nadie la usa (plan gratuito de Neon).

### Cambiar el rol

- `PATCH /api/v1/gestor/empleados/{id}/rol`, solo con `gestor:crear` (ADMIN).
- Nunca el propio: así quien lo cambia sigue siendo ADMIN, y la empresa no se
  queda nunca sin ninguno.
- Las authorities se resuelven en cada petición: vale desde la siguiente, sin
  volver a entrar.

## Consecuencias

- Una plantilla de 100 personas se da de alta en un minuto. Las invitaciones
  llegan en dos días (50 y 50), salvo que se suba el cupo por empresa.
- Las tandas en marcha se apuntan en memoria: vale para una sola instancia,
  como el resto de cupos (ADR 039).
- La app no cambia: importar y cambiar roles es cosa de la web.
