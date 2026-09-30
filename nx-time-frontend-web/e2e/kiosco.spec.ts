/**
 * El kiosco de fichaje contra el backend de verdad (ADR 033): una tablet se
 * empareja con el código que enseña, el ADMIN lo teclea en otra ventana, una
 * persona elige su PIN en su perfil y ficha con él en la tablet.
 *
 * Con Ana, que no sale en ninguna otra spec: aquí se ficha de verdad. Sin cámara
 * en el navegador de pruebas, la tablet ofrece buscar el nombre, que es lo que
 * se prueba; la tarjeta QR la cubren KioscoIT (backend) y los tests de la web.
 */

import { expect, test, type Browser, type Page } from '@playwright/test';

async function entrarEn(browser: Browser, email: string): Promise<Page> {
  const pagina = await (await browser.newContext()).newPage();
  await pagina.goto('/');
  await pagina.getByLabel('Correo electrónico').fill(email);
  await pagina.getByLabel('Contraseña').fill('demo1234');
  await pagina.getByRole('button', { name: 'Entrar' }).click();
  await expect(pagina.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
  return pagina;
}

/** Busca a Ana, teclea su PIN y deja la tablet enseñando sus botones. */
async function identificarseConPin(tablet: Page, pin: string) {
  await tablet.getByRole('button', { name: 'Busca tu nombre' }).click();
  await tablet.getByRole('textbox', { name: 'Tu nombre' }).fill('ana');
  await tablet.getByRole('button', { name: /^Ana Fern/ }).click();
  for (const cifra of pin) await tablet.getByRole('button', { name: cifra, exact: true }).click();
  await tablet.getByRole('button', { name: 'Aceptar' }).click();
  await expect(tablet.getByRole('heading', { name: 'Hola, Ana' })).toBeVisible();
}

test('emparejar una tablet y fichar en ella con el PIN', async ({ page: tablet, browser }) => {
  // Un nombre por ejecución: repetir la spec sobre la misma base deja kioscos revocados.
  const nombre = `Kiosco E2E ${Date.now() % 100_000}`;

  // 1. La tablet, sin sesión ni token, pide un código.
  await tablet.goto('/kiosco');
  await expect(tablet.getByRole('heading', { name: 'Empareja esta tablet' })).toBeVisible();
  const codigo = (await tablet.locator('.nx-kiosco__codigo').textContent())?.replace(/\s/g, '') ?? '';
  expect(codigo).toMatch(/^[A-Z0-9]{8}$/);

  // 2. El ADMIN lo teclea en los ajustes de la empresa.
  const admin = await entrarEn(browser, 'raul.ortega@techcorp.demo');
  await admin.getByRole('navigation', { name: 'Menú principal' }).first()
    .getByRole('link', { name: 'Ajustes de la empresa' }).click();
  await admin.getByLabel('Código de la tablet').fill(codigo);
  await admin.getByLabel('Nombre del kiosco').fill(nombre);
  await admin.getByRole('button', { name: 'Dar de alta' }).click();
  await expect(admin.getByText(nombre, { exact: true })).toBeVisible();

  // 3. La tablet se pone en marcha sola.
  await expect(tablet.getByText(nombre)).toBeVisible({ timeout: 15_000 });

  // 4. Ana elige su PIN en su perfil.
  const ana = await entrarEn(browser, 'ana.fernandez@techcorp.demo');
  await ana.goto('/perfil');
  await ana.getByLabel('Tu PIN').fill('4827');
  await ana.getByRole('button', { name: /Guardar el PIN|Cambiar el PIN/ }).click();
  await expect(ana.getByText('Tienes un PIN para el kiosco.')).toBeVisible();

  // 5. Y ficha en la tablet. La lista se carga al arrancar: se recarga para que
  //    salga Ana, que acaba de elegir su PIN.
  await tablet.reload();
  await expect(tablet.getByText(nombre)).toBeVisible();
  await identificarseConPin(tablet, '4827');

  const entrada = tablet.getByRole('button', { name: 'Fichar la entrada' });
  if (await entrada.isVisible()) {
    await entrada.click();
    await expect(tablet.getByRole('heading', { name: /^Ana: entrada a las \d\d:\d\d h$/ })).toBeVisible();
    // Vuelve sola a la espera, y se ficha la salida para dejar a Ana como estaba.
    await expect(tablet.getByRole('button', { name: 'Busca tu nombre' })).toBeVisible({ timeout: 10_000 });
    await identificarseConPin(tablet, '4827');
  }
  await tablet.getByRole('button', { name: 'Fichar la salida' }).click();
  await expect(tablet.getByRole('heading', { name: /^Ana: salida a las \d\d:\d\d h$/ })).toBeVisible();

  // 6. En su historial, la jornada dice que se abrió en el kiosco.
  await ana.goto('/historial');
  await expect(ana.getByText(`Kiosco · ${nombre}`).first()).toBeVisible();

  // 7. El ADMIN lo revoca, y la tablet vuelve a pedir que la emparejen.
  await admin.reload();
  await admin.getByRole('listitem').filter({ hasText: nombre }).getByRole('button', { name: 'Revocar' }).click();
  await admin.getByRole('dialog').getByRole('button', { name: 'Revocar' }).click();
  await expect(admin.getByText('Kiosco revocado.')).toBeVisible();
  await tablet.reload();
  await expect(tablet.getByRole('heading', { name: 'Empareja esta tablet' })).toBeVisible();
});
