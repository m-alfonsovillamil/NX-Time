/**
 * Proyectos y calendario laboral contra el backend de verdad, sin escribir
 * nada: la demo trae proyectos con horas y los festivos nacionales. Crear,
 * asignar, sacar y los festivos de empresa se comprobaron a mano al cerrar
 * W6b (ver su PR).
 */

import { expect, test } from '@playwright/test';

test('proyectos con su detalle y el calendario laboral', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('marta.sanchez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  await menu.getByRole('link', { name: 'Proyectos' }).click();
  const tabla = page.getByRole('table', { name: 'Proyectos de la empresa' });
  await tabla.getByRole('button', { name: /^Ver / }).first().click();
  await expect(page).toHaveURL(/proyecto=\d+/);
  await expect(page.getByRole('heading', { name: 'Quién ha estado' })).toBeVisible();

  await menu.getByRole('link', { name: 'Calendario laboral' }).click();
  await expect(page.getByRole('heading', { name: 'Calendario laboral' })).toBeVisible();
  await expect(page.getByRole('heading', { name: /^Festivos de / })).toBeVisible();
});
