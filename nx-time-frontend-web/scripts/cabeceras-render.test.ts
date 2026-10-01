import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { cabecerasDeRender } from './cabeceras-render';

// Vitest corre desde la carpeta de la web (en jsdom, import.meta.url no es un file:).
const RENDER_YAML = readFileSync(resolve('..', 'render.yaml'), 'utf8');

describe('las cabeceras de render.yaml que copia vite preview', () => {
  it('del render.yaml de verdad salen la CSP y las demás cabeceras de toda la web', () => {
    const cabeceras = cabecerasDeRender(RENDER_YAML);

    expect(Object.keys(cabeceras).sort()).toEqual([
      'Content-Security-Policy',
      'Permissions-Policy',
      'Referrer-Policy',
      'X-Content-Type-Options',
      'X-Frame-Options',
    ]);
    expect(cabeceras['Content-Security-Policy']).toMatch(/^default-src 'self';/);
    expect(cabeceras['Content-Security-Policy']).not.toContain('"');
  });

  it('la foto de perfil (un blob:) y la cámara del kiosco están permitidas', () => {
    const cabeceras = cabecerasDeRender(RENDER_YAML);

    expect(cabeceras['Content-Security-Policy']).toMatch(/img-src [^;]*blob:/);
    expect(cabeceras['Permissions-Policy']).toContain('camera=(self)');
  });

  it('las de otras rutas (la caché de /assets) no se aplican a toda la web', () => {
    const cabeceras = cabecerasDeRender(RENDER_YAML);

    expect(cabeceras['Cache-Control']).toBeUndefined();
  });
});
