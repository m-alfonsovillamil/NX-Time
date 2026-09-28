/**
 * La carga por áreas, comprobada en un navegador (fase W8, ADR 029 decisión 6).
 *
 * `npm run presupuesto` mide lo que se descarga ANTES de poder hacer nada.
 * Esto mide lo de después: quien solo ficha recorre todas sus páginas **sin
 * descargar ni una línea de las de gestión**. Un `import` normal donde tocaba
 * un `lazy()` (por ejemplo, reutilizar un componente de la plantilla en el
 * perfil) arrastraría ese trozo, y el presupuesto no lo vería si cae fuera del
 * inicial.
 *
 * El patrón vale para el build (`/assets/Cuadrantes-abc123.js`, en el CI) y
 * para el servidor de desarrollo (`/src/paginas/cuadrantes/Cuadrantes.tsx`).
 */

import { expect, test, type Page } from '@playwright/test';

import { entrar, paginaLista } from './ayudas';

const GESTION =
  /\/(PanelGestion|AusenciasEquipo|HistorialEquipo|Plantilla|Departamentos|Proyectos|CalendarioLaboral|PanelEmpresa|Informes|Integridad|Borrados|GestionOfertas|CanalDenuncias|VisadoFirmas|Analitica|Cuadrantes)[-.]/;

/**
 * Los módulos de JS que pide la página. Por URL y no por `resourceType()`: el
 * trozo de una página llega por `import()` o, si Vite lo ha precargado, por un
 * `<link rel="modulepreload">`, y cada uno se anota con un tipo distinto.
 */
function anotarModulos(page: Page): string[] {
  const pedidos: string[] = [];
  page.on('request', (r) => {
    const ruta = new URL(r.url()).pathname;
    if (/\.(js|tsx?)$/.test(ruta)) pedidos.push(ruta);
  });
  return pedidos;
}

test('un empleado recorre todo lo suyo sin descargar nada de gestión', async ({ page }) => {
  const pedidos = anotarModulos(page);
  await entrar(page, 'javier.lopez@techcorp.demo');

  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  const rutas = await menu.getByRole('link').evaluateAll((enlaces) => enlaces.map((a) => a.getAttribute('href') ?? ''));
  for (const ruta of rutas) {
    await menu.locator(`a[href="${ruta}"]`).click();
    await paginaLista(page, ruta);
  }

  expect(pedidos.filter((p) => GESTION.test(p))).toEqual([]);
});

test('el trozo de una página de gestión solo llega al abrirla', async ({ page }) => {
  // El control del test de arriba: si el patrón no reconociera los trozos de
  // gestión, aquel pasaría siempre sin comprobar nada.
  const pedidos = anotarModulos(page);
  const deCuadrantes = () => pedidos.filter((p) => /\/Cuadrantes[-.]/.test(p));
  await entrar(page, 'marta.sanchez@techcorp.demo');
  expect(deCuadrantes()).toEqual([]);

  await page.getByRole('navigation', { name: 'Menú principal' }).first().getByRole('link', { name: 'Cuadrantes' }).click();
  await paginaLista(page, '/cuadrantes');
  expect(deCuadrantes()).not.toEqual([]);
});
