# Despliegue en Render + Neon

Guía para poner NX Time en producción. Los valores de base de datos ya están
rellenados con el proyecto Neon **NX Time** (`raspy-sunset-60828363`, región
`aws-eu-central-1`).

> **Este fichero no contiene ni contraseñas ni el endpoint real**, porque el
> repositorio es público. Donde ponga `<TU-ENDPOINT>` o `<CONTRASEÑA…>`, saca el
> valor de tu `.env.local` (que está fuera del repositorio a propósito) o del
> panel de Neon:
>
> ```bash
> npx neon@latest connection-string production
> ```
>
> Publicar el host y los usuarios no es una brecha —la contraseña sigue siendo
> secreta y la conexión exige TLS— pero es exposición innecesaria: le ahorra a
> un atacante la mitad del trabajo, sobre todo si el proyecto no tiene lista
> blanca de IPs configurada, que es lo que trae Neon por defecto.

## 1. Variables de entorno en Render

En el panel del servicio, sección *Environment*. Son las que `render.yaml` marca
como `sync: false`.

### Base de datos (la aplicación)

Neon entrega la URL en formato **libpq** (`postgresql://usuario:clave@host/base`),
que **Spring no entiende**: necesita formato JDBC y las credenciales por separado.
La conversión ya está hecha:

| Variable | Valor |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<TU-ENDPOINT>-pooler.<region>.aws.neon.tech/neondb?channel_binding=require&sslmode=require` |
| `DATABASE_USERNAME` | `nxtime_app` |
| `DATABASE_PASSWORD` | `<CONTRASEÑA DE nxtime_app>` |

**Por qué la URL con `-pooler`:** la documentación de Neon la recomienda para
aplicaciones web con peticiones concurrentes.

**Por qué `nxtime_app` y no el dueño:** es el rol de mínimo privilegio de la
Fase 8. El dueño (`neondb_owner`) solo lo usa Flyway.

### Migraciones (Flyway)

Neon indica explícitamente que las migraciones de esquema deben ir por conexión
**directa**, no por el pooler: PgBouncer en modo transacción no soporta `SET`, que
las herramientas de migración necesitan.

| Variable | Valor |
|---|---|
| `SPRING_FLYWAY_URL` | `jdbc:postgresql://<TU-ENDPOINT>.<region>.aws.neon.tech/neondb?channel_binding=require&sslmode=require` |
| `SPRING_FLYWAY_USER` | `neondb_owner` |
| `SPRING_FLYWAY_PASSWORD` | `<CONTRASEÑA DE neondb_owner>` |

Fíjate en que este host **no** lleva `-pooler`. Que Flyway y la aplicación usen
roles distintos no es casualidad: es lo que hace que el `REVOKE` sobre la tabla
de auditoría signifique algo (ver Fase 8).

### Seguridad

| Variable | Valor |
|---|---|
| `JWT_SECRET` | genera uno nuevo (ver abajo) |

**Obligatoria: sin ella la aplicación no arranca**, y es a propósito.
`application.yml` trae una clave por defecto para desarrollo que es **pública**
(está en este repositorio); si producción la heredase, cualquiera podría firmar
tokens válidos y suplantar a cualquier usuario.

```bash
openssl rand -base64 64 | tr -d '

'
```

El `tr -d '

'` importa: en Windows `openssl` parte la salida en líneas y deja
retornos de carro, que no son base64 válidos. La aplicación falla al arrancar con
`Illegal base64 character`.

### Correo

| Variable | Valor |
|---|---|
| `MAIL_HOST` / `MAIL_PORT` | los de tu proveedor SMTP (Brevo, Resend…) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | credenciales del proveedor |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `true` |
| `MAIL_FROM` | remitente en un **dominio verificado**, o los correos irán a spam |

### CORS

| Variable | Valor |
|---|---|
| `CORS_ALLOWED_ORIGINS` | vacío mientras no haya cliente web |

La app Android no manda cabecera `Origin`, así que CORS no le afecta.

## 2. Crear el servicio en Render

1. *New* → *Blueprint*, apuntando a este repositorio: Render lee `render.yaml`.
2. Rellenar las variables de arriba cuando las pida.
3. El health check ya apunta a `/actuator/health`.

## 3. Compilar Android contra producción

La URL del backend sale de `BuildConfig` según el sabor de compilación:

```bash
./gradlew :nx-time-frontend-android:assembleProdRelease   -Pnxtime.prod.url=https://TU-SERVICIO.onrender.com/
```

Sin `-Pnxtime.prod.url` queda un marcador inválido a propósito: así un APK mal
construido falla en vez de apuntar en silencio a un sitio equivocado.

### Firma del APK

