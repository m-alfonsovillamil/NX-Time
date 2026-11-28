# Cambiar el nombre del producto

El nombre «NX Time» ya está cogido. **Se cambia antes del piloto y antes de
Google Play**: el paquete de la app (`applicationId`) no se puede cambiar una
vez publicada.

Desde el plan P (noviembre de 2026), el nombre, el dominio y el paquete están
cada uno en un solo sitio, y cambiarlos es un comando. El CI lo vigila: el job
«Marca en un solo sitio» (`scripts/dev/comprobar-marca.py`) falla si alguien
vuelve a escribir el nombre o el dominio en un texto que se ve.

## 1. Lo que decide Miguel antes

1. **El nombre** (30 caracteres como mucho: es lo que admite Play). Comprobar
   que está libre como marca (OEPM y EUIPO), que hay dominio y que no hay ya
   una app con ese nombre en Play.
2. **El dominio**, comprado. Si es posible, en Cloudflare, como el actual.
3. **El paquete** de la app, por ejemplo `es.<dominio>.app`. Es para siempre.
4. **Los correos** de privacidad y seguridad (por defecto, `privacidad@` y
   `seguridad@` del dominio nuevo).

## 2. El cambio en el código (un PR)

```bash
python scripts/dev/cambiar-nombre.py --nombre "Horas Claras" \
    --dominio horasclaras.es --paquete es.horasclaras.app --probar   # qué cambiaría
python scripts/dev/cambiar-nombre.py --nombre "Horas Claras" \
    --dominio horasclaras.es --paquete es.horasclaras.app            # cambiarlo
```

Cambia, todo o nada:

| Dónde | Qué |
|---|---|
| `application.yml` → `application.titular` | Nombre, correos y web. De aquí lo toman el backend (asuntos con `{producto}`, push, segundo factor, PDF, Excel, plantillas de correo, título de la API) y la web (`npm run titular` → `titular.ts`, `index.html`, manifest, `sw.js`, `security.txt`) |
| `application.yml` → `application.legal.version` | Una versión nueva de las condiciones: cambian el nombre y los correos de los textos legales, así que los ADMIN de cada empresa las aceptan otra vez al entrar |
| `strings.xml` → entidad `&marca;` | El nombre en la app |
| `build.gradle.kts` → `marcaPaquete`, `marcaHostWeb` | El paquete, la web (`WEB_URL`) y el dominio de App Links |
| `render.yaml` | El dominio en CORS, cookie, SSO, URL de la API y la CSP |
| `assetlinks.json` | El paquete que abre los enlaces de los correos |
| `SECURITY.md` | El correo de seguridad y la web |
| `comprobar-marca.py` | El nombre y el dominio viejos pasan a `RETIRADOS`: que no quede ninguno |

Después, a mano en el mismo PR:

- **El icono**: la «N» es de NX Time. Redibujar `ic_marca_frente.xml` (app) y
  `public/iconos/icono.svg` e `icono-adaptable.svg` (web), y `npm run iconos`,
  que saca los PNG de la web, el icono de 512 px de Play
  (`ic_launcher-playstore.png`) y el gráfico de la ficha (1024×500). Si cambian
  los colores: `Color.kt` → `npm run tokens` y `Marca.java` (ADR 057).
- **`google-services.json`** con el paquete nuevo (paso 4 de abajo): sin él, la
  app no compila.
- `README.md`, `docs/guias/` y `docs/GOOGLE-PLAY.md` (los textos de la ficha
  ya van con `{producto}`).
- Tests: `./gradlew :nx-time-backend:check`, la web (`npm run verificar:generado`,
  `typecheck`, `test`, `build`, `e2e`) y Android (`lintDevDebug`,
  `testDevDebugUnitTest`).

Ensayado el 28/11/2026 con un nombre inventado: la web (500 tests), el backend
y la app (566 tests) pasan, y no queda «NX Time» en nada que se vea.

## 3. Lo que NO se cambia

Son nombres internos, no los ve nadie, y cambiarlos rompe cosas sin ganar nada:

- **los paquetes de Java y Kotlin** (`com.nxtime.nxtime`, `com.nxtime.app` como
  *namespace*): unos 6.500 sitios, solo en el código;
- **el esquema `nxtime://sso`** de la vuelta del SSO a la app;
- **el emisor y el destino del token** (`JwtServiceImpl.EMISOR = "nxtime"`):
  cambiarlos cierra todas las sesiones;
- **las cookies** (`__Secure-nx_refresh`…) y **las claves de `localStorage`**
  (`nx-…`): cambiarlas cierra sesiones y olvida preferencias;
- **el prefijo de las tarjetas del kiosco** (`NXK1.`): cambiarlo invalida las ya
  impresas;
- **la base de datos y sus roles** (`neondb`, `nxtime_app`), los nombres de los
  servicios de Render (`nxtime-backend`, que es también la URL con la que la
  app llama a la API) y del cubo de copias: no se ven fuera;
- **los comentarios, los tests y las migraciones** que nombran NX Time.

## 4. Lo que hace Miguel fuera del código

En este orden, **después** de que el PR esté listo y **antes** de mergearlo:

1. **Cloudflare**: el dominio nuevo, con los registros para Render (`api.` y la
   raíz), DNSSEC, CAA y el Email Routing de `privacidad@` y `seguridad@`. Dejar
   el viejo apuntando un tiempo y redirigiendo al nuevo.
2. **Render**: *Custom Domains* del dominio nuevo en `nxtime-backend` y
   `nxtime-web`. Esperar a que el certificado esté emitido. En el panel,
   `CORS_ALLOWED_ORIGINS` y `MAIL_FROM` (no están en `render.yaml`).
3. **Brevo**: dar de alta el dominio nuevo como remitente, con SPF, DKIM y DMARC
   alineados, y cambiar `MAIL_FROM`.
4. **Firebase**: registrar la app Android con el paquete nuevo (y `.dev`), con
   el SHA-1 de la firma, y bajar el `google-services.json` nuevo. Restringir la
   clave de API al paquete nuevo. Para la web, añadir el dominio nuevo a los
   dominios autorizados.
5. **SSO**: la URL de vuelta nueva en Google y en Microsoft (Entra).
6. **Sentry**: nada obligatorio. Si se quiere, renombrar los proyectos.
7. **GitHub**: renombrar el repositorio, si se quiere. GitHub redirige el nombre
   viejo, pero `APP_DOWNLOAD_URL` conviene cambiarla igual (o apuntarla ya a Play).

Después: mergear, desplegar con «Desplegar» → «ahora» y comprobar el correo, el
login, el SSO, las notificaciones y que un botón de un correo abre la app.

## 5. La app con el paquete nuevo

Para Android es **otra app**: no actualiza a la vieja, se instala al lado. Quien
tenga la vieja con fichajes sin enviar tiene que mandarlos antes (abrir la app
con red) y después desinstalarla. Por eso conviene hacerlo **antes de que la
plantilla de IIDD instale nada**.

Para no depender de que la gente se entere, el backend puede **retirar** el
paquete viejo (ADR 064): con `APP_IDS_RETIRADOS=com.nxtime.app`, la app vieja
manda primero los fichajes pendientes y luego enseña «Esta app ya no se usa» con
el enlace a la nueva.
