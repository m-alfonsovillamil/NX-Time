# CLAUDE.md

Lo estable del repositorio para trabajar con Claude Code. El detalle está en el
[README](README.md) y en [`docs/`](docs/); aquí solo va lo que hace falta en
cada sesión.

## Estructura

- `nx-time-backend/`: Spring Boot 4, Java 21, Gradle 9, PostgreSQL y Flyway (`src/main/resources/db/migration/Vn__*.sql`).
- `nx-time-frontend-android/`: Kotlin y Compose, con sabores `dev` y `prod` (`dev` apunta a `10.0.2.2:8080`).
- `nx-time-frontend-web/`: React 19, TypeScript estricto, Vite y npm (no pnpm).
- `docs/adr/`: decisiones numeradas. `docs/openapi.json`: el contrato (generado). `docs/DESPLIEGUE.md`: Render y Neon.
- `.github/workflows/`: CI (`ci.yml`, `web.yml`, `e2e.yml`), despliegue nocturno, copia diaria y keep-alive.

## Comandos

```bash
docker compose up -d postgres                                  # los tests usan Postgres real (5433)
./gradlew :nx-time-backend:check                               # tests + JaCoCo (60 % en service/controller/audit)
./gradlew :nx-time-backend:test --rerun --tests '*Clase*'      # repetir tests sin que salgan UP-TO-DATE
./gradlew :nx-time-backend:actualizarOpenApi                   # regenerar docs/openapi.json tras cambiar un DTO
./gradlew :nx-time-frontend-android:testDevDebugUnitTest       # siempre con sabor: DevDebug
./gradlew :nx-time-frontend-android:lintDevDebug
cd nx-time-frontend-web && npm run generar && npm run typecheck && npm test && npm run build
cd nx-time-frontend-web && npm run e2e                         # Playwright, con el backend levantado
```

- Para los tests y el jar hace falta el JDK 21 (el `java` del sistema puede ser otro).
- **Nunca `--rerun-tasks`**: deja `build/` bloqueado. Si sale «Unable to delete directory», ejecutar `./gradlew --stop`, borrar esa carpeta y repetir.
- Probar contra una **base desechable** (`CREATE DATABASE nxtime_x` en el Postgres de Docker, y arrancar con `--spring.datasource.url` y `--spring.flyway.url`). La base `nxtime` arrastra migraciones de otras ramas.
- Antes de probar, matar lo que haya en el puerto: una instancia vieja sigue sirviendo y engaña.

## Convenciones

- Textos de la interfaz, mensajes de error, commits, PR y documentación, en español.
- Ramas `feat/…`, `fix/…`, `docs/…` y `chore/…`, siempre con PR. El CI en verde antes de pedir el merge.
- **Nunca mergear, desplegar ni publicar la app sin permiso explícito en cada caso.** Desplegar es lanzar «Desplegar» o tocar variables en Render, que despliega `main`.
- Migraciones: el siguiente `Vn` libre se mira en la carpeta; nunca se edita una migración ya aplicada. Los CHECK de tipos (`ck_avisos_tipo`, `ck_ejecuciones_tarea_tarea`) se reemplazan enteros, volviendo a listar todos los valores. `VARCHAR`, nunca `CHAR(n)`.
- Cambiar el contrato: backend, Android y web en el mismo PR; regenerar `openapi.json` y `npm run api:types`.
- Multiempresa sin filtro automático (ADR 006): cada endpoint que reciba un id comprueba `empresa_id` a mano.
- Cada authority nueva se refleja en `Permisos.kt` (con su caso en `PermisosTest`) y en la web.
- Ampliar `DemoDataSeeder` cuando haya pantallas nuevas, o saldrán vacías en la demo.
- Una decisión de diseño con consecuencias lleva un ADR nuevo en `docs/adr/`.

## Cómo encontrar bugs aquí

Los defectos serios salen **ejecutando** (levantar el sistema, recorrer el flujo
por la interfaz, con `curl` o en el emulador) y mirando la cobertura de JaCoCo;
leer el código no los encuentra. El patrón de bug que se repite es truncar
antes de sumar (`/ 60`, `toMinutes()`). Antes de dar algo por bug, comprobar que
los datos de prueba pueden mostrarlo y descartar la explicación inocente.
