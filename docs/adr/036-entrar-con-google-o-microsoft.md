# 36. Entrar con Google o con Microsoft: solo para quien ya tiene cuenta

**Estado:** aceptada · **Fecha:** octubre 2026 · Aplazada dos veces (planes del
20/09 y del 30/09) y hecha en el plan del 6/10/2026

## Contexto

Lo primero que pregunta una empresa al evaluar una herramienta así es si su
gente puede entrar con la cuenta del trabajo. Hasta aquí solo había correo y
contraseña (ADR 014), con su JWT propio y su refresh rotado (ADR 019, 030).

Lo que condicionaba el diseño ya estaba decidido antes:

- **La cadena de filtros no guarda sesiones** (`STATELESS`). El SSO necesita
  recordar algo entre mandar a la persona al proveedor y verla volver.
- **El backend no sabe su URL pública.** Detrás del proxy de Render la petición
  llega por HTTP y con otro nombre, y `server.forward-headers-strategy` está
  apagado a propósito: reabriría el `X-Forwarded-For` falsificable que se cerró
  el 19/09/2026.
- **La sesión de la web va en cookies `SameSite=Strict`** (ADR 030), y la de la
  app, en el cuerpo de la respuesta.
- **Un correo es de una sola cuenta**, en toda la instalación (índice único
  sobre `lower(email)`, V17).
- **A la gente la da de alta su empresa** (ADR 014). No hay registro libre de
  empleados.

## Decisión

### Solo entra quien ya tiene cuenta

> Desde el [ADR 038](038-sso-lo-que-faltaba.md) hay una excepción: se puede
> **registrar una empresa** con una de estas cuentas. A los empleados los sigue
> dando de alta su empresa.

El SSO **no crea usuarios ni empresas**. Es otra forma de abrir la sesión de una
cuenta que existe, no otra forma de tenerla. Quien entra con Google y no está
dado de alta vuelve a la pantalla de acceso con «pide a tu empresa que te dé de
alta». La contraseña sigue valiendo, y siempre se puede elegir otra con un
código al correo.

### De la cuenta de fuera a la persona: primero el sujeto, luego el correo

Tabla `identidades_externas` (V39): proveedor, sujeto (el `sub` del ID token),
el correo al vincular y de quién es.

1. **Por el sujeto**, si esa cuenta ya está vinculada. Es lo normal desde la
   segunda vez. No depende del correo: quien cambia de correo en Google sigue
   entrando, y quien hereda un correo viejo de otra persona **no** entra en la
   cuenta de esa persona.
2. **Por el correo**, la primera vez, y solo si el proveedor **garantiza** que
   es suyo. Entonces se vincula.

Una cuenta de fuera es de una sola persona, y una persona tiene como mucho una
de cada proveedor.

### A quién se le cree el correo

Es la decisión de seguridad de todo esto: un correo que el proveedor no
garantiza sería una forma de entrar en la cuenta de otro.

- **Google**: con `email_verified = true`.
- **Microsoft**: su `email` **no está verificado** en una aplicación abierta a
  cualquier organización. El administrador de un inquilino puede ponerle a un
  usuario el correo que quiera (el fallo conocido como «nOAuth»). Se cree solo
  con `xms_edov = true` —un *claim* opcional que hay que pedir al registrar la
  aplicación: «el dueño del dominio del correo está verificado»— o si es una
  cuenta personal, que verifica el propio Microsoft.

Quien tiene una cuenta de Microsoft sin esa garantía no entra por el botón, pero
puede **vincularla desde Ajustes** con su sesión abierta: ahí no hace falta
creerse el correo, porque controla las dos cuentas a la vez.

### El flujo es explícito; la criptografía, de Spring

Flujo de código de OpenID Connect con PKCE (`SsoController`, `OidcClient`). La
firma del ID token, las claves del proveedor (JWKS) y las fechas las valida
`NimbusJwtDecoder`, de `spring-security-oauth2-jose`. Encima se le exige que el
token sea para esta aplicación (`aud`), de quien debe (`iss`) y de esta ida
(`nonce`).

El emisor de Microsoft es uno por organización: se compone con el `tid` del
propio token, exigiendo que sea un GUID. No lo debilita —la firma ya ha dicho
que el token es de Microsoft—; comprueba que es coherente consigo mismo.

**No se usa `oauth2Login()` de Spring**, que era el plan. Ese flujo guarda la
petición de autorización en la sesión del servidor, deduce la URL de vuelta de
la petición y valida un emisor fijo: aquí no hay sesión, la URL no se puede
deducir y el emisor de Microsoft cambia. Habría que sustituir las tres piezas, y
la primera, por una cookie con un objeto de Spring serializado dentro. Se queda
lo que no hay que reescribir: validar el token.

### Lo que se recuerda entre la ida y la vuelta va en una cookie firmada

