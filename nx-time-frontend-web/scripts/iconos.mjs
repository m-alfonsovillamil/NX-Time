/**
 * Los PNG de la web instalable, a partir de los SVG de `public/iconos/` (W9).
 *
 * El manifest y el iPhone piden PNG de tamaños concretos, y el origen es un SVG
 * (la marca de la app Android pasada a SVG). Se rasteriza con el Chromium de
 * Playwright, que ya es dependencia de desarrollo: ni ImageMagick ni una
 * librería más. Se versionan los PNG; esto solo hace falta si cambia la marca.
 *
 * ```bash
 * npm run iconos
 * ```
 */

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from '@playwright/test';

const ICONOS = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'iconos');

const SALIDAS = [
  // Los del manifest con `purpose: any`: con las esquinas redondeadas del SVG.
  { svg: 'icono.svg', png: 'icono-192.png', lado: 192 },
  { svg: 'icono.svg', png: 'icono-512.png', lado: 512 },
  // `purpose: maskable`: a sangre, lo recorta el sistema.
  { svg: 'icono-adaptable.svg', png: 'icono-adaptable-512.png', lado: 512 },
  // El del iPhone también a sangre: iOS pone sus propias esquinas, y lo
  // transparente lo pinta negro.
  { svg: 'icono-adaptable.svg', png: 'apple-touch-icon.png', lado: 180 },
];

const navegador = await chromium.launch();
try {
  for (const { svg, png, lado } of SALIDAS) {
    const pagina = await navegador.newPage({ viewport: { width: lado, height: lado } });
    const marcado = readFileSync(join(ICONOS, svg), 'utf-8');
    await pagina.setContent(
      `<style>html,body{margin:0;background:transparent}svg{display:block;width:${lado}px;height:${lado}px}</style>${marcado}`,
    );
    await pagina.screenshot({ path: join(ICONOS, png), omitBackground: true });
    await pagina.close();
    console.log(`  ${png}  ${lado}x${lado}`);
  }
} finally {
  await navegador.close();
}
