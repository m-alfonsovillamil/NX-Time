# 46. El alta de empresas con aprobación, y el ciclo de vida de una empresa

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

NX Time es, desde el 17/10/2026, un servicio gratuito para empresas que presta
una persona física (plan J). Eso tiene tres consecuencias que el código no
recogía:

- **Cualquiera podía registrar una empresa y usarla al momento.** Con el
  servicio abierto, el titular responde de lo que se aloja (DSA) y es encargado
  del tratamiento de cada empresa (RGPD, art. 28), y no sabía con quién.
- **Nadie aceptaba nada.** Un encargo del tratamiento exige un contrato (art.
  28.3), y unas condiciones de servicio tienen que haberse aceptado para
  obligar (Ley 7/1998).
- **El panel de plataforma era de solo lectura** (ADR 040), y lo que miraba el
  operador quedaba en una línea de log que se pierde.

## Decisión

### Estado de la empresa

`empresas.estado` (V43): `PENDIENTE`, `ACTIVA` o `SUSPENDIDA`. Las de antes
quedan `ACTIVA`.

- Registrar (con contraseña o con Google/Microsoft, en web o app) crea la
  empresa `PENDIENTE`. **Al operador se le avisa cuando el correo está
  confirmado**: con contraseña, al canjear el código; con SSO, en el momento,
  porque el proveedor ya lo garantiza. Antes de eso no se sabe si el correo es
  de quien se registra.
- **Solo una empresa `ACTIVA` deja entrar.** Lo comprueban el login, el
  canje del código, la renovación, el SSO (con sus motivos `empresa-pendiente`
  y `empresa-suspendida`), cada petición (`SecurityUser.isEnabled`, así que un
  access token deja de valer en la siguiente petición y no a los quince
  minutos) y los kioscos.
- **Una sola respuesta para «tu empresa no está activa»: 423 Locked** con el
  texto de qué hacer. El 403 del login ya significaba «confirma tu correo» y los
  clientes lo usan para enseñar la pantalla del código: reutilizarlo habría
  mandado a esa pantalla a quien espera la aprobación.
- `application.alta.requiere-aprobacion` (por defecto `true`) lo apaga para una
  instalación propia. Los tests lo apagan salvo `AltaConAprobacionIT` y la e2e
  del registro, que prueban el camino de producción.

### Lo que hace el operador

Cuatro acciones en el panel, con `plataforma:gestionar` (la tienen los mismos
operadores que `plataforma:ver`, fuera de los roles):

| Acción | Desde | Qué pasa |
|---|---|---|
| Aprobar | `PENDIENTE` | Pasa a `ACTIVA`; correo a sus ADMIN |
| Rechazar | `PENDIENTE` | Correo a quien la registró, con el motivo, y **se borra entera** (no ha llegado a usarse) |
| Suspender | `ACTIVA` | Pasa a `SUSPENDIDA`, se revocan todas sus sesiones; correo con el motivo. Los datos se quedan |
| Reactivar | `SUSPENDIDA` | Vuelve a `ACTIVA`; correo |

No se puede suspender la propia empresa del operador: se quedaría sin el panel
con el que se reactiva.

### Lo que queda escrito

`acciones_de_plataforma` (V43): cada acción **y cada consulta al detalle** de
una empresa, con quién, cuándo y el motivo. Solo inserción, con trigger y
`REVOKE`, como la auditoría de fichajes (V5). Sin claves ajenas: una empresa
rechazada se borra y su historial tiene que quedar, con el nombre que tenía.

### Las condiciones

- El registro exige una casilla («acepto las condiciones y el contrato de
  encargo en nombre de la empresa, y soy mayor de edad»). Se guardan la versión
  (`application.legal.version`), la fecha y quién.
- Cambiar los textos es subir esa versión. Al ADMIN de una empresa con otra
  versión (o ninguna, las de antes de la V43) se le pide que las acepte al
  entrar (`condicionesPorAceptar` en la sesión). No bloquea: se queda el aviso
  hasta que las acepta.

### Quién presta el servicio, en un solo sitio

`application.titular` (producto, titular, NIF, domicilio, correos, web) en la
configuración del backend. La web lo copia con `npm run titular` a
`src/legal/titular.ts` y a `public/.well-known/security.txt`, y su CI falla si
la copia se queda atrás. Las páginas legales no piden nada a la API: el aviso
legal tiene que leerse con el servidor dormido. **El nombre «NX Time» va a
cambiar** (está cogido): esto hace que el cambio de titular y de correos sea
una línea, aunque el nombre siga repartido por los textos de la interfaz.

## Consecuencias

- La app 1.16 publicada no manda la casilla: registrar una empresa desde ella
  da 400 hasta que se publique la 1.17. No hay usuarios reales.
- Quien registra con contraseña ve dos esperas: el código del correo y la
  aprobación. Es a propósito: sin la primera, el operador recibiría avisos de
  correos que no son de nadie.
- Sin operadores configurados (`PLATAFORMA_OPERADORES` vacía) nadie aprueba:
  se registra un error en cada alta. En producción está puesta.
- **La baja de una empresa a petición suya** (exportación completa y borrado a
  los 30 días, art. 28.3.g) no va aquí: comparte la exportación en ZIP con la
  de la Inspección (J7) y llega con ella. Mientras, se pide por correo.
- Los textos legales son borradores serios: **antes de aprobar la primera
  empresa real tienen que pasar por un abogado.** La aprobación es lo que
  impide que entre nadie antes.
