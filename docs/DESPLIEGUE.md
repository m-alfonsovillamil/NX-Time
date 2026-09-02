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
