/**
 * Avisos, mi perfil y ajustes contra el backend de verdad, sin escribir nada en
 * la base: el menú de usuario lleva a su sitio, la campana a todos los avisos,
 * y el tema elegido sobrevive a recargar (y la sesión también, ADR 030).
 *
 * Subir el CV y la foto, descargar mis datos y pedir el borrado se comprueban
 * a mano al cerrar la fase (ver el PR de W3b): cada ejecución los dejaría
 * hechos en la base de demo.
 */

import { expect, test } from '@playwright/test';

test('perfil, ajustes y avisos', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('javier.lopez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('link', { name: 'Mi perfil' }).click();
  await expect(page.getByRole('heading', { name: 'Datos laborales' })).toBeVisible();
  await expect(page.getByText('Estos datos los gestiona Recursos Humanos.')).toBeVisible();

  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('link', { name: 'Ajustes' }).click();
  await page.getByLabel('Tema').selectOption('oscuro');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.dataset['tema'])).toBe('oscuro');
  await page.getByLabel('Tema').selectOption('sistema');

  await page.getByRole('button', { name: /^Avisos: / }).click();
  await page.getByRole('link', { name: 'Ver todos' }).click();
  await expect(page.getByRole('heading', { name: 'Avisos', exact: true })).toBeVisible();
});
