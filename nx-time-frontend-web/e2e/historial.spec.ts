/**
 * El historial contra el backend de verdad: el menú lleva a él, un periodo
 * trae su total, y los diálogos de una jornada se abren con lo que dice el
 * servidor (las horas de la jornada, lo trabajado para repartir).
 *
 * No envía correcciones: una segunda ejecución encontraría la primera aún
 * pendiente y el servidor respondería 409, con razón. Enviarlas de verdad se
 * comprueba a mano al cerrar cada fase (ver el PR de W2b).
 */

import { expect, test } from '@playwright/test';

const EMPLEADO = { email: 'javier.lopez@techcorp.demo', contrasena: 'demo1234' };

test('el historial: periodos, total y los diálogos de una jornada', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(EMPLEADO.email);
  await page.getByLabel('Contraseña').fill(EMPLEADO.contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();

  await page.getByRole('navigation', { name: 'Menú principal' }).first().getByRole('link', { name: 'Historial' }).click();
  await expect(page).toHaveURL(/\/historial$/);
  await expect(page.getByRole('heading', { name: 'Mi historial' })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Mis jornadas' })).toBeVisible();

  await test.step('el mes anterior se pide entero y trae su total', async () => {
    await page.getByRole('tab', { name: 'Mes anterior' }).click();
    await expect(page.getByText(/^Del .* al .* · .* trabajadas$/)).toBeVisible();
  });

  const primeraCerrada = page.getByRole('group', { name: /^Acciones de la jornada del / }).first();

  await test.step('pedir corrección parte de las horas de la jornada', async () => {
    await primeraCerrada.getByRole('button', { name: 'Pedir corrección' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Pedir corrección' });
    await expect(dialogo.getByLabel('Hora de entrada')).toHaveValue(/^\d{2}:\d{2}$/);
    await dialogo.getByRole('button', { name: 'Cancelar' }).click();
    await expect(dialogo).toBeHidden();
  });

  await test.step('repartir enseña lo trabajado ese día', async () => {
    await primeraCerrada.getByRole('button', { name: 'Proyectos' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Repartir por proyecto' });
    await expect(dialogo.getByText(/^Trabajado ese día: /)).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialogo).toBeHidden();
  });
});
