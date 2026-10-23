# 51. El segundo factor (TOTP), las contraseñas comunes y cerrar sesiones al momento

**Estado:** aceptada · **Fecha:** octubre 2026 · Completa el [ADR 034](034-endurecimiento-de-octubre.md) y el [ADR 036](036-entrar-con-google-o-microsoft.md)

## Contexto

Hasta octubre de 2026 una contraseña bastaba para entrar en cualquier cuenta,
también en las que más pueden hacer:

- **El operador de plataforma** (ADR 040) ve y gestiona todas las empresas.
- **El Responsable del canal de denuncias** (ADR 048) lee expedientes que la
  Ley 2/2023 obliga a proteger. La Recomendación 1/2026 de la Autoridad
  Independiente de Protección del Informante le pide un segundo factor.
- **Los ADMIN y RRHH** ven y cambian datos de toda la plantilla.

Además:

- Las contraseñas se elegían sin más regla que el largo, así que
  «Password123» valía.
- «Cerrar todas las sesiones» revocaba los refresh, pero el access token de un
  intruso seguía valiendo sus quince minutos.

## Decisión

### TOTP, y nada más

- **El código de 6 cifras de una aplicación de autenticación** (RFC 6238):
  - SHA-1, 30 segundos y una ventana de un paso a cada lado. Son los
    parámetros que entienden todas las aplicaciones; otros valores están en la
    norma, pero hay aplicaciones que los ignoran sin avisar.
  - Implementado a mano (`security/totp/Totp`), sin librería: es un HMAC y una
    cuenta. Los vectores de la RFC están en `TotpTest`.
- **Un código no vale dos veces.** Se guarda el último paso aceptado
  (`segundo_factor.ultimo_paso`), y se gasta con un `UPDATE … WHERE
  ultimo_paso < :paso`: si llegan dos peticiones a la vez con el mismo código,
  solo vale una.
- **El secreto va cifrado** en la base, con AES-256-GCM y la clave
  **`TOTP_CLAVE`**, que está fuera de la base:
  - El id de la persona va como dato asociado: un secreto copiado a la fila de
    otra no descifra.
  - En producción no arranca sin la clave, ni con la de desarrollo, como
    `JWT_SECRET`.
  - Cambiar la clave deja inservibles los segundos factores ya activados.
- **Diez códigos de recuperación** de un solo uso, de la forma `abcde-23456`:
  - Unos 49 bits de azar cada uno, sin caracteres que se confundan al copiarlos
    a mano.
  - Se guardan con SHA-256: con ese azar, un hash lento no añade nada.
- No hay SMS ni correo como segundo factor: el correo es justo lo que sirve
  para recuperar la contraseña, y el SMS cuesta dinero y se puede robar
  cambiando la SIM. Las llaves de seguridad (WebAuthn) quedan para más
  adelante.

### Entrar en dos pasos

1. `POST /auth/login` con la contraseña buena. A quien tiene segundo factor no
   se le abre sesión: la respuesta lleva solo un **desafío**. Es un JWT de cinco
   minutos, con su propio `aud` (`nxtime-2fa`), así que no sirve como access
   token ni al revés.
2. `POST /auth/2fa` con el desafío y el código, que puede ser de la aplicación
   o de recuperación. Abre la sesión igual que el login, con las cookies para
   la web.

Detalles de ese segundo paso:

- **Huella:** el desafío lleva una huella de la contraseña y de la fecha de
  activación. Si en esos cinco minutos cambia una de las dos, deja de valer.
- **Límite de intentos propio.** Los fallos de código cuentan con la clave
  `2fa:` + correo, aparte de los de la contraseña. Con la misma clave, acertar
  la contraseña ponía la cuenta a cero, y quien la supiera podía probar códigos
  sin fin.
- **Google y Microsoft cuentan como segundo factor:** lo pone el proveedor. Por
  ahí no se pide código. El kiosco no cambia.

### A quién se le exige

Es opcional para todos, y obligatorio para:

- el **operador de plataforma**;
- el **Responsable del canal de denuncias**;
- los **ADMIN y RRHH** de una empresa que lo exija. Lo decide un ADMIN en los
  ajustes de la empresa, y solo si él ya lo tiene: si no, su propia sesión
  quedaría limitada sin haber entendido por qué.

