/**
 * Ofertas internas y canal de denuncias contra el backend de verdad, sin
 * escribir nada: presentarse o denunciar dejaría rastro en la base de demo
 * para las siguientes ejecuciones. Presentarse (sin CV y con CV), la denuncia
 * anónima con su código y responder por él se comprobaron a mano al cerrar
 * W4c (ver su PR).
 */

import { expect, test } from '@playwright/test';

test('ofertas internas y canal de denuncias', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('javier.lopez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  await menu.getByRole('link', { name: 'Ofertas internas' }).click();
  await expect(page.getByRole('heading', { name: 'Ofertas internas' })).toBeVisible();
  // La demo trae una vacante abierta a la que Javier ya se presentó.
  await expect(page.getByText(/Ya te has presentado a esta vacante/)).toBeVisible();
  await page.getByRole('tab', { name: 'Mis candidaturas' }).click();
  await expect(page.getByText(/^CV adjunto: /).first()).toBeVisible();

  await menu.getByRole('link', { name: 'Canal de denuncias' }).click();
  await expect(page.getByRole('heading', { name: 'Canal de denuncias' })).toBeVisible();
  // Un código que no existe: el 404 del servidor, sin confirmar cuáles valen.
  await page.getByLabel('Código de seguimiento').fill('NO-EXISTE-1234');
  await page.getByRole('button', { name: 'Buscar' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('dialog')).toBeHidden();
});
