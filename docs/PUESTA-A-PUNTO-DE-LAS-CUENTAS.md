# Puesta a punto de las cuentas: vigilancia y secretos

Lo que queda por hacer en los paneles (Render, Neon, GitHub, Cloudflare…) antes
de que NX Time lo use una empresa real. Lo hace quien tiene las cuentas, no el
código. Paso a paso y en orden.

> **Antes de empezar, el despliegue tiene que estar hecho** (los deploy hooks y
> «Desplegar» → «ahora», en `DESPLIEGUE.md`, «Desplegar solo de noche»).
> Cambiar una variable en Render vuelve a desplegar, y lo que se despliega es
> `main`: si `main` va por delante de producción, cambiar una contraseña sacaría
> también todo lo que estaba pendiente.

| Parte | Tiempo | Cuándo |
|---|---|---|
| 1. Vigilancia externa (UptimeRobot) | Hecho (7/11) | — |
| 2. Sacar los `.env` de OneDrive | 10 min | Antes que el 3 |
| 3. Segundo factor en todas las cuentas | 30 min | **Antes** de rotar: que los secretos nuevos caigan en cuentas protegidas |
| 4. Rotar los secretos, de uno en uno | 45 min | Con calma, comprobando después de cada uno |
| 5. Conexión a Neon con `verify-full` | 5 min | Con la 4 (también toca la `DATABASE_URL`) |
| 6. Proteger `main` en GitHub | Hecho (6/11) | — |
| 7. Neon, Firebase, Sentry y Cloudflare | 30 min | Cuando sea |

Después de **cada** cambio en Render, comprobar que sigue vivo:

```powershell
(Invoke-WebRequest -UseBasicParsing https://api.nxtime-web.com/actuator/health).Content
# {"groups":["liveness","readiness"],"status":"UP"}
```

Si un despliegue no arranca, Render deja funcionando el anterior: no se cae
nada, pero el cambio no se ha aplicado. Mirar el registro del despliegue en
Render.

---

## 1. Vigilancia externa (UptimeRobot)

Sentry avisa de los errores, y GitHub de las tareas nocturnas que fallan. Nada
avisa de que el servicio entero esté caído. Esto sí.

1. Crear cuenta en uptimerobot.com, con segundo factor. Antes, mirar en sus
   condiciones que el plan gratuito admite uso comercial. Si no, Better Stack
   tiene un plan gratuito equivalente y los pasos son los mismos.
2. *New monitor* → **Keyword**:
   - URL: `https://api.nxtime-web.com/actuator/health`
   - palabra: `UP`, y avisar **cuando no esté**;
   - cada 5 minutos.

   Keyword y no HTTP: un 200 con `"status":"DOWN"` (sin base de datos) también
   es una caída.
3. *New monitor* → **HTTP(s)**:
   - URL: `https://nxtime-web.com/`
   - cada 5 minutos.
4. *Alert contacts*: el correo, y la app del móvil si se quiere.
5. Opcional: una *Status page* pública con los dos monitores, para dar a las
   empresas.

**Hecho el 7/11/2026** con los dos monitores, cada 5 minutos y con aviso por
correo. En el de la salud, la palabra es `"status":"UP"` entera y con
mayúsculas exactas, para no fiarse de que `UP` salga en otro sitio.

Un aviso de caída de un minuto que se arregla solo suele ser un arranque en frío
de Render (`DESPLIEGUE.md`, apartado 4). Si se repite, es el momento del plan de
pago.

## 2. Sacar los `.env` de OneDrive

`.env.local`, `.env.local.bak` y `.env.render` tienen contraseñas en claro y
OneDrive los sube a la nube de Microsoft. Por eso se rotan después (parte 4): no
se sabe quién ha podido verlos.

1. Crear una carpeta **fuera** de OneDrive, por ejemplo
   `C:\Users\<tú>\NX Time - secretos`.
2. Mover allí `.env.local`.
3. Borrar `.env.local.bak` y `.env.render`: lo que tienen está en `.env.local`
   o en Render.