Quien está obligado y no lo tiene **entra, pero solo puede activarlo**:

- Se decide por sesión y no por persona. El access token lleva si la sesión
  pasó por un segundo factor (claim `mfa`), y el refresh también
  (`refresh_tokens.con_segundo_factor`).
- `SegundoFactorObligatorioFilter` responde 403 con `codigo:
  SEGUNDO_FACTOR_OBLIGATORIO` a todo menos a lo imprescindible: el perfil,
  activarlo, la contraseña y las sesiones, las condiciones y el aviso de
  privacidad.
- La web le enseña solo la pantalla de activarlo. La app le dice que lo active
  en la web: activarlo desde el móvil en el que está la aplicación de
  autenticación es incómodo, y la app solo pide el código al entrar.
- Al activarlo, la sesión desde la que lo hace pasa a contar como sesión con
  segundo factor: acaba de demostrar que tiene el móvil.

### Cambios que avisan, y quién los puede deshacer

- **Activarlo y desactivarlo piden la contraseña y un código.** Una sesión
  robada no basta para quitarlo, ni para ponerlo y dejar fuera a su dueño.
- **Cada cambio manda un correo** a su dueño: activado, desactivado, quitado
  por un administrador, código de recuperación usado o códigos nuevos.
- **Un ADMIN o RRHH se lo quita** a alguien de su empresa que ha perdido el
  móvil y los códigos, con las mismas reglas que dar de baja: no a sí mismo, ni
  a un rol por encima del suyo. Quien se lo exige no se lo puede quitar.
- El operador que lo pierda todo no tiene a nadie por encima: se le quita en la
  base (ver `docs/DESPLIEGUE.md`).

### Contraseñas comunes

- Una contraseña nueva no puede estar entre las **unas diez mil más usadas**
  (`@NoEsComun`, en el registro, al cambiarla y al elegirla con un código).
  Las mayúsculas no cuentan.
- La lista es local (`seguridad/contrasenas-comunes.txt`). Sale de SecLists
  (MIT) y de la lista del NCSC británico (Open Government Licence), con las de
  menos de 8 caracteres fuera, y unas cuantas en español añadidas a mano.
- No se llama a ningún servicio de fuera: mandar la contraseña de alguien a un
  tercero, ni un trozo de su hash, es justo lo que no hay que hacer con ella.
- El mínimo sigue en 8, que es lo que dice el NIST (SP 800-63B) con lista de
  bloqueo, y el máximo en 72 bytes (ADR 034).
- **Hallazgo de paso:** los errores de validación de `@Valid` salían con el
  texto genérico de Spring («Invalid request content.»). El mensaje de cada
  campo no llegaba a nadie, y con esta regla, que solo puede comprobar el
  servidor, la persona no sabría qué cambiar. Ahora `ErroresDeValidacion` los
  pone en `detail`.

### Cerrar todas las sesiones, al momento

`usuarios.sesiones_validas_desde`: el filtro del JWT rechaza los access tokens
emitidos antes. La persona ya se lee de la base en cada petición, así que no
cuesta ninguna consulta más. El token lleva su emisión al milisegundo (claim
`emi`), porque el `iat` va en segundos. Se marca:

- al «cerrar todas las sesiones»;
- al elegir contraseña con un código.

Al cambiar la contraseña con la sesión abierta no se marca: esa sesión sigue
abierta (ADR 034), y las demás caen al renovar.

## Consecuencias

- **`TOTP_CLAVE` hay que crearla en Render antes de desplegar**: sin ella la
  aplicación no arranca. Se genera con `openssl rand -base64 32`.
- **El operador entra, la primera vez, a una pantalla que solo deja activarlo.**
  Hay que tener a mano el móvil con la aplicación de autenticación.
- La app 1.16 no conoce el desafío. Quien tenga el segundo factor activado no
  podrá entrar con ella hasta la 1.17.
- En la demo, Lucía (la operadora de `application-dev.yml`) lo tiene activado
  con un secreto público (`DemoDataSeeder.SECRETO_TOTP_DE_LUCIA`), y los e2e
  calculan su código. Un código gastado en una spec no vale en la siguiente
  dentro de los mismos 30 segundos: la ayuda de los e2e espera al siguiente.
- La activación desde una sesión robada sigue siendo posible si quien la tiene
  sabe también la contraseña. Para eso está el correo de aviso.
