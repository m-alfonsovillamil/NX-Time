# 44. Registrar una empresa con Google o Microsoft también desde la app

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

Registrar una empresa con una cuenta de Google o de Microsoft, sin contraseña
ni código, se podía solo desde la web (ADR 038). La app tenía el registro de
siempre, con correo, contraseña y el código que llega por correo, y para
entrar y para vincular ya salía al navegador (ADR 036).

En la web el registro con una cuenta de fuera va en dos pasos y se apoya en una
cookie: al volver del proveedor no se crea nada, queda apuntado quién es en
`nx_sso_alta`, firmada y con un cuarto de hora de vida, y la web pide el nombre
de la empresa y el de la persona. La app no tiene cookies: el navegador en el
que la persona elige su cuenta es otro proceso, y lo único que le llega a la
app es la URL con la que el navegador vuelve (`nxtime://sso?…`).

Esa URL no puede llevar nada que valga por sí solo. La ve el sistema, puede
acabar en un registro, y cualquier otra app puede declarar que atiende
`nxtime://`. Por eso al entrar y al vincular viaja un código de un solo uso y
un minuto, atado a un secreto que solo tiene la app que empezó (PKCE).

## Decisión

Tres pasos, y ninguno nuevo en lo que protege:

1. **La ida, como las otras.** `…/iniciar?cliente=app&registro=1&reto=…`, con
   el reto del verificador que la app se guarda.
2. **La vuelta trae un código de registro**, `nxtime://sso?alta=…`: un solo
   uso, un minuto, y solo vale con el verificador de esa ida. La app lo cambia
   en `POST /auth/sso/registro/canjear` por **un pase** y por la cuenta con la
   que está registrando, para decirla encima del formulario.
3. **El formulario va con el pase**, a `POST /auth/sso/registro/app`. La
   respuesta es la sesión, como la de un login desde la app.

**El pase es la cookie de la web, entregada de otra forma.** Lleva lo mismo
(proveedor, sujeto, correo y cuándo caduca), firmado con la misma clave, y vale
el mismo cuarto de hora. No es una pieza de seguridad nueva: es la que ya
había, en el cuerpo de una respuesta en vez de en una cabecera.

Lo que no es obvio:

- **Hacen falta dos cosas y no una porque duran distinto.** El código que
  vuelve por la URL tiene que morir enseguida, porque es lo que puede caer en
  otras manos. Rellenar el nombre de la empresa lleva más de un minuto. El
  código se cambia nada más volver, en una petición de la app, por algo que
  aguanta el rato del formulario y que nunca ha pasado por la URL.
- **Es otro tipo de código que el de vincular**, aunque los dos guarden lo
  mismo. Presentar uno donde toca el otro lo quema y no da nada: quien empezó a
  vincular no ha pedido registrar una empresa.
- **El pase no se gasta al usarlo.** Si el nombre de la empresa está cogido, la
  respuesta es un 409 y el mismo pase sirve para probar con otro, igual que la
  cookie. Una vez registrada, repetirlo da 409: ya hay cuenta con ese correo.
- **Quien ya tiene cuenta, entra.** Ha demostrado lo mismo que pulsando «Entrar
  con Google», así que vuelve con un código de sesión y no con uno de registro.
  La app lo canjea como siempre.
- **La vuelta la atiende el registro, no el acceso.** Va por un canal aparte en
  la app, como la de vincular. Si el sistema cerró la app mientras la persona
  estaba en el navegador, al volver arranca en el acceso, que al ver que hay
  una vuelta de registro pendiente lleva a esa pantalla sin tocarla.

Las dos rutas nuevas llevan el mismo límite por IP que el registro de la web:
diez por minuto.

## Lo que se ha probado en vivo

En el emulador, con la app de desarrollo contra el backend local y un Google de
mentira (`e2e/proveedor-oidc.mjs`):

- Con una cuenta sin cuenta de NX Time: del botón al navegador, vuelta a la
  app, formulario sin correo ni contraseña con la cuenta dicha arriba, y al
  crear la empresa, «Mi jornada». En la base: la empresa, su ADMIN con el
  correo ya confirmado, la cuenta de Google vinculada y la sesión abierta con
  origen ANDROID.
- Con una cuenta que ya tenía cuenta de NX Time: vuelve y entra.

## Lo que esto no arregla

- **No se ha probado contra Google ni Microsoft de verdad.** El acceso con
  Google sí está probado en producción; este recorrido, solo contra el
  proveedor de mentira.
- **Los códigos siguen en memoria** (ADR 036 y 039). Con una segunda instancia,
  el canje podría caer en la que no emitió el código. Es la misma limitación
  que al entrar y al vincular, y se arregla con lo mismo.
- **Si el sistema cierra la app con el formulario a medias, el pase se pierde**:
  vive en la pantalla, no en las preferencias. Hay que volver a pulsar el
  botón. Guardarlo sería guardar en el teléfono algo que vale por «este correo
  es mío».
- **En un teléfono con las dos variantes de la app instaladas** (la de
  desarrollo y la publicada), Android pregunta con cuál abrir la vuelta. Solo
  pasa en un equipo de desarrollo; elegir la que no empezó no hace nada, porque
  no tiene el verificador.

## Consecuencias

- Lo que se puede hacer en la web con una cuenta de fuera se puede hacer ya en
  la app: entrar, vincular y registrar.
- Dos rutas públicas más bajo `/auth/sso/`, y un modo más (`REGISTRO_APP`) en el
  estado del SSO.
- La app sube a la 1.15 (versionCode 16).
