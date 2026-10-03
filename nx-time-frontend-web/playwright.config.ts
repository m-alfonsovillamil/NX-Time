import { defineConfig, devices } from '@playwright/test';

// La web no instala los tipos de Node (no los necesita), y esto es lo único de
// Node que usa la configuración.
declare const process: { env: Record<string, string | undefined> };

/** GitHub Actions define `CI=true` en todos sus pasos. */
const enCi = Boolean(process.env['CI']);

/**
 * `npm run capturas`: npm pone el nombre del script en `npm_lifecycle_event`
 * (en cualquier sistema, sin `cross-env`). Solo entonces existe el proyecto que
 * saca las capturas del README; `npm run e2e` y el CI no lo ven.
 */
const sacandoCapturas = process.env['npm_lifecycle_event'] === 'capturas';

/**
 * Los tests de extremo a extremo: **contra el backend de verdad**, no contra
 * respuestas simuladas.
 *
 * Los tests de Vitest cubren lo que se puede razonar (el refresco serializado,
 * el formateo, los mensajes de error); estos cubren lo único que ninguno de
 * ellos puede demostrar: que la web y el backend **hablan el mismo idioma**. En
 * este proyecto los defectos que se han escapado siempre han salido ejecutando
 * el sistema, no leyendo el código.
 *
 * Están fuera de `npm test` a propósito: necesitan un backend levantado con el
 * perfil `demo`. En el CI los ejecuta `.github/workflows/e2e.yml`, que levanta
 * Postgres y el jar del backend en cada PR (fase W8). En local:
 *
 * ```bash
 * docker compose up -d postgres
 * ./gradlew :nx-time-backend:bootRun --args="--spring.profiles.active=dev,demo"
 * npm run e2e
 * ```
 *
 * Mejor sobre una base **recién creada**: los datos que dejan las pruebas a
 * mano cambian el orden de las listas y algunas specs los notan.
 */
export default defineConfig({
  testDir: './e2e',
  // Uno en marcha: los tests fichan de verdad, y dos jornadas abiertas a la
  // vez para el mismo usuario chocarían con `uq_registros_jornada_abierta`.
  workers: 1,
  // Sin reintentos, tampoco en el CI: un test que a veces falla se arregla, no
  // se tapa. Y un `test.only` olvidado dejaría el CI en verde probando uno.
  retries: 0,
  forbidOnly: enCi,
  reporter: enCi ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    // El arranque en frío no aplica en local, pero el primer render con Vite
    // sin caché sí tarda.
    actionTimeout: 15_000,
    // Lo que hace falta para entender un fallo del CI sin reproducirlo: la
    // traza (DOM, red y consola de cada paso) solo de los que fallan.
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'escritorio', use: { ...devices['Desktop Chrome'] }, testIgnore: /(movil|capturas)\.spec/ },
    // La web es hoy el cliente de quien tenga iPhone (no hay app de iOS), así
    // que el móvil se prueba en los dos motores: Chromium como un Android, y
    // WebKit, que es el de Safari y el único que hay en un iPhone.
    { name: 'movil', use: { ...devices['Pixel 7'] }, testMatch: /movil\.spec/ },
    { name: 'iphone', use: { ...devices['iPhone 14'] }, testMatch: /movil\.spec/ },
    ...(sacandoCapturas ? [{ name: 'capturas', use: { ...devices['Desktop Chrome'] }, testMatch: /capturas\.spec/ }] : []),
  ],
  webServer: {
    // En el CI, el build de producción y no el servidor de desarrollo: es lo
    // que se despliega, y así no hay la recarga del primer arranque de Vite
    // (descubre dependencias al vuelo, recarga la página y la spec que estaba
    // en marcha falla).
    command: enCi ? 'npm run build && npm run preview -- --port 5173 --strictPort' : 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !enCi,
    timeout: 120_000,
    // Unas claves de Firebase de mentira, para que Ajustes ofrezca encender el
    // push: `push.spec` simula las APIs de Google en la red. La clave VAPID
    // tiene que ser una clave pública válida (65 bytes en base64url); es la de
    // ejemplo de la documentación de web-push.
    env: {
      VITE_FIREBASE_API_KEY: 'clave-e2e',
      VITE_FIREBASE_PROJECT_ID: 'nx-time-e2e',
      VITE_FIREBASE_SENDER_ID: '1234567890',
      VITE_FIREBASE_APP_ID: '1:1234567890:web:e2e',
      VITE_FIREBASE_VAPID_KEY: 'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U',
    },
  },
});
