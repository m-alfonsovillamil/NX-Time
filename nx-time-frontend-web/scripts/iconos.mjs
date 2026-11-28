/**
 * Los PNG de la marca, a partir de los SVG de `public/iconos/` (W9 y plan P).
 *
 * - Los de la web instalable: el manifest y el iPhone piden PNG de tamaños
 *   concretos.
 * - Los de Google Play: el icono de 512 px (`ic_launcher-playstore.png` de la
 *   app) y el gráfico de funciones de 1024×500 de la ficha
 *   (`docs/play/grafico-de-funciones.png`), con el nombre de `titular.ts` y el
 *   lema de la pantalla de acceso. Así, tras un cambio de nombre
 *   (docs/CAMBIO-DE-NOMBRE.md), redibujar los SVG y lanzar esto basta.
 *
 * El origen es un SVG (la marca de la app Android pasada a SVG). Se rasteriza
 * con el Chromium de Playwright, que ya es dependencia de desarrollo: ni
 * ImageMagick ni una librería más. Se versionan los PNG; esto solo hace falta
 * si cambia la marca.
 *
 * ```bash
 * npm run iconos
 * ```
 */

import { mkdirSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from '@playwright/test';

const AQUI = dirname(fileURLToPath(import.meta.url));
const WEB = resolve(AQUI, '..');
const RAIZ = resolve(WEB, '..');
const ICONOS = join(WEB, 'public', 'iconos');

const SALIDAS = [
  // Los del manifest con `purpose: any`: con las esquinas redondeadas del SVG.
  { svg: 'icono.svg', png: join(ICONOS, 'icono-192.png'), lado: 192 },
  { svg: 'icono.svg', png: join(ICONOS, 'icono-512.png'), lado: 512 },
  // `purpose: maskable`: a sangre, lo recorta el sistema.
  { svg: 'icono-adaptable.svg', png: join(ICONOS, 'icono-adaptable-512.png'), lado: 512 },
  // El del iPhone también a sangre: iOS pone sus propias esquinas, y lo
  // transparente lo pinta negro.
  { svg: 'icono-adaptable.svg', png: join(ICONOS, 'apple-touch-icon.png'), lado: 180 },
  // El de Google Play, también a sangre: Play le pone sus esquinas y su
  // sombra, y pide un cuadrado de 512 sin transparencias.
  {
    svg: 'icono-adaptable.svg',
    png: join(RAIZ, 'nx-time-frontend-android', 'src', 'main', 'ic_launcher-playstore.png'),
    lado: 512,
  },
];

/** El gráfico de funciones de la ficha de Play: 1024×500, sin transparencias. */
const GRAFICO = join(RAIZ, 'docs', 'play', 'grafico-de-funciones.png');

/** Un valor `clave: "..."` o `clave: '...'` de un fichero de TypeScript, sin importarlo. */
export function leerTexto(fuente, clave) {
  const valor = fuente.match(new RegExp(`\\b${clave}: (["'])(.*?)\\1`));
  if (!valor) throw new Error(`No encuentro «${clave}».`);
  return valor[2];
}

const escapar = (texto) => texto.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Una fuente de @fontsource en línea, para que la página no dependa de cargar ficheros. */
function fuente(peso) {
  const requerir = createRequire(import.meta.url);
  const ruta = requerir.resolve(`@fontsource/sora/files/sora-latin-${peso}-normal.woff2`);
  const datos = readFileSync(ruta).toString('base64');
  return `@font-face{font-family:Sora;font-weight:${peso};src:url(data:font/woff2;base64,${datos}) format('woff2')}`;
}

/** El HTML del gráfico de funciones: el icono, el nombre y el lema, con la paleta de la marca. */
export function htmlDelGrafico({ producto, lema, icono, colores }) {
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><style>
${fuente(400)}${fuente(700)}
html,body{margin:0}
body{width:1024px;height:500px;display:flex;align-items:center;gap:56px;padding:0 88px;box-sizing:border-box;
  background:linear-gradient(135deg,${colores.fondoArriba},${colores.fondoAbajo});font-family:Sora,sans-serif}
.icono{flex:none;width:224px;height:224px;filter:drop-shadow(0 12px 28px rgba(11,30,56,.28))}
.icono svg{width:100%;height:100%;display:block}
h1{margin:0;font-weight:700;font-size:76px;line-height:1.05;color:${colores.primario};letter-spacing:-1px;text-wrap:balance}
p{margin:20px 0 0;font-weight:400;font-size:30px;line-height:1.3;color:${colores.texto};max-width:560px;text-wrap:balance}
</style></head><body><div class="icono">${icono}</div>
<div><h1>${escapar(producto)}</h1><p>${escapar(lema)}</p></div></body></html>`;
}

/** Un color de `tokens.css` (el bloque claro, que es el primero). */
function color(tokens, nombre) {
  const valor = tokens.match(new RegExp(`--nx-${nombre}:\\s*(#[0-9a-fA-F]{6})`));
  if (!valor) throw new Error(`No encuentro --nx-${nombre} en tokens.css.`);
  return valor[1];
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const navegador = await chromium.launch();
  try {
    for (const { svg, png, lado } of SALIDAS) {
      const pagina = await navegador.newPage({ viewport: { width: lado, height: lado } });
      const marcado = readFileSync(join(ICONOS, svg), 'utf-8');
      await pagina.setContent(
        `<style>html,body{margin:0;background:transparent}svg{display:block;width:${lado}px;height:${lado}px}</style>${marcado}`,
      );
      await pagina.screenshot({ path: png, omitBackground: true });
      await pagina.close();
      console.log(`  ${png.slice(RAIZ.length + 1)}  ${lado}x${lado}`);
    }

    const tokens = readFileSync(join(WEB, 'src', 'estilos', 'tokens.css'), 'utf-8');
    const html = htmlDelGrafico({
      producto: leerTexto(readFileSync(join(WEB, 'src', 'legal', 'titular.ts'), 'utf-8'), 'producto'),
      lema: leerTexto(readFileSync(join(WEB, 'src', 'i18n', 'es', 'acceso.ts'), 'utf-8'), 'lema'),
      icono: readFileSync(join(ICONOS, 'icono.svg'), 'utf-8'),
      colores: {
        fondoArriba: color(tokens, 'fondo-arriba'),
        fondoAbajo: color(tokens, 'fondo-abajo'),
        primario: color(tokens, 'primary'),
        texto: color(tokens, 'on-primary-container'),
      },
    });
    const pagina = await navegador.newPage({ viewport: { width: 1024, height: 500 } });
    await pagina.setContent(html);
    await pagina.evaluate(() => document.fonts.ready);
    mkdirSync(dirname(GRAFICO), { recursive: true });
    await pagina.screenshot({ path: GRAFICO });
    await pagina.close();
    console.log(`  ${GRAFICO.slice(RAIZ.length + 1)}  1024x500`);
  } finally {
    await navegador.close();
  }
}