4. Decir a los scripts dónde está, con una variable de usuario:

   ```powershell
   [Environment]::SetEnvironmentVariable('NXTIME_ENV', 'C:\Users\<tú>\NX Time - secretos\.env.local', 'User')
   ```

   La leen `scripts/copia-neon.ps1` (la copia del PC) y
   `scripts/probar-restauracion.sh`. Cerrar y abrir la terminal para que la vean.
5. Vaciar la papelera de OneDrive en la web (onedrive.live.com → Papelera de
   reciclaje), o los ficheros seguirán allí 30 días.
6. Mejor aún: guardar cada valor también en el gestor de contraseñas, junto a
   la frase de las copias.

## 3. Segundo factor en todas las cuentas

Con una app de autenticación (Google Authenticator, 1Password, Bitwarden…), no
por SMS. Guardar los **códigos de recuperación** de cada una en el gestor de
contraseñas.

| Cuenta | Dónde |
|---|---|
| GitHub | *Settings* → *Password and authentication* |
| Render | *Account Settings* → *Two-factor authentication* |
| Neon | *Account settings* → *Security* (si se entra con Google o GitHub, protege esa cuenta) |
| Cloudflare | *My Profile* → *Authentication* |
| Brevo | *My profile* → *Security* |
| Google (Firebase y Play) | myaccount.google.com → *Seguridad* → *Verificación en dos pasos* |
| Sentry | *User settings* → *Security* |

## 4. Rotar los secretos, de uno en uno

La tabla de cómo se rota cada uno está en
`docs/cumplimiento/respuesta-a-incidentes.md`, apartado 2. Aquí va el orden y
lo que hay que tocar después de cada uno.

Para generar un secreto en Windows (en vez de `openssl`):

```powershell
$b = New-Object byte[] 64; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

1. **Lo que no se usa, fuera**: el secreto `NEON_API_KEY` de GitHub (*Settings*
   → *Secrets* → *Actions*) y el token de Neon AI Gateway de `.env.local`
   (revocarlo en Neon). Ningún workflow ni el código los usa.
2. **`JWT_SECRET`** en Render → *Save and deploy*. Todas las sesiones se cierran
   en 15 minutos como mucho: hacerlo a una hora tranquila.
3. **Contraseña de `nxtime_app`**: Neon → *Roles* → *Reset password*. Después:
   - Render → `DATABASE_PASSWORD`;
   - `.env.local` → `PGPASSWORD`.
4. **Contraseña de `neondb_owner`**: Neon → *Roles* → *Reset password*. Después:
   - Render → `SPRING_FLYWAY_PASSWORD`;
   - GitHub → secreto `COPIA_DATABASE_URL` (lleva la contraseña dentro);
   - `.env.local` → `DATABASE_URL` y `DATABASE_URL_UNPOOLED`.

   Luego, *Actions* → «Copia diaria» → *Run workflow*: tiene que acabar en verde.
5. **Clave SMTP de Brevo**: Brevo → *SMTP & API* → nueva clave → Render
   `MAIL_PASSWORD` → borrar la vieja en Brevo. Probar con un «He olvidado mi
   contraseña» a una cuenta propia, y mirar el registro de Brevo si no llega.
6. **Clave de la cuenta de servicio de Firebase**: Google Cloud → *IAM* →
   *Cuentas de servicio* → nueva clave JSON → Render `FIREBASE_CREDENTIALS_JSON`
   → borrar la vieja. Probar con una notificación al móvil.
7. **Secreto OAuth de Google**, solo si llegó a crearse.
8. **Contraseña de la cuenta de demostración**: desde la propia cuenta.

## 5. Conexión a Neon con `verify-full`

Hoy la conexión va cifrada pero con `sslmode=require`, que **no comprueba el
certificado**: se probó que con un almacén de confianza vacío entra igual. La
cadena buena está probada contra Neon y con la imagen de Java del `Dockerfile`
(`DESPLIEGUE.md`, «Base de datos»).

En Render, cambiar **solo lo que va detrás del `?`**, dejando el host igual:

| Variable | Termina en |
|---|---|
| `DATABASE_URL` | `?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require` |
| `SPRING_FLYWAY_URL` | lo mismo |

Las dos a la vez, y *Save and deploy*. Comprobar la salud.

En GitHub, el secreto `COPIA_DATABASE_URL` (es `pg_dump`, no Java: otra forma):

```
postgresql://neondb_owner:<contraseña>@<host directo>/neondb?sslmode=verify-full&sslrootcert=system&channel_binding=require
```

Y lanzar «Copia diaria» a mano para ver que sigue en verde.

## 6. Proteger `main` en GitHub

> **Hecho el 6/11/2026** con la API (`gh api .../rulesets`): la regla `main`,
> activa, con lo de abajo. Se ve y se cambia en *Settings* → *Rules* →
> *Rulesets*. Si alguna vez hace falta un arreglo urgente sin esperar al CI, se
> desactiva ahí un momento, y se vuelve a activar después.

*Settings* → *Rules* → *Rulesets* → *New branch ruleset*:

- nombre: `main`; *Enforcement*: **Active**; *Target*: la rama por defecto;
- **Restrict deletions** y **Block force pushes**;
- **Require a pull request before merging**, con 0 aprobaciones (no hay nadie
  más que apruebe);
- **Require status checks to pass**: `Build y tests` y `Build Android`.

  Solo esas dos, que corren en todos los PR. Las de la web y Playwright solo
  corren si cambian ciertas rutas: exigirlas dejaría bloqueado cualquier PR que
  no las dispare.

«Desplegar» mueve la etiqueta `desplegado`, no `main`: la protección no le
afecta.

## 7. Neon, Firebase, Sentry y Cloudflare

**Neon**
- `nxtime_app` es miembro de `neon_superuser` y **no se puede quitar**: Neon lo
  hace con todos los roles. Lo compensan los triggers de solo inserción, y los
  diez estaban activos en producción el 6/11/2026. No hay que hacer nada.
- Mirar en *Settings* cuántas horas atrás deja volver el plan (*restore window*).
  El gratuito, 6 horas: por eso hay copias diarias.
- Lista blanca de IPs: **no**, de momento. La copia diaria sale de GitHub
  Actions, que no tiene IP fija, y Render gratuito tampoco.

**Firebase** (Google Cloud → *APIs y servicios* → *Credenciales*):
- la clave de Android: *Aplicaciones de Android*, paquete `com.nxtime.app` y la
  huella SHA-1 de la clave de firma (`keytool -list -v -keystore <el .jks>`);
- la clave de la web: *Sitios web*, `https://nxtime-web.com/*`;
- en las dos, *Restringir clave* a las API de Firebase que salen marcadas
  (Installations y Cloud Messaging).

