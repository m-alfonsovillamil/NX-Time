/**
 * El panel de gestión y las ausencias del equipo contra el backend de verdad,
 * sin decidir nada: aprobar o rechazar cambiaría lo que ven las siguientes
 * ejecuciones. Decidir se comprobó a mano al cerrar W5b (ver su PR).
 */

import { expect, test } from '@playwright/test';

test('panel de gestión y ausencias del equipo de una gestora', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('marta.sanchez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  await menu.getByRole('link', { name: 'Panel de gestión' }).click();
  await expect(page.getByRole('heading', { name: 'Panel de gestión' })).toBeVisible();
  // La demo trae solicitudes de ausencia pendientes: el contador lleva a su bandeja.
  await page.getByRole('link', { name: /^\d+ Ausencias por aprobar$/ }).click();
  await expect(page.getByRole('heading', { name: 'Ausencias del equipo' })).toBeVisible();
  await expect(page.getByRole('list', { name: 'Pendientes' }).getByRole('button', { name: 'Aprobar' }).first()).toBeVisible();

  await page.getByRole('tab', { name: 'Resueltas' }).click();
  await expect(page.getByRole('tabpanel')).toBeVisible();
});
