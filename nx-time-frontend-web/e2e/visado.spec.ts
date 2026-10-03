/**
 * El visado de firmas contra el backend de verdad, sin visar nada (el visto
 * bueno no se deshace): el mes anterior con quién ha firmado y la huella que
 * se comprueba. Visar se comprobó a mano al cerrar W7a (ver su PR).
 */

import { expect, test } from '@playwright/test';

import { irASeccion } from './ayudas';

test('visado de firmas para RRHH', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('elena.rios@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await irASeccion(page, 'Visado de firmas');
  await expect(page.getByText(/personas? han? firmado/)).toBeVisible();
  await expect(page.getByRole('table', { name: 'Firmas del mes por persona' })).toBeVisible();
});
