# 20. Los tokens de la web viven en memoria, y la cookie espera al dominio propio

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

El [ADR 019](019-el-refresh-token-se-hashea-y-se-rota.md) dejó el refresh token
hasheado, rotado y con detección de reutilización, y terminaba diciendo que eso
hacía *defendible* cualquiera de las opciones de almacenamiento en el navegador
—sin elegir ninguna—. Aquí se elige.

El cliente web (React + TypeScript, SPA aparte) recibe un access token de 15
minutos y un refresh de 12 horas. La pregunta es dónde los guarda entre
peticiones, y la respuesta condiciona a qué se parece un XSS en este proyecto.

## Las tres opciones y su precio

| Opción | Qué se lleva un XSS | Qué cuesta |
|---|---|---|
| Refresh en `localStorage` | 12 horas de sesión, y el ladrón renueva desde su máquina | Nada. **Descartada** |
| Cookie `HttpOnly; Secure; SameSite` | Nada directamente: el script no lee la cookie | Lo de abajo |
| **Access y refresh solo en memoria** | Solo lo que alcance la pestaña abierta | Recargar la página cierra la sesión |

La cookie es la mejor de las tres en seguridad, y aun así **hoy no se puede
tener**. No por trabajo: por dominio.

`nxtime-web.onrender.com` y `nxtime-backend.onrender.com` no comparten dominio
padre registrable — `onrender.com` está en la Public Suffix List precisamente
para que un servicio no pueda poner cookies a otro. Eso convierte cada petición
de la web al backend en **cross-site**: `SameSite=Lax` no viaja, y `SameSite=None`
lo bloquean el ITP de Safari, la protección de Firefox y cualquier bloqueador de
terceros. Una cookie que llega a veces es peor que ninguna: la sesión se caería
en unos navegadores y no en otros, y el fallo se diagnosticaría durante días.

La salida limpia es un dominio propio (`app.nxtime.es` y `api.nxtime.es`, ~10 €
al año, admitido en el plan gratuito de Render). No está comprado.

## Decisión

**Access y refresh viven solo en memoria, en un módulo de la SPA.** No pasan por
`localStorage`, `sessionStorage` ni cookie.

La consecuencia visible es que **recargar la página o abrir una pestaña nueva
obliga a volver a entrar**. Se asume, y estas son las razones por las que el
precio es bajo *hoy*:

- Lo que hay son dos pantallas, login y fichar. La molestia es real pero pequeña,
  y con el arranque en frío de Render el primer login ya tarda ~50 s le pongas
  donde le pongas el token.
- No obliga a tocar el backend más allá de lo ya hecho: `allowCredentials` sigue
  en `false` y los tokens siguen viajando en la cabecera `Authorization`.
- Con la rotación del ADR 019, un refresh robado de la memoria de una pestaña
  vale 12 horas *y* deja rastro: en cuanto el legítimo renueva, la reutilización
  revoca la familia entera.

## El paso siguiente, y el error que hay que evitar al darlo

El día que haya dominio propio, el cambio es: refresh en cookie
`HttpOnly; Secure; SameSite=Lax; Path=/auth/refresh`, access en memoria como
ahora.

**Entonces sí hará falta CSRF, y ese es el error clásico.** Hoy no hace falta
porque el token va en una cabecera que un formulario de otro sitio no puede
poner. Una cookie la manda el navegador sola, así que `POST /auth/refresh` desde
una página ajena funcionaría; con `SameSite=Lax` no es explotable en la práctica,
pero apoyar toda la defensa en un atributo que algún navegador podría relajar es
exactamente el razonamiento que envejece mal. Cuando se ponga la cookie hay que
poner también el token CSRF, en el mismo PR.

Y hará falta `setAllowCredentials(true)` en CORS, que **obliga** a enumerar los
orígenes: con credenciales, el comodín `*` deja de ser válido.

## Consecuencias

- La web no puede ofrecer "mantener la sesión iniciada". No se promete.
- El refresco de 401 se serializa con **una única promesa en vuelo**, igual que
  el mutex que la Fase A12 puso en Android y por el mismo motivo: con rotación,
  dos refrescos en paralelo son una reutilización y cierran la sesión. En la web
  no es una optimización, es un requisito.
- `CORS_ALLOWED_ORIGINS` sigue teniendo que ponerse a mano en el panel de Render
  cuando la web se despliegue. Sin eso la web compila, despliega y no funciona,
  con un error de CORS en consola y **nada** en los logs del backend. Por eso la
  Fase A10 añadió un WARN al arrancar si está vacío en producción.
- Esta decisión se revisa cuando se compre el dominio, no antes.