`nx_sso`: el `state`, el `nonce`, el verificador de PKCE y para qué era la ida.
`HttpOnly`, `Secure`, diez minutos, solo en `/auth/sso`, y se borra al volver.

- **Firmada** con HMAC-SHA256 y una clave *derivada* de la del JWT: no se puede
  escribir ni retocar desde fuera, y no se confunde con un token de acceso.
- **`SameSite=Lax`, no `Strict`**: la vuelta empieza en el proveedor, y con
  `Strict` el navegador no la traería. Por lo mismo se le pide a Microsoft que
  vuelva por la URL (`response_mode=query`) y no con un formulario.

### La URL pública se escribe

`SSO_URL_PUBLICA` (la de la API) y `SSO_URL_WEB`. La URL de vuelta que se
registra en el proveedor tiene que coincidir letra a letra, y el backend no la
puede deducir. Sin ellas, o sin las credenciales de un proveedor, ese proveedor
**no existe**: no sale en `/auth/sso/proveedores`, no hay botón y sus rutas
responden que no está disponible. Desplegar antes de dar de alta nada no rompe.

### Al volver

- **La web**: las mismas cookies que tras un login, y de vuelta a la web, que
  arranca, ve que hay sesión que recuperar y pide su access token. La sesión
  sale del mismo sitio que la del login (`AuthService.abrirSesion`): un solo
  lugar que emite sesiones, para que no acaben siendo distintas.
- **La app**: vuelve por `nxtime://sso` con un **código**, no con la sesión. Esa
  URL la ve el sistema y cualquier app puede declarar que la atiende. El código
  vale una vez y un minuto, y va atado a un secreto de la app: al empezar manda
  el SHA-256 de un valor aleatorio, y al canjear presenta el valor. Quien se
  quede con el código no tiene con qué canjearlo, y un intento fallido lo quema.
  Los códigos viven en memoria: duran un minuto y hay una sola instancia.
- **Vincular**: quién vincula lo dice la cookie del refresh, que es `Strict`:
  solo viaja si la navegación sale de la propia web. No abre otra sesión.

### Los errores son una redirección

Las rutas de ida y vuelta las pide el navegador navegando; un `ProblemDetail`
sería una pantalla en blanco con texto. Se vuelve a la web (o a la app) con el
motivo en la URL, y es allí donde se explica.

A quien ha demostrado que el correo es suyo se le dice la verdad —«no hay cuenta
con ese correo»—: no es enumerar cuentas, es contarle algo de su propio correo.
A quien no lo ha demostrado no se le dice nada del correo.

## Consecuencias

- **Hay que dar de alta la aplicación en dos consolas** (Google Cloud y
  Microsoft Entra) y poner seis variables en Render. Los pasos están en
  `docs/DESPLIEGUE.md`. En Microsoft hay que pedir el *claim* `xms_edov`: sin
  él, solo entran por el botón las cuentas personales.
- **La app tiene que abrir el navegador en el dominio público de la API**
  (`api.nxtime-web.com`), no en el de Render que usa para todo lo demás: la
  cookie de la ida solo vuelve al mismo nombre. Por eso `/auth/sso/proveedores`
  da la URL de inicio completa.
- **El límite de intentos por IP cuenta las vueltas** (10 por minuto, con el
  login). En una oficina con una sola IP, once personas entrando en el mismo
  minuto verían a la undécima esperar. Es el mismo límite que ya tenía el login.
- Un SSO con el correo garantizado **confirma el correo** de quien registró su
  empresa y no había canjeado el código (V37): demuestra lo mismo.
- Las cuentas vinculadas son dato personal: se borran con el borrado de datos
  (`PersonalDataEraser`). No salían en la exportación de mis datos: ya salen
  ([ADR 038](038-sso-lo-que-faltaba.md)).
- Reiniciar el backend en el minuto en que alguien vuelve del navegador a la app
  le hace repetir el botón.

## Alternativas descartadas

- **Crear la cuenta al entrar** (*just-in-time*). Habría que decidir a qué
  empresa pertenece cada correo, con qué rol y quién lo aprueba. Hoy eso lo
  decide quien da de alta.
- **Que una empresa exija SSO y apague las contraseñas.** Tiene sentido y no
  está hecho: pide decidir qué pasa con el código de recuperación y con el
  kiosco. Queda para después.
- **Vincular siempre por el correo, sin tabla.** Más simple, y roto: el correo
  cambia de manos, y en Microsoft ni siquiera es de fiar.
- **Guardar el estado de la ida en la base.** Una tabla y dos escrituras por
  cada inicio de sesión para algo que vive diez minutos y que el navegador puede
  llevar firmado.
- **Google Identity Services en la app** (el selector de cuentas nativo). Solo
  resuelve Google, y obliga a otro flujo distinto para Microsoft.
