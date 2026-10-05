/**
 * Mide lo que nota quien usa la web: cuánto pesa la primera carga, cuánto
 * tarda en verse, cuánto salta la página al llegar los datos y cuánto tarda en
 * responder el menú.
 *
 *   npm run build && npx vite preview --port 4173 &
 *   node scripts/medir.mjs http://localhost:4173 [http://localhost:4174 …]
 *
 * Con varias direcciones (una por build) saca una columna por cada una: así se
 * compara una rama con `main`. Necesita el backend local con el perfil `demo`.
 *
 * ## Por qué no Lighthouse
 *
 * Lighthouse mide una URL pública, y aquí lo que importa está detrás del
 * login: la tabla que sustituye al esqueleto, el cambio de página desde el
 * menú. Playwright ya es una dependencia, entra con una cuenta de la demo y da
 * las mismas métricas (LCP, CLS) con `PerformanceObserver`, más las que
 * Lighthouse no tiene (el tiempo de un cambio de página).
 *
 * ## Qué red
 *
 * La carga inicial y el menú se miden con una red lenta simulada (1,6 Mbit/s
 * y 150 ms de latencia, el «3G rápido» de las herramientas del navegador): en
 * local todo tarda 5 ms y no se vería ninguna diferencia. Cada medida se
 * repite y se da la mediana.
 */

import { chromium } from '@playwright/test';

const BASES = process.argv.slice(2);
if (BASES.length === 0) {
  console.error('Uso: node scripts/medir.mjs <url> [<url> …]');
  process.exit(1);
}

const REPETICIONES = 5;
const RED_LENTA = { offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 };
const CUENTA = 'elena.rios@techcorp.demo';

const mediana = (valores) => {
  const orden = [...valores].sort((a, b) => a - b);
  return orden[Math.floor(orden.length / 2)];
};

/** Deja en `window.__medidas` el LCP y la suma de saltos de diseño, que se van acumulando. */
const OBSERVAR = () => {
  window.__medidas = { lcp: 0, cls: 0 };
  new PerformanceObserver((lista) => {
    for (const e of lista.getEntries()) window.__medidas.lcp = e.startTime;
  }).observe({ type: 'largest-contentful-paint', buffered: true });
  new PerformanceObserver((lista) => {
    for (const e of lista.getEntries()) if (!e.hadRecentInput) window.__medidas.cls += e.value;
  }).observe({ type: 'layout-shift', buffered: true });
};

/** Va a una sección por el menú lateral, abriendo antes su apartado si está plegado. */
async function enlaceDelMenu(pagina, nombre) {
  const menu = pagina.locator('nav.nx-lateral');
  const enlace = menu.getByRole('link', { name: nombre, exact: true });
  if (!(await enlace.isVisible())) {
    await menu
      .locator('.nx-subgrupo')
      .filter({ has: pagina.getByRole('link', { name: nombre, exact: true, includeHidden: true }) })
      .locator(':scope > button[aria-expanded="false"]')
      .click();
  }
  return enlace;
}

