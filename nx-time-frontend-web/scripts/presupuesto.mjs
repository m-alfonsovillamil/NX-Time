/**
 * El presupuesto de peso: el JS que se descarga **antes de poder hacer nada** no pasa de 150 kB comprimido.
 *
 * Se ejecuta después de `npm run build`, en CI (ADR 029, decisión 6).
 *
 * ## Por qué hace falta
 *
 * La web tendrá unas treinta páginas, y la mayoría no las abre casi nadie: el
 * editor de cuadrantes, la analítica, el visado de firmas. Cada página va en su
 * propio trozo (`React.lazy`), pero basta un `import` normal en el sitio
 * equivocado para que una de ellas se meta en el trozo inicial, y nada
 * fallaría: la web iría un poco más lenta cada vez, en el móvil y con la red de
 * una nave, sin que nadie lo notara en su portátil. Esto lo convierte en un
 * fallo del CI.
 *
 * ## Qué cuenta
 *
 * Lo que `dist/index.html` carga de entrada: sus `<script type="module">` y
 * sus `<link rel="modulepreload">`, comprimidos con gzip (lo que viaja de
 * verdad). No cuenta el CSS ni las fuentes, ni los trozos que se piden al
 * visitar una página.
 */

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const LIMITE_KB = 150;

const DIST = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'dist');

let html;
try {
  html = readFileSync(join(DIST, 'index.html'), 'utf-8');
} catch {
  console.error('No hay dist/index.html: ejecuta `npm run build` antes.');
  process.exit(1);
}

const iniciales = [
  ...html.matchAll(/<script[^>]+type="module"[^>]+src="([^"]+)"/g),
  ...html.matchAll(/<link[^>]+rel="modulepreload"[^>]+href="([^"]+)"/g),
].map((m) => m[1]);

if (iniciales.length === 0) {
  // Mejor fallar que aprobar un presupuesto sin haber medido nada.
  console.error('No se ha encontrado ningún script en dist/index.html: ¿ha cambiado el formato?');
  process.exit(1);
}

let total = 0;
for (const ruta of new Set(iniciales)) {
  const kb = gzipSync(readFileSync(join(DIST, ruta.replace(/^\//, '')))).length / 1024;
  total += kb;
  console.log(`  ${ruta}  ${kb.toFixed(1)} kB`);
}

console.log(`JS inicial: ${total.toFixed(1)} kB comprimido (límite: ${LIMITE_KB} kB).`);
if (total > LIMITE_KB) {
  console.error('Se ha pasado del presupuesto. Busca qué página ha dejado de cargarse con React.lazy.');
  process.exit(1);
}
