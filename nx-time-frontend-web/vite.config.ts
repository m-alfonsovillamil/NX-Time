import react from '@vitejs/plugin-react';
// De `vitest/config` y no de `vite`: es lo que hace que el bloque `test`
// exista en el tipo de la configuración, y sin ello `tsc` lo rechaza.
import { defineConfig } from 'vitest/config';

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
export default defineConfig({
  plugins: [react()],
  server: {
    // El test de `navegacion/secciones.ts` lee `NoticeType.java` y
    // `RoleAuthorities.java` para comprobar que la web y el backend dicen lo
    // mismo. Vite solo sirve lo que hay dentro del proyecto; se abre esa
    // carpeta del backend y nada más.
    fs: { allow: ['.', '../nx-time-backend/src/main/java/com/nxtime/nxtime/domain'] },
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/auth': { target: 'http://localhost:8080', changeOrigin: true },
    },
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
});
