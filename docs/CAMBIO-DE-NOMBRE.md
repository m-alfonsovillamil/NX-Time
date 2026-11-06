# Cambiar el nombre del producto: el inventario

Qué hay que tocar cuando se elija el nombre nuevo, sacado del código a 6/11/2026.
El nombre «NX Time» ya está cogido. **Se hace antes del piloto y antes de Google
Play**: el `applicationId` de la app no se puede cambiar una vez publicada.

## Lo que decide Miguel antes

1. El nombre. Comprobar que está libre como marca (OEPM y EUIPO), que hay
   dominio y que no hay ya una app con ese nombre en Play.
2. El dominio, comprado. Si es posible, en Cloudflare, como el actual.
3. El `applicationId` nuevo de la app, por ejemplo `es.<dominio>.app`. Es para
   siempre.

## Lo que se cambia en el código (un PR mío)

### Lo que ve la gente

| Dónde | Qué | Cuántos sitios |
|---|---|---|
| `application.yml` → `application.titular.*` | Producto, correos de privacidad y seguridad, web. De aquí sale `titular.ts` para la web (`npm run titular`) | 1 bloque |
| Plantillas de correo (`templates/email/*.html`) | La cabecera y el pie de `layout.html` y el texto de cada correo. Hoy van escritos a mano: **pasarlos a la variable del titular**, así la próxima vez no hay que tocarlos | 33 apariciones |
| Asuntos y textos de correo en Java (`SegundoFactorServiceImpl`, `AccessCodeServiceImpl`, `SsoServiceImpl`…) | Igual: desde el titular | ~15 |
| Nombre en las apps de autenticación (`SegundoFactorServiceImpl.EMISOR`) | El nombre con el que sale el segundo factor en Google Authenticator. Las entradas ya creadas seguirán con el nombre viejo: no pasa nada | 1 |
| Título de la API (`OpenApiConfig`) | | 1 |
| Web: textos (`i18n/es/*.ts`), `index.html`, `manifest.webmanifest`, `sw.js`, `security.txt` | El nombre y el dominio | ~25 |
| App: `strings.xml` (`app_name` y textos), icono (`ic_marca_frente.xml` y lanzador) | | ~16 |
| Textos legales | Salen del titular, pero cambiar de nombre y dominio es **una versión nueva** de las condiciones (`application.legal.version`): se avisa a los ADMIN, que las aceptan otra vez | 1 |
| `README.md`, `SECURITY.md`, guías de `docs/guias/`, `docs/GOOGLE-PLAY.md` | | — |

### La configuración que lleva el dominio

| Dónde | Qué |
|---|---|
| `render.yaml` | Los valores de producción: CORS, dominio de la cookie de sesión (`COOKIE_DOMAIN`), URL de la API para la web, URL pública, `MAIL_FROM` y la CSP. `application-prod.yml` solo lo menciona en comentarios |
| `SsoConfig` y `SesionWeb` | La redirección del SSO y el dominio por defecto de la sesión web |
| `nx-time-frontend-android/build.gradle.kts` | `applicationId` y `WEB_URL` del sabor `prod`. La API la llama por `nxtime-backend.onrender.com`, que no cambia |

Los workflows (keep-alive, tareas nocturnas) llaman a `nxtime-backend.onrender.com`: no cambian.
| `APP_DOWNLOAD_URL` (`application.yml`) | El enlace al APK que va en el correo de alta. Hoy apunta al repositorio de GitHub (`NX-Time.apk`): con Play, al de Play |

## Lo que NO se cambia

Son nombres internos, no los ve nadie, y cambiarlos rompe cosas sin ganar nada:

- **los paquetes de Java y Kotlin** (`com.nxtime.nxtime`, `com.nxtime.app` como
  *namespace*): unos 6.500 sitios, solo en el código;
- **el emisor y el destino del token** (`JwtServiceImpl.EMISOR = "nxtime"`):
  cambiarlos cierra todas las sesiones;
- **las cookies** (`__Secure-nx_refresh`…) y **las claves de `localStorage`**
  (`nx-…`): cambiarlas cierra sesiones y olvida preferencias;
- **el prefijo de las tarjetas del kiosco** (`NXK1.`): cambiarlo invalida las ya
  impresas;
- **la base de datos y sus roles** (`neondb`, `nxtime_app`), los nombres de los
  servicios de Render (`nxtime-backend`) y del cubo de copias: no se ven fuera.

## Lo que hace Miguel fuera del código

En este orden, **después** de que el PR esté listo y **antes** de mergearlo:

1. **Cloudflare**: el dominio nuevo, con los registros para Render (`api.` y la
   raíz). Dejar el viejo apuntando un tiempo y redirigiendo al nuevo.
2. **Render**: *Custom Domains* del dominio nuevo en `nxtime-backend` y
   `nxtime-web`. Esperar a que el certificado esté emitido.
3. **Brevo**: dar de alta el dominio nuevo como remitente, con SPF, DKIM y DMARC
   (paso C2.1 del plan), y cambiar `MAIL_FROM`.
4. **Firebase**: registrar la app Android con el `applicationId` nuevo (y el
   SHA-1 de la firma) y bajar el `google-services.json` nuevo. Restringir la
   clave de API al paquete nuevo. Para la web, añadir el dominio nuevo a los
   dominios autorizados.
5. **SSO** (si ya está encendido): la URL de redirección nueva en Google y en
   Microsoft.
6. **Sentry**: nada obligatorio. Si se quiere, renombrar los proyectos.
7. **GitHub**: renombrar el repositorio, si se quiere. GitHub redirige el nombre
   viejo, pero `APP_DOWNLOAD_URL` conviene cambiarla igual.

Después: mergear, desplegar con «Desplegar» → «ahora» y comprobar el correo, el
login, el SSO y las notificaciones.

## La app con el `applicationId` nuevo

Para Android es **otra app**: no actualiza a la vieja, se instala al lado. Quien
tenga la vieja con fichajes sin enviar tiene que mandarlos antes (abrir la app
con red) y después desinstalarla. Por eso conviene hacerlo **antes de que la
plantilla de IIDD instale nada**.
