# 38. SSO, lo que faltaba: vincular desde la app, registrar una empresa y la exportación

**Estado:** aceptada · **Fecha:** octubre 2026 · Completa el
[ADR 036](036-entrar-con-google-o-microsoft.md)

## Contexto

El ADR 036 dejó entrar con Google o con Microsoft a quien ya tenía cuenta, y
apuntó lo que quedaba fuera:

- vincular una cuenta solo se podía **desde la web**;
- no se podía **registrar una empresa** con una de esas cuentas;
- las cuentas vinculadas **no salían en la exportación** de «mis datos»;
- una empresa no podía **exigir** el SSO y apagar las contraseñas.

Aquí se hacen las tres primeras. La cuarta se aplaza, y se explica por qué.

## Decisión

### Vincular desde la app: en dos pasos, y el segundo lo da la app

En la web, quién vincula lo dice la cookie de la sesión, que es `Strict` y solo
viaja si la navegación sale de la propia web. En la app no hay cookie: el
navegador que abre no sabe quién tiene la sesión.

- La app abre el navegador con `…/iniciar?cliente=app&vincular=1&reto=…`.
- Al volver del proveedor **no se vincula nada**. El servidor guarda un minuto la
  cuenta con la que se ha entrado y devuelve a la app un **código de vínculo**
  (`nxtime://sso?vinculo=…`), atado al mismo reto de PKCE que al entrar.
- La app lo confirma con **su sesión y su verificador** en
  `POST /api/v1/perfil/identidades`. Es ahí donde se decide a quién se vincula:
  a quien presenta el token.

El tramo del navegador no lleva nada que ate la cuenta a una persona. Un enlace
que alguien le mande a otro para que «vincule» no consigue nada: el código
vuelve a la app de la víctima, que no tiene el verificador de esa ida.

Un código de **entrar** no sirve para vincular ni uno de **vincular** para
entrar, y presentarlo donde no es lo gasta. En la app, la vuelta de vincular va
por un flujo aparte del de entrar (`AccesoSso.vueltaDeVinculo`): en uno solo,
una vuelta que nadie recogiera se la encontraría la pantalla de acceso al cerrar
la sesión, y la canjearía como un intento de entrar.

### Registrar una empresa: primero quién eres, después cómo se llama

1. «Registrar con Google» manda el navegador a `…/iniciar?registro=1`.
2. Al volver, con el correo **garantizado por el proveedor**:
   - si ya tiene cuenta, **entra en ella**. Ha demostrado lo mismo que pulsando
     «Entrar con Google», y ofrecerle registrar otra empresa sería un error;
   - si no, el servidor deja una cookie firmada (`nx_sso_alta`, `HttpOnly`,
     quince minutos) con la cuenta del proveedor y su correo, y lo devuelve a
     `/registro?sso=continuar`.
3. La web pregunta con qué cuenta se registra (`GET /auth/sso/registro`), pide
   el nombre de la empresa y el de la persona, y los manda a
   `POST /auth/sso/registro`. Nace la empresa y su ADMIN, con el correo **ya
   confirmado**, la cuenta **vinculada** y la sesión abierta.

No hay contraseña ni código: el código del ADR 034 demostraba que el correo es
de quien registra, y eso lo ha dicho el proveedor. La cuenta nace con una
contraseña inutilizable, como las altas (ADR 014); quien quiera una la elige
con un código al correo.

Por qué así y no de una vez:

- **Los datos no van en la URL.** Empezar es una navegación (GET); el nombre de
  la empresa y el de la persona tendrían que ir como parámetros. Van después, en
  el cuerpo de un POST, como en el registro de siempre.
- **La cookie del alta va firmada con otra clave que la de estado.** La de
  estado se consigue con solo empezar; la del alta vale por «este correo es
  mío». No se pueden confundir.
- **Con el correo sin garantizar no se registra.** Una cuenta de trabajo de
  Microsoft sin `xms_edov` vuelve con el motivo y se le ofrece el registro de
  siempre.
- La cookie se gasta al registrar. Si el nombre de la empresa está cogido,
  sigue valiendo para probar con otro sin volver al proveedor.

**Solo en la web.** La app sigue registrando con correo y contraseña.

### Las cuentas vinculadas salen en «mis datos»

En el JSON, con el proveedor, el identificador que da el proveedor (`sujeto`),
el correo de esa cuenta y las fechas. En el PDF, sin el identificador, que no
le dice nada a quien lo lee.

### Exigir el SSO: aplazado

No se ha hecho, y no por tamaño. Una cuenta de trabajo de Microsoft cuyo
inquilino no manda `xms_edov` **no entra por el botón**: tiene que vincularse
con la sesión abierta, es decir, entrando antes con la contraseña. Si su empresa
apaga las contraseñas, esa persona no tiene forma de entrar la primera vez.

Y el SSO **no se ha probado todavía contra Google ni Microsoft de verdad**: no
se sabe cuántas cuentas reales caen en ese caso. Decidir cómo se sale de ahí —
que la exigencia solo alcance a quien ya tiene una cuenta vinculada, que el
código de alta sirva para vincular, o exigirlo solo con Google— antes de saberlo
sería decidir a ciegas algo que deja a gente fuera.

Dos cosas que ya se saben para cuando se haga:

- El ADMIN tiene que conservar la contraseña: si el proveedor se cae o el
  inquilino se configura mal, alguien tiene que poder entrar a apagarlo.
- El login solo responde **403** por «correo sin confirmar», y la web y la app
  usan ese 403 para pasar a la pantalla del código. Rechazar una contraseña
  buena porque la empresa exige SSO necesita **otro código de estado**.

## Consecuencias

- La app sube a **1.14** (versionCode 15): tarjeta «Cuentas vinculadas» en
  Ajustes.
- Quien tiene una cuenta de Microsoft sin correo garantizado ya puede
  vincularla desde el móvil, sin pasar por la web.
- Registrar una empresa con una cuenta de fuera **no pasa por el código al
  correo**. El límite de intentos por IP alcanza también a
  `POST /auth/sso/registro`, como a `/auth/register-manager`.
- Una empresa registrada así tiene un ADMIN **sin contraseña**. Si pierde el
  acceso a esa cuenta de Google o de Microsoft, la recupera con «¿Has olvidado
  tu contraseña?», que le manda un código a ese mismo correo.
- Hay un tercer sitio que crea empresas (`SsoServiceImpl.registrarEmpresa`),
  además del registro y de la demo.
- Nada de esto se ha probado contra proveedores reales: como el ADR 036, contra
  uno de mentira, en los tests de integración y en un navegador.

## Alternativas descartadas

- **Vincular desde la app con un «pase» que diga quién vincula**, pedido con la
  sesión y llevado en la URL del navegador. Quien se hiciera con el pase podría
  vincular *su* cuenta de Google a la cuenta de otro. Con el código al revés —
  el navegador trae la cuenta, la sesión la pone la app— no hay nada que robar.
- **Registrar mandando el nombre de la empresa en la ida.** Datos personales en
  una URL, y un formulario que se rellena antes de saber si esa cuenta sirve.
- **Sacar el nombre y los apellidos del ID token.** Google los da; Microsoft, no
  siempre. Preguntarlos es una línea de formulario y vale para los dos.
- **Hacer ya «exigir SSO» dejando a todos la contraseña la primera vez.** Es una
  de las salidas posibles, no la única, y la elección depende de lo que diga la
  prueba con proveedores reales.