Si las notificaciones dejan de llegar justo después, la restricción está mal:
quitarla, y volver a probar.

**Sentry**
- La región: el DSN tiene que llevar `.de.` (`…ingest.de.sentry.io`). Si no,
  la organización está en EE. UU., y hay que crearla de nuevo en la UE.
- *Settings* → *Security & Privacy*: **Data Scrubber** activado y **Prevent
  Storing of IP Addresses** activado.

**Render**
- En la ficha de `nxtime-backend`, comprobar que la región es **Frankfurt**,
  como dice `render.yaml`. La región no se puede cambiar una vez creado el
  servicio, y el DNS de Render apunta a un nombre `us-west1`. El tiempo de
  respuesta medido (unos 75 ms desde España) encaja con Frankfurt, pero hay que
  verlo en el panel: `docs/cumplimiento/transferencias.md` promete que los
  datos están en la UE.

**Cloudflare** (el dominio `nxtime-web.com`):
- **DNSSEC**: *DNS* → *Settings* → *Enable DNSSEC*. Si el dominio no está
  registrado en Cloudflare, copiar el registro DS que enseña en el registrador.
- **CAA**: hoy los certificados los firma Google Trust Services, y Render
  también usa Let's Encrypt. Dos registros CAA en `nxtime-web.com`:
  - `0 issue "pki.goog"`
  - `0 issue "letsencrypt.org"`

  Si algún día falla la renovación de un certificado en Render, lo primero que
  hay que mirar son estos registros.
- **Ningún registro colgado**: un CNAME que apunte a un servicio que ya no
  existe se lo puede quedar otro. Repasar la lista y borrar lo que no se use.
- Las dos direcciones de destino añadidas por error en *Email Routing*: borrarlas.

---

Al terminar, apuntar la fecha en la tabla de `docs/cumplimiento/medidas-de-seguridad.md`.
