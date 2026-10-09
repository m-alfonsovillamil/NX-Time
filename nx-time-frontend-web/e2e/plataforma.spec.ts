/**
 * El panel de plataforma contra el backend de verdad (ADR 040).
 *
 * En local y en el CI, `application-dev.yml` nombra operadora a Lucía, una
 * EMPLEADA de demo que ninguna otra spec usa: así el menú de las cuatro
 * cuentas de siempre sigue siendo el suyo. Aquí no se escribe nada.
 *
 * Lo que de verdad cierra la puerta es el servidor, y eso lo prueba
 * `PlataformaIT`. Esta spec comprueba lo que ve cada quien: que quien no es
 * operador no encuentra el apartado, y que la ruta escrita a mano le dice que
 * no es para su cuenta en vez de enseñarle una página rota.
 */

import { expect, test } from '@playwright/test';

import { entrar, irASeccion, paginaLista } from './ayudas';

const OPERADORA = 'lucia.moreno@techcorp.demo';

test('la operadora ve las empresas, busca una, abre su detalle y comprueba la traza', async ({ page }) => {
  await entrar(page, OPERADORA);
  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  // Es una empleada: lo suyo y la plataforma, nada de la gestión de su empresa.
  await expect(menu.getByRole('heading', { name: 'Plataforma' })).toBeVisible();
  await expect(menu.getByRole('heading', { name: 'Gestión' })).toHaveCount(0);

  await irASeccion(page, 'Empresas');
  await paginaLista(page, '/plataforma');
  await expect(page.getByRole('heading', { level: 1, name: 'Empresas' })).toBeVisible();

  const empresas = page.getByRole('table', { name: 'Empresas dadas de alta, con sus cifras de uso' });
  await expect(empresas.getByRole('link', { name: 'TechCorp Solutions' })).toBeVisible();
  await expect(empresas.getByRole('link', { name: 'Consultora Ibérica' })).toBeVisible();
  // Los totales: al menos las dos de demo, y alguien ha fichado.
  const totales = page.getByRole('list', { name: 'La instalación' });
  await expect(totales.getByText(/han? fichado en 30 días/)).toBeVisible();
  await expect(page.getByRole('table', { name: 'Estado de cada tarea nocturna' })).toBeVisible();

  await page.getByLabel('Buscar por nombre').fill('ibérica');
  await expect(empresas.getByRole('link', { name: 'TechCorp Solutions' })).toHaveCount(0);
  await expect(empresas.getByRole('link', { name: 'Consultora Ibérica' })).toBeVisible();
  await page.getByLabel('Buscar por nombre').fill('');

  await empresas.getByRole('link', { name: 'TechCorp Solutions' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'TechCorp Solutions' })).toBeVisible();
  // A quién escribir: su ADMIN, y nadie más de la plantilla.
  await expect(page.getByRole('link', { name: 'raul.ortega@techcorp.demo' })).toBeVisible();
  await expect(page.getByText('javier.lopez@techcorp.demo')).toHaveCount(0);
  await expect(page.getByRole('table', { name: 'Plantilla por rol' })).toBeVisible();

  await page.getByRole('link', { name: 'Todas las empresas' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Empresas' })).toBeVisible();
  await page.getByRole('button', { name: 'Comprobar la cadena' }).click();
  await expect(page.getByRole('status').getByText('La traza está intacta.')).toBeVisible({ timeout: 30000 });
});

test('un ADMIN no ve el apartado, y la ruta escrita a mano le dice que no es para su cuenta', async ({ page }) => {
  await entrar(page, 'raul.ortega@techcorp.demo');
  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  await expect(menu.getByRole('heading', { name: 'Gestión' })).toBeVisible();
  await expect(menu.getByRole('heading', { name: 'Plataforma' })).toHaveCount(0);
  await expect(menu.getByRole('link', { name: 'Empresas', exact: true })).toHaveCount(0);

  // Ni la lista ni el detalle de su propia empresa. Y sin pedirle nada al
  // servidor: la página no llega a pintarse.
  const pedidas: string[] = [];
  page.on('request', (peticion) => {
    if (peticion.url().includes('/api/v1/plataforma/')) pedidas.push(peticion.url());
  });
  for (const ruta of ['/plataforma', '/plataforma?empresa=1']) {
    await page.goto(ruta);
    await expect(page.getByRole('heading', { name: 'Esta página no es para tu cuenta' })).toBeVisible();
    await expect(page.getByText('TechCorp Solutions')).toHaveCount(0);
  }
  expect(pedidas).toEqual([]);
});
