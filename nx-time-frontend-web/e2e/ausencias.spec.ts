/**
 * Ausencias y calendario contra el backend de verdad, sin escribir nada: la
 * lista con su saldo, y un mes con sus festivos (octubre tiene el 12, que el
 * backend calcula y siembra solo).
 *
 * Solicitar una ausencia de verdad se comprueba a mano al cerrar la fase: cada
 * ejecución dejaría una petición más en la base de demo.
 */

import { expect, test } from '@playwright/test';

test('ausencias y calendario: saldo, lista y un festivo nacional', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('javier.lopez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  await menu.getByRole('link', { name: 'Ausencias' }).click();
  await expect(page.getByRole('heading', { name: /^Vacaciones de \d{4}$/ })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Mis solicitudes de ausencia' })).toBeVisible();

  await menu.getByRole('link', { name: 'Calendario' }).click();
  await expect(page.getByRole('table')).toBeVisible();
  // Hasta octubre: su festivo nacional lo pone el backend, sin que nadie lo cargue.
  const siguiente = page.getByRole('button', { name: 'Mes siguiente' });
  while (!(await page.getByRole('heading', { name: /^Octubre de/ }).isVisible())) {
    await siguiente.click();
  }
  await expect(page.getByRole('button', { name: /12 de octubre.*Festivo/ })).toBeVisible();
});
