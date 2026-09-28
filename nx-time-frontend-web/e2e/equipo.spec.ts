/**
 * El historial del equipo contra el backend de verdad: el filtro por persona
 * lo hace el servidor (fase W5), así que al elegir a alguien la tabla solo trae
 * sus jornadas. Lo único que escribe es una jornada de Javier, para que tenga
 * traza de auditoría. Proponer una corrección sobre el
 * fichaje de otra persona se comprobó a mano al cerrar W5a (ver su PR).
 */

import { expect, test } from '@playwright/test';

test('historial del equipo con filtro por persona y la traza de auditoría', async ({ page }) => {
  // Javier ficha primero. Las jornadas que siembra la demo no tienen traza (se
  // insertan sin pasar por el servicio), así que sin esto la primera jornada
  // de Javier solo tenía traza si otra spec había fichado antes con él: pasaba
  // en una base usada y fallaba en la base nueva del CI.
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('javier.lopez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  // Hasta que la jornada ha cargado no se sabe cuál de los dos botones hay.
  await expect(page.getByRole('button', { name: /^Fichar (entrada|salida)$/ })).toBeVisible();
  if (await page.getByRole('button', { name: 'Fichar salida' }).isVisible()) {
    if (await page.getByRole('button', { name: 'Reanudar' }).isVisible()) {
      await page.getByRole('button', { name: 'Reanudar' }).click();
    }
    await page.getByRole('button', { name: 'Fichar salida' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Terminar' }).click();
  }
  await page.getByRole('button', { name: 'Fichar entrada' }).click();
  await page.getByRole('button', { name: 'Fichar salida' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Terminar' }).click();
  await expect(page.getByRole('button', { name: 'Fichar entrada' })).toBeVisible();
  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();

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
