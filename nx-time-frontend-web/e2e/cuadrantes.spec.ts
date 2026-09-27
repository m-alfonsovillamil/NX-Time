/**
 * El editor de cuadrantes contra el backend de verdad, sin escribir nada:
 * las plantillas de la demo y el cuadrante de una persona. Crear una
 * plantilla, asignarla (con el aviso de jornada del servidor) y poner una
 * excepción se comprobaron a mano al cerrar W7c (ver su PR).
 */

import { expect, test } from '@playwright/test';

test('editor de cuadrantes para una gestora', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('marta.sanchez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await page.getByRole('navigation', { name: 'Menú principal' }).first().getByRole('link', { name: 'Cuadrantes' }).click();
  await expect(page.getByRole('heading', { name: /^Semana del / })).toBeVisible();

  await page.getByRole('tab', { name: 'Plantillas' }).click();
  await expect(page).toHaveURL(/ver=plantillas/);
  await expect(page.getByRole('button', { name: 'Nueva plantilla' })).toBeVisible();

  await page.getByRole('tab', { name: 'Por persona' }).click();
  await page.getByLabel('Persona', { exact: true }).selectOption({ label: 'Javier' });
  await expect(page.getByRole('heading', { name: 'Las próximas dos semanas' })).toBeVisible();
});
