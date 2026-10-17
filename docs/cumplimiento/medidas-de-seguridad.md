# Medidas de seguridad (RGPD, art. 32)

Lo que hay a 17/10/2026, y lo que el contrato de encargo promete y todavía
depende de algo pendiente. **El anexo del contrato de encargo (`/legal/encargo`)
no puede prometer más que esta página.** Si una medida cambia, se cambian las
dos y se sube `application.legal.version`.

## Ya en el código

| Medida | Dónde |
|---|---|
| Contraseñas con BCrypt, como mucho 72 bytes | `ApplicationConfig`, `CabeEnBcrypt` |
| Límite por IP (entradas, registro, códigos, consultas públicas) y por cuenta con espera exponencial | `LoginRateLimitFilter`, `LimitadorDeIntentosPorCuenta` |
| Cuenta de baja comprobada después de la contraseña (sin pistas de tiempo) | `ApplicationConfig` (J1) |
| Códigos de un solo uso, 5 intentos y caducidad | `AccessCodeServiceImpl` |
| SSO OpenID Connect con PKCE, nonce y state firmados | `security/sso` (ADR 036) |
| Permisos por authority y aislamiento por empresa en cada consulta | `RoleAuthorities`, servicios |
| Access token de 15 min con `iss`/`aud`; refresh rotado, revocable, con detección de reutilización | `JwtServiceImpl`, `AuthServiceImpl` |
| Sesión web en cookie `HttpOnly`, `Secure`, `SameSite=Strict`, prefijo `__Secure-`, con CSRF de doble envío y Origin | `SesionWeb` (ADR 030) |
| Cabeceras: HSTS, CSP, `Permissions-Policy` sin cámara (salvo kiosco), micrófono ni ubicación | `SecurityConfig` |
| Tope de 1 MB en los cuerpos que no son subidas | `TopeDelCuerpo` |
| Traza de auditoría de solo inserción, encadenada con hash y verificada cada noche | V3, V5, V28, `VerificadorDeAuditoria` |
| Firma mensual con huella, invalidada si algo cambia | V33 |
| Historial del operador de solo inserción | V43 |
| Kiosco: PIN con bloqueo y anulación, tope de fallos por kiosco, tarjeta sin capturas en la app | `IdentificacionEnKiosco`, `FicharEnKioscoScreen` |
| Registros sin correos; Sentry con los correos tapados y sin IP | `LimpiezaDeSentry`, J1 |
| App: sesión cifrada con el almacén de claves, sin copias de seguridad, sin tráfico en claro en producción | ADR 045, `network_security_config` |
| Dependencias vigiladas (Dependabot), CodeQL, acciones y contenedores fijados | `.github/` (J1) |
| Copias diarias cifradas (AES-256), 30 días, con la frase fuera de OneDrive | `scripts/copia-neon.ps1` (J2) |

## Prometido y pendiente (antes de la primera empresa real)

| Medida | Estado | Quién |
|---|---|---|
| Segundo factor en GitHub, Render, Neon, Cloudflare, Brevo, Google/Firebase y Sentry | Pendiente (J2) | Miguel |
| Secretos fuera de OneDrive y rotados | Pendiente (J2) | Miguel |
| La frase de las copias creada y guardada en un gestor de contraseñas; copias viejas sin cifrar borradas | Pendiente (J2) | Miguel |
| Neon: `nxtime_app` fuera de `neon_superuser`, restauración de prueba hecha, lista blanca de IPs | Pendiente (J2) | Miguel |
| Sentry en la región de la UE | Por confirmar (`DESPLIEGUE.md` apunta a `ingest.de`) | Miguel |
| Segundo factor (TOTP) en NX Time, obligatorio para el operador | Plan J, J8 | Código |
| Conservación automática de cada dato | Plan J, J5 | Código |
| Registro de accesos a datos personales de otra persona | Plan J, J5 | Código |
