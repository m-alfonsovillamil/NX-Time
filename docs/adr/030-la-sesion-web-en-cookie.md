# 30. La sesión de la web va en una cookie, con dominio propio y CSRF de doble envío

**Estado:** aceptada · **Fecha:** septiembre 2026 · **Sustituye** al
[ADR 020](020-tokens-en-el-navegador.md)

## Contexto

El [ADR 020](020-tokens-en-el-navegador.md) dejó los dos tokens de la web en
memoria porque la cookie `HttpOnly`, que era la mejor opción, no se podía tener:
la web (`nxtime-web.onrender.com`) y la API (`nxtime-backend.onrender.com`) eran
dos sitios distintos, porque `onrender.com` está en la Public Suffix List, y una
cookie `SameSite` no viajaba entre ellas. El precio era que **recargar la página
cerraba la sesión**. Con dos pantallas se toleraba; con las treinta del
[ADR 029](029-la-web-alcanza-a-la-app.md), no.

El ADR 020 terminaba con la condición para revisarlo: comprar un dominio. Se ha
comprado `nxtime-web.com`.

## Decisión

### Un dominio, dos subdominios del mismo sitio

- `nxtime-web.com` → la web (static site de Render). `www.` redirige aquí.
- `api.nxtime-web.com` → el backend.

Los dos cuelgan de `nxtime-web.com`, así que son **el mismo sitio** y una
cookie `SameSite=Strict` viaja en un `fetch` de uno a otro con
`credentials: 'include'`. Quien abra la web por `nxtime-web.onrender.com`, que
sigue existiendo, es redirigido a `nxtime-web.com` (`VITE_URL_CANONICA`): desde
allí la cookie no funcionaría y el CORS no lo admite.

### El refresh, en una cookie que JavaScript no ve

El login con `origen: WEB` ya no devuelve el refresh en el cuerpo: lo pone en la
cookie `nx_refresh`, `HttpOnly; Secure; SameSite=Strict; Path=/auth`, sin
`Domain` (solo la ve la API) y con la vida del refresh web (12 h, ADR 019). Solo
viaja con `/auth/refresh` y `/auth/logout`. Un XSS en la web ya no se lleva la
sesión: como mucho, el access de 15 minutos que haya en memoria.

El access token sigue **solo en memoria** y en la cabecera `Authorization`. Al
abrir la página, la web pide un access nuevo con la cookie (`restaurarSesion`) y,
si vale, entra sin pasar por el login.

### El CSRF, en el mismo cambio que la cookie

Es el error que el ADR 020 dejó avisado. Una cookie la manda el navegador solo,
así que las dos rutas que la usan exigen además:

1. **Doble envío**: la cabecera `X-CSRF-Token` igual a la cookie `nx_csrf`, que
   el servidor pone **legible** y con `Domain=nxtime-web.com` para que la web,
   en otro subdominio, pueda leerla. Otra web no puede leer esa cookie, así que
   no puede copiarla en la cabecera.
2. **Un `Origin` de la lista blanca**, si viene.

`SameSite=Strict` ya lo pararía en la práctica, pero apoyar toda la defensa en
un atributo que algún navegador podría relajar es el razonamiento que envejece
mal. El CSRF se comprueba **antes** de tocar el token: una petición ajena no
llega ni a rotarlo. El filtro CSRF de Spring sigue desactivado a propósito: el
resto de la API se autentica con una cabecera que otra web no puede poner.

### CORS con credenciales

`allowCredentials(true)`, que obliga a enumerar los orígenes (`*` deja de
valer). En desarrollo, donde la lista es `*`, se mantiene sin credenciales: allí
la web habla con la API por el proxy de Vite, en el mismo origen, y CORS no
interviene.

### Varias pestañas

La cookie es de todo el navegador, y el servidor rota el refresh: dos pestañas
que renuevan a la vez son la reutilización que revoca la familia entera (ADR
019). Cada renovación va dentro de un cerrojo del navegador
(`navigator.locks`), así que van de una en una y la segunda manda la cookie ya
rotada. Al cerrar sesión en una pestaña, un `BroadcastChannel` cierra las demás.

### La app Android no cambia

Con el refresh en el cuerpo, `/auth/refresh` y `/auth/logout` funcionan como
antes: sin cookies, sin CSRF, sin `Origin`. Sigue hablando con
`nxtime-backend.onrender.com`.

## Consecuencias

- **Recargar, o abrir otra pestaña, ya no cierra la sesión.** Dura lo que el
  refresh web: 12 horas desde la última renovación.
- **El dominio es parte de la configuración** en cinco sitios: los dominios de
  Render, `CORS_ALLOWED_ORIGINS`, `COOKIE_DOMAIN`, y en la web `VITE_API_URL`,
  `VITE_URL_CANONICA` y la CSP de `render.yaml`. Cambiarlo más adelante es una
  hora de configuración, pero cierra las sesiones abiertas y, a partir de W9,
  las webs instaladas y sus push.
- **El despliegue tiene un orden**: los dominios de Render verificados, con su
  certificado, *antes* de que este cambio llegue a `main`. Si no, la web apunta
  a una API que aún no responde y nadie puede entrar. Está en `DESPLIEGUE.md`.
- **Hay un 400 más en `/auth/refresh`**: sin cuerpo y sin cookie. Es el mismo
  mensaje que cuando el cuerpo venía en blanco, así que para la app no cambia
  nada.
- **El registro de empresa desde la web** (fase W3) tendrá que pedir también
  `origen: WEB`, o recibiría un refresh de 30 días en el cuerpo. Hoy solo
  existe en la app.
