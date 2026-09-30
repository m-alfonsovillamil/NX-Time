# 33. Fichaje en kiosco: un dispositivo que solo ficha, tarjeta QR firmada y PIN por persona

**Estado:** aceptada · **Fecha:** septiembre 2026 · Sale de lo que el
[ADR 022](022-alcance-de-la-web.md) dejó fuera con nombre

## Contexto

En una obra, un almacén o una tienda, la gente no tiene el móvil a mano al
llegar, o no quiere instalar la app. Lo que se usa es una tablet en la entrada:
cada persona se identifica y ficha. Eso abre NX Time a un tipo de empresa al que
hasta ahora no llegaba.

Lo que no se puede perder por el camino es lo que ya garantiza el registro:
**cada fichaje es de una persona identificable** (ADR 016, art. 34.9 ET), no se
edita en el sitio (ADR 010) y queda en la cadena de auditoría (ADR 003 y 018).

## Decisión

### El kiosco es un dispositivo, no una persona

Tabla `kioscos` (V36): de una empresa, con un nombre («Entrada almacén») y un
token opaco del que solo se guarda el SHA-256, como el refresh (ADR 019). **Solo
sabe fichar**: su authority, `kiosco:fichar`, no es de ningún rol, y su filtro
(`KioskAuthenticationFilter`) solo mira `/kiosco/**` con su propio esquema,
`Authorization: Kiosco <token>`. Con ese token no se llega a nada de
`/api/v1/**`, y con el JWT de una persona no se llega al kiosco. Por eso lo
protege `SecurityConfig` por ruta y no un `@PreAuthorize`: la comprobación de
que toda authority exigida la tiene algún rol (`RoleAuthoritiesTest`) sigue
valiendo tal cual.

No se borra nunca, porque los fichajes lo citan: se revoca, y deja de valer en
la siguiente petición.

### Emparejar sin teclear nada en la tablet

La tablet abre `/kiosco`, pide un código de 8 caracteres (sin 0/O ni 1/I/L) y
lo enseña. El ADMIN lo teclea en **Ajustes de la empresa** y le pone nombre. La
tablet pregunta por el estado con un secreto que solo ella tiene, y **el token
se genera y se entrega en esa respuesta, una sola vez**. De código, secreto y
token solo se guarda el hash, y el código caduca a los diez minutos. Pedir
códigos va limitado por IP; preguntar por el estado no, porque la tablet lo
hace cada pocos segundos y el secreto (256 bits) no se adivina probando.

Los emparejamientos viejos se barren al pedir uno nuevo, no con una tarea
programada: cada tarea nueva es una base de datos despierta más.

### Tarjeta QR firmada, sin secreto guardado por persona

El QR dice `NXK1.<usuarioId>.<versión>.<firma>`, con la firma un HMAC-SHA256
del servidor sobre id y versión. Comprobarla es recalcular la firma y mirar que
la versión sea la vigente (`usuarios.kiosco_tarjeta_version`). **Regenerarla
sube la versión** y deja sin valor todas las anteriores, impresas o no.

La clave se deriva de `JWT_SECRET` con una etiqueta propia. Consecuencia
aceptada: rotar ese secreto invalida todas las tarjetas, igual que cierra todas
las sesiones.

La tarjeta solo sirve para fichar en un kiosco de su empresa; no abre ninguna
sesión ni enseña nada. Por eso RRHH (`empleado:gestionar`) puede imprimir las de
la plantilla. El backend da el QR ya dibujado en SVG (ZXing solo para la
matriz), así ni la web ni la app necesitan una librería de QR para pintarlo.

### PIN elegido por cada persona, no único, y límites por persona

Para quien no lleva la tarjeta: busca su nombre en la lista del kiosco y teclea
su PIN, de 4 a 6 cifras, sin repetidas ni seguidas. **Lo elige ella** desde su
perfil y va con BCrypt: nadie más lo ve (ADR 014). **No es único en la
empresa**: la identificación es nombre + PIN, así que elegir uno nunca revela el
de otra persona.

