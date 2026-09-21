import { defineConfig, devices } from '@playwright/test';

/**
 * El test de extremo a extremo: **contra el backend de verdad**, no contra
 * respuestas simuladas.
 *
 * Es el único de este proyecto, y esa proporción es deliberada. Los tests de
 * Vitest cubren lo que se puede razonar (el refresco serializado, el formateo,
 * los mensajes de error); este cubre lo único que ninguno de ellos puede
 * demostrar: que la web y el backend **hablan el mismo idioma**. En este
 * proyecto los defectos que se han escapado siempre han salido ejecutando el
 * sistema, no leyendo el código.
 *
 * Está fuera de `npm test` a propósito: necesita un backend levantado con
 * `docker compose` y el perfil `demo`, y un test que no se puede ejecutar sin
 * leer un README no debería bloquear a quien solo quiere cambiar un color.
 *
 * ```bash
 * docker compose up -d postgres
 * ./gradlew :nx-time-backend:bootRun --args="--spring.profiles.active=dev,demo"
 * npm run e2e
 * ```
 */
export default defineConfig({
  testDir: './e2e',
  // Uno en marcha: los tests fichan de verdad, y dos jornadas abiertas a la
  // vez para el mismo usuario chocarían con `uq_registros_jornada_abierta`.
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    ...devices['Desktop Chrome'],
    // El arranque en frío no aplica en local, pero el primer render con Vite
    // sin caché sí tarda.
    actionTimeout: 15_000,
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