async function medirUnaVez(navegador, base) {
  const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 } });
  const pagina = await contexto.newPage();
  await pagina.addInitScript(OBSERVAR);
  const cdp = await contexto.newCDPSession(pagina);
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', RED_LENTA);

  // Lo que baja en la primera carga, por tipo. `encodedDataLength` es lo que viaja por la red.
  const peso = { script: 0, stylesheet: 0, font: 0, otros: 0 };
  let peticiones = 0;
  const tipos = new Map();
  cdp.on('Network.responseReceived', (e) => tipos.set(e.requestId, e.type.toLowerCase()));
  cdp.on('Network.loadingFinished', (e) => {
    const tipo = tipos.get(e.requestId) ?? 'otros';
    peso[tipo in peso ? tipo : 'otros'] += e.encodedDataLength;
    peticiones += 1;
  });

  /* 1. La primera carga: la pantalla de entrar. */
  await pagina.goto(base, { waitUntil: 'networkidle' });
  await pagina.getByLabel('Correo electrónico').waitFor();
  await pagina.waitForTimeout(500);
  const entrada = await pagina.evaluate(() => ({ ...window.__medidas }));
  // Cuándo estuvo lista la fuente del título (Sora 700), desde el inicio de la navegación.
  const fuente = await pagina.evaluate(() => {
    const sora = performance.getEntriesByType('resource').filter((r) => /sora-latin-700.*\.woff2/.test(r.name));
    return sora.length > 0 ? Math.round(sora[0].responseEnd) : null;
  });
  const cargaInicial = { ...peso, peticiones };

  /* 2. Entrar, y la primera página con datos. */
  await pagina.getByLabel('Correo electrónico').fill(CUENTA);
  await pagina.getByLabel('Contraseña').fill('demo1234');
  await pagina.getByRole('button', { name: 'Entrar' }).click();
  await pagina.getByRole('heading', { name: 'Mi jornada' }).waitFor();
  await pagina.locator('.nx-esqueleto').first().waitFor({ state: 'detached' }).catch(() => {});
  await pagina.waitForTimeout(1200);

  /* 3. Cambiar de página desde el menú: el ratón llega, se para un instante y pulsa. */
  const cambios = {};
  const saltos = {};
  for (const [nombre, titulo] of [
    ['Plantilla', 'Plantilla'],
    ['Historial', /historial/i],
    ['Analítica', 'Analítica'],
  ]) {
    const enlace = await enlaceDelMenu(pagina, nombre);
    await pagina.evaluate(() => (window.__medidas.cls = 0));
    await enlace.hover();
    // Lo que tarda una persona entre poner el ratón encima y pulsar.
    await pagina.waitForTimeout(400);
    const antes = Date.now();
    await enlace.click();
    await pagina.getByRole('heading', { level: 1, name: titulo }).waitFor();
    cambios[nombre] = Date.now() - antes;
    await pagina.locator('.nx-esqueleto').first().waitFor({ state: 'detached' }).catch(() => {});
    await pagina.waitForTimeout(1500);
    saltos[nombre] = await pagina.evaluate(() => window.__medidas.cls);
  }

  /* 4. Como en el móvil, donde no hay ratón que pase antes por encima: llegar y pulsar. */
  const directos = {};
  for (const nombre of ['Ausencias', 'Calendario']) {
    const enlace = await enlaceDelMenu(pagina, nombre);
    // `dispatchEvent` y no `click`: Playwright mueve el ratón hasta el enlace antes de pulsar, y eso ya sería pasar por encima.
    const antes = Date.now();
    await enlace.dispatchEvent('click');
    await pagina.getByRole('heading', { level: 1, name: nombre }).waitFor();
    directos[nombre] = Date.now() - antes;
    await pagina.locator('.nx-esqueleto').first().waitFor({ state: 'detached' }).catch(() => {});
    await pagina.waitForTimeout(500);
  }

  await contexto.close();
  return { cargaInicial, lcp: entrada.lcp, clsEntrada: entrada.cls, fuente, cambios, saltos, directos };
}

const kB = (bytes) => `${(bytes / 1024).toFixed(1)} kB`;
const ms = (n) => (n === null ? '—' : `${Math.round(n)} ms`);

const navegador = await chromium.launch();
const resultados = [];
for (const base of BASES) {
  const pasadas = [];
  for (let i = 0; i < REPETICIONES; i++) pasadas.push(await medirUnaVez(navegador, base));
  const de = (f) => mediana(pasadas.map(f));
  resultados.push({
    base,
    filas: [
      ['Primera carga: JS', kB(de((p) => p.cargaInicial.script))],
      ['Primera carga: CSS', kB(de((p) => p.cargaInicial.stylesheet))],
      ['Primera carga: fuentes', kB(de((p) => p.cargaInicial.font))],
      ['Primera carga: peticiones', String(de((p) => p.cargaInicial.peticiones))],
      ['LCP de la pantalla de entrar', ms(de((p) => p.lcp))],
      ['Fuente del título lista', ms(de((p) => p.fuente ?? 0) || null)],
      ['CLS de la pantalla de entrar', de((p) => p.clsEntrada).toFixed(3)],
      ...['Plantilla', 'Historial', 'Analítica'].flatMap((n) => [
        [`Menú → ${n}: hasta ver su título`, ms(de((p) => p.cambios[n]))],
        [`Menú → ${n}: CLS al llegar los datos`, de((p) => p.saltos[n]).toFixed(3)],
      ]),
      ...['Ausencias', 'Calendario'].map((n) => [`Sin pasar antes el ratón → ${n}: hasta ver su título`, ms(de((p) => p.directos[n]))]),
    ],
  });
}
await navegador.close();

console.log(`| Medida | ${resultados.map((r) => r.base).join(' | ')} |`);
console.log(`|---|${resultados.map(() => '---').join('|')}|`);
resultados[0].filas.forEach(([nombre], i) => {
  console.log(`| ${nombre} | ${resultados.map((r) => r.filas[i][1]).join(' | ')} |`);
});
