/**
 * La analítica contra el backend de verdad (todo es de lectura): RRHH ve la
 * empresa y descarga el CSV; una gestora ve solo su departamento, y la
 * cabecera lo dice.
 */

import { expect, test, type Page } from '@playwright/test';

import { irASeccion } from './ayudas';

async function entrar(page: Page, email: string) {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
}

test('analítica de la empresa para RRHH, con el CSV', async ({ page }) => {
  await entrar(page, 'elena.rios@techcorp.demo');
  await irASeccion(page, 'Analítica');
  await expect(page.getByText(/toda la empresa/)).toBeVisible();
  await expect(page.getByRole('table', { name: 'Absentismo por grupo' })).toBeVisible();
  const csv = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar CSV' }).click();
  const fichero = await csv;
  expect(fichero.suggestedFilename()).toMatch(/\.csv$/);
  expect(await fichero.failure()).toBeNull();

  await page.getByRole('tab', { name: 'Puntualidad' }).click();
  await expect(page).toHaveURL(/ver=puntualidad/);
});

test('una gestora ve su departamento', async ({ page }) => {
  await entrar(page, 'marta.sanchez@techcorp.demo');
  await irASeccion(page, 'Analítica');
  await expect(page.getByText(/departamento de /)).toBeVisible();
});
