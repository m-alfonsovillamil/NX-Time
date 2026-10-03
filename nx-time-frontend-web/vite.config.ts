import { readFileSync } from 'node:fs';

import react from '@vitejs/plugin-react';
// De `vitest/config` y no de `vite`: es lo que hace que el bloque `test`
// exista en el tipo de la configuración, y sin ello `tsc` lo rechaza.
import { defineConfig } from 'vitest/config';

import { cabecerasDeRender } from './scripts/cabeceras-render.ts';

/**
 * En desarrollo, `/api` y `/auth` se reenvían al backend local.
 *
 * Así el navegador ve un solo origen y **no hay CORS mientras se desarrolla**,
 * que es lo que evita perder una tarde persiguiendo un preflight que en
 * producción no existiría igual. El precio es que el CORS de verdad solo se
 * comprueba al desplegar; por eso la fase A10 dejó un WARN al arrancar el
 * backend en producción si `CORS_ALLOWED_ORIGINS` está vacío.
 *
 * Los tests corren en jsdom porque prueban componentes; `scripts/` no lo
 * necesita, pero tener dos entornos por proyecto complica más de lo que ahorra.
 */
const proxy = {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/auth': { target: 'http://localhost:8080', changeOrigin: true },
  // Lo que habla la tablet del kiosco (ADR 033). Solo lo que va DEBAJO de
  // /kiosco/: /kiosco a secas es la página de la web, y reenviarla mandaría la
  // pantalla de la tablet al backend. En producción no chocan: la web y la API
  // están en dominios distintos.
  '^/kiosco/': { target: 'http://localhost:8080', changeOrigin: true },
};

export default defineConfig(({ isPreview }) => ({
  plugins: [react()],
  // `vite preview` sirve el build de producción, y lo usa Playwright en el CI
  // (ver playwright.config.ts). Necesita el mismo reenvío que el servidor de
  // desarrollo, y las mismas cabeceras que Render: así los E2E corren con la
  // CSP de producción. El servidor de desarrollo no las lleva porque Vite mete
  // scripts en línea que la CSP rechazaría. Se lee solo al arrancar preview:
  // el build no depende de render.yaml.
  preview: {
    proxy,
    ...(isPreview === true
      ? { headers: cabecerasDeRender(readFileSync(new URL('../render.yaml', import.meta.url), 'utf8')) }
      : {}),
  },
  server: {
    // El test de `navegacion/secciones.ts` lee `NoticeType.java` y
    // `RoleAuthorities.java` para comprobar que la web y el backend dicen lo
    // mismo. Vite solo sirve lo que hay dentro del proyecto; se abre esa
    // carpeta del backend y nada más.
    fs: { allow: ['.', '../nx-time-backend/src/main/java/com/nxtime/nxtime/domain'] },
    proxy,
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/pruebas/preparar.ts'],
    // `e2e/` es de Playwright, pero sus ficheros se llaman `.spec.ts` y eso
    // encaja con el patrón por defecto de Vitest, que intenta ejecutarlos y
    // falla con un error que no dice por qué. Sin esta línea, `npm test` está
    // roto en cuanto existe el primer test de extremo a extremo.
    exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
  },
}));
