/**
 * La plantilla y los departamentos contra el backend de verdad, sin escribir
 * nada. RRHH ve la plantilla entera (gestores incluidos, fase W6), que es lo
 * que permite poner departamento a quien la app no dejaba. Dar de alta, de
 * baja y asignar departamento se comprobaron a mano al cerrar W6a (ver su PR).
 */

import { expect, test } from '@playwright/test';

test('plantilla entera y departamentos para RRHH', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('elena.rios@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  await menu.getByRole('link', { name: 'Plantilla' }).click();
  const tabla = page.getByRole('table', { name: 'Personas de la empresa' });
  // Marta es GESTOR: en «mis empleados» no salía.
  await expect(tabla.getByRole('cell', { name: 'Marta', exact: true })).toBeVisible();
  await expect(tabla.getByRole('cell', { name: 'Gestor' }).first()).toBeVisible();

  await menu.getByRole('link', { name: 'Departamentos' }).click();
  await expect(page.getByRole('heading', { name: 'Departamentos' })).toBeVisible();
  await expect(page.getByLabel('Nombre del departamento')).toBeVisible();
});