Los límites son **por persona y por kiosco, nunca por IP**: la tablet entera
comparte una. Cinco PIN fallidos seguidos bloquean el de esa persona quince
minutos, acierte o no después (cambiarlo desde el perfil lo desbloquea). Cada
kiosco tiene además un tope de identificaciones por minuto. La lista del kiosco
enseña solo nombre y apellidos, sin correo, y solo de quien tiene PIN.

### El fichaje es el mismo, con el kiosco dicho en la auditoría

`TimeEntryService.registrarDesdeKiosco` pasa por las mismas reglas que fichar
desde la app: la máquina de estados, el proyecto obligatorio con varios (ADR
017), el aviso de día no laborable, el panel que se refresca. `usuario` y
`modificadoPor` son la persona, porque es ella quien ficha.

Desde dónde se fichó queda en **el motivo de la fila de auditoría**
(«Desde el kiosco «Entrada almacén» (id 3)»), que ya entra en la huella de la
cadena. No hace falta cambiar la fórmula de `HuellaDeAuditoria` ni añadir un
`AuditAction`, y las filas antiguas se verifican igual. `registros.kiosco_id`
guarda además en qué kiosco se abrió la jornada, para el distintivo del
historial.

La tablet manda la credencial al identificar y **otra vez al fichar**: el
servidor no guarda ningún «ya identificado» entre las dos llamadas, así que no
hay nada que caduque ni que se pueda robar. La identificación va en su propia
transacción, que no se deshace al rechazar: si fuera la misma que la del
fichaje, un fichaje rechazado («ya hay una jornada activa») borraría también el
contador de PIN fallidos.

De paso, la auditoría guarda la IP de verdad del cliente (`IpDelCliente`, la
misma lógica que el límite de login) y no la del proxy de Render, que es lo que
guardaba hasta ahora.

## Consecuencias

- Un PIN se puede prestar, como una contraseña, y una tarjeta se puede pasar
  por otra persona. El kiosco hace más difícil fichar por otro, no lo impide.
  Hacer una foto al fichar resolvería eso, pero queda fuera: es un dato
  biométrico (RGPD, art. 9) y no se hace a la ligera.
- Con Render gratuito, el primer fichaje del día tras dormirse el servidor
  puede tardar ~50 s con gente esperando. La pantalla del kiosco no sondea al
  servidor (la cuota de Neon), así que eso no se arregla aquí: es parte de la
  decisión entre SaaS y plan de pago.
- Fichar sin conexión (una cola en la tablet) sigue fuera. En una obra sin
  cobertura sería lo siguiente.
- **La pantalla de la tablet es la web** (`/kiosco`), en cualquier tablet o
  iPad, fuera del marco y de la sesión. Lee el QR con el `BarcodeDetector` del
  navegador y, donde no existe (Safari), con jsQR cargado bajo demanda en su
  propio trozo: no cuenta en el JS inicial. La web deja usar la cámara a su
  propio origen (`Permissions-Policy: camera=(self)` en `render.yaml`).
- La tablet **no sondea** mientras espera: solo llama al identificar, al fichar
  y cada tres horas para refrescar la lista de nombres. Vuelve sola a la espera
  a los cinco segundos de fichar y a los veinte si alguien se va a medias.
- En la web: Ajustes de la empresa → Kioscos (alta con el código, lista y
  revocar); el perfil, con el PIN y la tarjeta; «Tarjetas del kiosco» para que
  RRHH imprima las de la plantilla; y el distintivo «Kiosco · …» en los
  historiales.
- En la app Android (K3): Perfil → «Fichar en un kiosco» para elegir el PIN y
  enseñar la tarjeta a pantalla completa, sobre blanco y con el brillo al
  máximo mientras está abierta. El QR se dibuja en el móvil a partir de su
  código con ZXing core; la app no lee QR ni tiene modo kiosco: la tablet es
  la web.
