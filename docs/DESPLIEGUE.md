# Despliegue en Render + Neon

Guía para poner NX Time en producción. Los valores de base de datos ya están
rellenados con el proyecto Neon **NX Time** (`raspy-sunset-60828363`, región
`aws-eu-central-1`).

> **Este fichero NO contiene contraseñas.** Donde ponga `<CONTRASEÑA…>`, cópiala
> del panel de Neon o de tu `.env.local` (que está fuera del repositorio a
> propósito: este repositorio es público).

## 1. Variables de entorno en Render

En el panel del servicio, sección *Environment*. Son las que `render.yaml` marca
como `sync: false`.

### Base de datos (la aplicación)

Neon entrega la URL en formato **libpq** (`postgresql://usuario:clave@host/base`),
que **Spring no entiende**: necesita formato JDBC y las credenciales por separado.
La conversión ya está hecha:

| Variable | Valor |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://ep-rapid-resonance-b2uy1dd4-pooler.c-6.eu-central-1.aws.neon.tech/neondb?channel_binding=require&sslmode=require` |
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
| `SPRING_FLYWAY_URL` | `jdbc:postgresql://ep-rapid-resonance-b2uy1dd4.c-6.eu-central-1.aws.neon.tech/neondb?channel_binding=require&sslmode=require` |
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

## 4. Dos avisos del plan gratuito

- **Render duerme el servicio** tras 15 minutos sin tráfico: la primera petición
  tarda unos 50 segundos. Conviene saberlo antes de enseñarlo en una entrevista.
- **Neon suspende la base de datos** por inactividad. Por eso
  `application-prod.yml` sube el `connection-timeout` de HikariCP a 45 s.

Un cron gratuito (cron-job.org, UptimeRobot) llamando a `/actuator/health` cada
10 minutos mantiene ambos despiertos.

## 5. Estado actual

- ✅ Esquema creado en Neon: las 5 migraciones aplicadas (PostgreSQL 18).
- ✅ Rol `nxtime_app` creado y garantía append-only de la auditoría verificada
  contra Neon (ver `V5__audit_append_only_trigger.sql`).
- ⬜ Servicio en Render: pendiente de crear.
