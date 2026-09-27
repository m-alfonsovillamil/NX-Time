/**
 * Panel de empresa, informes e integridad contra el backend de verdad. Aquí
 * no se escribe nada, así que se prueba todo: el panel con sus cifras, las
 * dos descargas (el Excel y el PDF llegan enteros y con su nombre) y la
 * comprobación completa de la cadena de auditoría.
 */

import { expect, test } from '@playwright/test';

test('panel de empresa, informes e integridad para RRHH', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('elena.rios@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();

  await menu.getByRole('link', { name: 'Panel de empresa' }).click();
  await expect(page.getByRole('heading', { name: 'Horas por empleado' })).toBeVisible();
  await expect(page.getByText(/^La raya es la media del equipo/)).toBeVisible();

  await menu.getByRole('link', { name: 'Informes' }).click();
  const excel = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar Excel' }).click();
  const fichero = await excel;
  expect(fichero.suggestedFilename()).toMatch(/\.xlsx$/);
  expect(await fichero.failure()).toBeNull();

  await page.getByLabel('Persona', { exact: true }).selectOption({ label: 'Javier' });
  const pdf = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar PDF' }).click();
  const informe = await pdf;
  expect(informe.suggestedFilename()).toMatch(/\.pdf$/);
  expect(await informe.failure()).toBeNull();

  await menu.getByRole('link', { name: 'Integridad de la auditoría' }).click();
  await page.getByRole('button', { name: 'Comprobar la cadena' }).click();
  await expect(page.getByRole('status').getByText('La traza está intacta.')).toBeVisible({ timeout: 30000 });
});
