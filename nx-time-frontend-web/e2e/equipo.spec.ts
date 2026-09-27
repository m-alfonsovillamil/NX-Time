/**
 * El historial del equipo contra el backend de verdad, sin escribir nada: el
 * filtro por persona lo hace el servidor (fase W5), así que al elegir a
 * alguien la tabla solo trae sus jornadas. Proponer una corrección sobre el
 * fichaje de otra persona se comprobó a mano al cerrar W5a (ver su PR).
 */

import { expect, test } from '@playwright/test';

test('historial del equipo con filtro por persona y la traza de auditoría', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('elena.rios@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await page.getByRole('navigation', { name: 'Menú principal' }).first().getByRole('link', { name: 'Historial del equipo' }).click();
  await expect(page.getByRole('heading', { name: 'Historial del equipo' })).toBeVisible();
  const tabla = page.getByRole('table', { name: 'Jornadas del equipo' });
  await expect(tabla.getByRole('cell', { name: 'Javier' }).first()).toBeVisible();

  // Con una persona elegida, la columna «Persona» sobra y no sale.
  await page.getByLabel('Empleado').selectOption({ label: 'Javier' });
  await expect(page).toHaveURL(/persona=\d+/);
  await expect(tabla.getByRole('columnheader', { name: 'Persona' })).toBeHidden();
  await expect(tabla.getByRole('row').nth(1)).toBeVisible();

  await tabla.getByRole('button', { name: 'Auditoría' }).first().click();
  const traza = page.getByRole('dialog', { name: 'Traza de auditoría' });
  await expect(traza.getByText('Jornada creada').first()).toBeVisible();
});