Android **no instala un APK sin firmar**, y el repositorio es público, así que
la clave no puede vivir en él. Se genera una vez:

```bash
keytool -genkeypair -keystore nxtime-release.jks -storetype PKCS12 \
  -alias nxtime -keyalg RSA -keysize 4096 -validity 10000
```

y se declara, **fuera del repositorio**, en `~/.gradle/gradle.properties`:

```properties
nxtime.release.storeFile=C:/ruta/fuera/del/repo/nxtime-release.jks
nxtime.release.storePassword=...
nxtime.release.keyAlias=nxtime
nxtime.release.keyPassword=...
nxtime.prod.url=https://nxtime-backend.onrender.com/
```

Con eso, `assembleProdRelease` ya no necesita `-P`. Sin esas propiedades el
release compila igual, pero sin firmar (es lo que pasa en el CI).

> 🚨 **Copia de seguridad del `.jks` y de su contraseña.** Si se pierde
> cualquiera de los dos, la app instalada **no se puede volver a actualizar
> nunca**: Android exige que cada versión venga firmada con la misma clave.

## 4. El arranque en frío

- **Render duerme el servicio** tras 15 minutos sin tráfico. Medido el
  09/09/2026: `/actuator/health` en caliente 0,3 s, login en caliente 0,8 s y
  **login en frío 160 s**. El 11/09/2026, desde el APK de release, el frío
  **pasó de 180 s** (entre 180 y ~210 s): no es una cifra fija.
- **Neon suspende la base de datos** por inactividad. Por eso
  `application-prod.yml` sube el `connection-timeout` de HikariCP a 45 s.

Se ataca por dos lados, y hacen falta los dos:

1. **`.github/workflows/keep-alive.yml`** llama a `/actuator/health` cada 10
   minutos. Tres pegas: GitHub retrasa los `schedule` con carga (a veces más de
   15 minutos), **desactiva los workflows programados tras 60 días sin actividad
   en el repositorio**, y tener el servicio despierto 24/7 gasta ~730 de las
   **750 horas** mensuales de Render — no cabe un segundo servicio gratuito en
   la misma cuenta.
2. **La app espera al servidor cuando puede estar dormido**
   (`ArranqueEnFrio.kt`): si no ha tenido respuesta en 10 minutos, la petición
   espera hasta 300 s y, pasados 3 s, la app avisa de que el servidor está
   despertando. Si hay respuesta reciente, rigen tiempos cortos (15 s de
   conexión, 30 s de lectura). Antes usaba los 10 s por defecto de OkHttp, así
   que la primera petición de cada mañana fallaba siempre.

## 5. Copias de seguridad

**Neon no es una copia de seguridad en el plan gratuito.** Comprobado el
11/09/2026: la restauración instantánea solo llega **6 horas** atrás, hay **un
único snapshot manual** y **no hay copias programadas**. Sirve para "acabo de
romper algo", no para "esto se borró la semana pasada" ni para perder el acceso
a la cuenta.

Por eso hay dos capas:

| Capa | Cubre | Dónde |
|---|---|---|
| Restauración instantánea de Neon | las últimas 6 horas | consola de Neon → *Branches* → `production` → *Restore* (sobrescribe la rama y guarda el estado anterior en una rama de respaldo) |
| Copia diaria propia | 30 días, y nunca menos de 7 copias | `scripts/copia-neon.ps1` → `OneDrive\Copias NX Time` |

### La copia diaria

`scripts/copia-neon.ps1` hace un `pg_dump` en formato custom con la conexión
**directa** (`DATABASE_URL_UNPOOLED` de `.env.local`: PgBouncer no admite el
estado de sesión que usa `pg_dump`), comprueba que el fichero se puede leer y
trae los datos de las tablas clave **antes** de darlo por bueno, y aplica la
retención. Deja `copias.log` en la carpeta de destino y, si falla, un
`ULTIMA-COPIA-FALLIDA.txt` que desaparece con la siguiente copia buena.

Corre como tarea programada de Windows, todos los días a las 14:00 y, si el PC
estaba apagado a esa hora, en cuanto se enciende:

