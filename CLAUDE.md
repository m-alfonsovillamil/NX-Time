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
- Probar siempre contra una **base desechable**: la base `nxtime` arrastra migraciones de otras ramas. Lo hace `scripts/dev/backend-desechable.sh` (ver abajo).

## Scripts de ayuda (`scripts/dev/`)

Están para usarlos, no para reescribirlos en cada sesión:

- `backend-desechable.sh <base> [puerto] [--sin-compilar]`: crea la base si no existe, compila (resolviendo los bloqueos de `build/`), arranca con `dev,demo` y sin `show-sql`, y espera a los datos de demo. Comprueba antes que el puerto esté libre. También acepta `--parar <puerto>` y `--borrar <base>`. El log va a `nx-time-backend/build/backend-<base>.log`.
- `sustituir.py cambios.py [--probar]`: aplica una lista `CAMBIOS = [(fichero, viejo, nuevo)]`; cada texto viejo debe aparecer exactamente una vez, y si alguno falla no se escribe nada. Para cambios con barras invertidas, comillas o varias líneas, que en un heredoc se rompen: el `cambios.py` se escribe con Write en el scratchpad.
- `ui.ps1` (PowerShell: `. .\scripts\dev\ui.ps1`): manejar el emulador por el árbol de accesibilidad (`Textos`, `Tocar`, `Escribir`, `Tecla`, `Captura`).
- Además: `scripts/e2e.py` (recorrido por HTTP), `scripts/probar-restauracion.sh` y `npm run capturas` en la web.

## Gastar poco contexto

- **Salida de compilaciones y tests, filtrada:** `./gradlew -q …` o `… 2>&1 | grep -E "FAILED|BUILD|error:|Exception" | head -40`; Playwright con `--reporter=line`; `npm ci --silent`. Si algo falla, leer el informe de ese test (`build/test-results`, `build/reports`), no la salida entera.
- **Ficheros grandes, por secciones:** README, `docs/DESPLIEGUE.md`, `render.yaml`, los ADR largos. Primero `grep -n "^#"` para ubicar la sección, y luego leer solo ese rango.
- **`docs/openapi.json` no se lee entero:** buscar la ruta o el esquema con Grep, o filtrarlo con un script.
- Logs del backend: buscar con Grep (`ERROR`, la clase o la ruta), no leerlos enteros.
- Para buscar en el repo, mejor la herramienta Grep o Glob que un subagente; dejar los subagentes para búsquedas muy amplias o trabajo en paralelo.

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
