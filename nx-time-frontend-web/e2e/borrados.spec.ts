/**
 * Los borrados de datos contra el backend de verdad, sin ejecutar nada:
 * borrar es irreversible y dejaría la demo sin esa persona. Registrar una
 * solicitud y ejecutarla escribiendo el nombre se comprobaron a mano al
 * cerrar W6d (ver su PR).
 */

import { expect, test } from '@playwright/test';

test('borrados de datos para RRHH', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('elena.rios@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await page.getByRole('navigation', { name: 'Menú principal' }).first().getByRole('link', { name: 'Borrados de datos' }).click();
  await expect(page.getByRole('heading', { name: 'Borrados de datos' })).toBeVisible();
  // Registrar abre el formulario con las personas a quien se le puede registrar.
  await page.getByRole('button', { name: 'Registrar solicitud' }).click();
  await expect(page.getByRole('dialog').getByRole('option', { name: /Javier/ })).toBeAttached();
  await page.getByRole('dialog').getByRole('button', { name: 'Cancelar' }).click();
});