```powershell
$script = "C:\ruta\al\repo\scripts\copia-neon.ps1"
Register-ScheduledTask -TaskName "NX Time - copia de Neon" `
  -Action (New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`"") `
  -Trigger (New-ScheduledTaskTrigger -Daily -At 14:00) `
  -Settings (New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries -RunOnlyIfNetworkAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 15))
```

Necesita el cliente de **PostgreSQL 18**: `pg_dump` no puede copiar un servidor
de una versión mayor más nueva que él.

> ⚠️ El script **tiene que guardarse en UTF-8 con BOM**. PowerShell 5.1 lee un
> `.ps1` sin BOM como ANSI, y todas las tildes de los mensajes salían rotas.

### Probar la restauración

Una copia que nunca se ha restaurado no es una copia. `scripts/probar-restauracion.sh`
(Git Bash, con Docker) la restaura en un PostgreSQL 18 desechable en el puerto
5434 — no toca la base de desarrollo — y comprueba:

1. que `pg_restore` termina **sin errores**;
2. las mismas filas por tabla que producción (si se lanza sin argumento, copia
   producción en ese momento y compara);
3. las migraciones de Flyway, todas con éxito;
4. que ninguna secuencia va por detrás de su id máximo;
5. que la auditoría sigue siendo append-only: los dos triggers de V5 **disparan
   de verdad** contra un `DELETE` y un `TRUNCATE`, y `nxtime_app` sigue sin
   `UPDATE` ni `DELETE`.

```bash
scripts/probar-restauracion.sh                                   # copia nueva de producción
scripts/probar-restauracion.sh "/c/Users/.../nxtime-AAAA-MM-DD_HHMMSS.dump"   # una copia hecha
```

**Verificado el 11/09/2026** con las dos formas: `RESTAURACIÓN VERIFICADA`.
Conviene repetirlo de vez en cuando, y siempre después de una migración nueva.

Lo que costó llegar ahí, para no repetirlo:

- La copia nombra roles internos de Neon (`neon_auth`, `neon_superuser` y
  `cloud_admin`, dueño de unos `DEFAULT PRIVILEGES`). Fuera de Neon hay que
  crearlos antes de restaurar, o `pg_restore` da errores que no afectan a los
  datos pero **tapan los que sí importan**.
- `psql` en Windows manda las tildes de una consulta en la codificación de la
  consola: el script fuerza `PGCLIENTENCODING=UTF8`. La primera versión dio las
  secuencias por buenas porque su consulta había muerto por eso y la salida
  vacía parecía "ninguna atrasada". Ahora una consulta fallida cuenta como fallo.

### Restaurar de verdad en Neon

**Sin probar contra Neon** — lo probado es la restauración en PostgreSQL 18
local. Si hiciera falta: crear en la consola una base vacía en la rama, con
`neondb_owner` como propietario, y restaurar con la conexión directa a ella:

```bash
pg_restore --dbname="postgresql://neondb_owner:...@HOST-DIRECTO/nxtime_restaurada?sslmode=require" copia.dump
```

Cabe esperar errores en las líneas de `neon_auth` y `cloud_admin` (el propietario
de la base no puede asignárselas). Después, apuntar `DATABASE_URL` y las
`SPRING_FLYWAY_*` de Render a la base nueva y comprobar con los mismos cinco
puntos de arriba.

## 6. Estado actual

- ✅ Esquema creado en Neon: las 5 migraciones aplicadas (PostgreSQL 18).
- ✅ Rol `nxtime_app` creado y garantía append-only de la auditoría verificada
  contra Neon (ver `V5__audit_append_only_trigger.sql`).
- ✅ **Servicio creado y funcionando**: https://nxtime-backend.onrender.com

### Verificado en producción

Comprobado de extremo a extremo, no solo el *health check*:

| Prueba | Resultado |
|---|---|
| `GET /actuator/health` | `{"status":"UP","groups":["liveness","readiness"]}` |
| `GET /v3/api-docs` | OpenAPI completo, con `servers` apuntando a la URL pública |
| `POST /auth/register-manager` | `200` — empresa y ADMIN creados |
| `POST /auth/login` | `200` — token firmado, rol `ADMIN` |
| Endpoint protegido sin token | `401` |

Que el registro escriba y la aplicación siga en pie confirma lo que más fácil
sería tener mal: **Flyway migró con `neondb_owner` y la aplicación atiende con
`nxtime_app`**. Si esas dos conexiones se hubieran configurado con el mismo rol,
o la aplicación no habría arrancado (el rol de mínimo privilegio no puede hacer
DDL) o el `REVOKE` sobre la auditoría habría quedado en papel mojado.

### Cuidado al crear el servicio

Render construye el formulario de variables a partir de `render.yaml`. Hasta
[#6](https://github.com/m-alfonsovillamil/NX-Time/pull/6) el blueprint **no
declaraba las tres `SPRING_FLYWAY_*`**, así que no se pedían y había que
añadirlas a mano por el panel; sin ellas `application-prod.yml` las hereda del
datasource y Flyway intenta migrar con el rol de la aplicación, que no puede.

Ojo también con las variables que dejes **vacías**: Render las descarta en vez de
guardarlas en blanco. Eso se llevó por delante `MAIL_FROM`, que no tiene valor
por defecto y sin la cual la aplicación no arranca.
