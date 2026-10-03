/**
 * Avisos, mi perfil y ajustes contra el backend de verdad, sin escribir nada en
 * la base: el menú de usuario lleva a su sitio, la campana a todos los avisos,
 * y el tema elegido sobrevive a recargar (y la sesión también, ADR 030).
 *
 * Subir el CV, descargar mis datos y pedir el borrado se comprueban a mano al
 * cerrar la fase (ver el PR de W3b): cada ejecución los dejaría hechos en la
 * base de demo. La foto sí se sube: se rompió en producción sin que lo viera
 * ningún test (1/10/2026), y subirla otra vez solo sustituye la anterior.
 */

import { expect, test } from '@playwright/test';

import { entrar } from './ayudas';

/** Un PNG de 8 × 8 de un solo color: lo más pequeño que el servidor acepta como foto. */
const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEUlEQVR4nGOQz3+NFTEMLQkAoOReQWgyRl8AAAAASUVORK5CYII=';

// En el CI, la web se sirve con la CSP de render.yaml (vite.config.ts): la foto
// se pinta desde un blob:, y si la CSP no lo admite, el <img> no carga.
test('la foto de perfil subida se ve en la página', async ({ page }) => {
  await entrar(page, 'javier.lopez@techcorp.demo');
  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('link', { name: 'Mi perfil' }).click();

  await page
    .getByLabel('Cambiar la foto')
    .setInputFiles({ name: 'foto.png', mimeType: 'image/png', buffer: Buffer.from(PNG, 'base64') });
  await expect(page.getByRole('status').getByText('Foto actualizada.')).toBeVisible();

  const foto = page.getByRole('img', { name: /^Foto de / });
  await expect(foto).toBeVisible();
  await expect
    .poll(() => foto.evaluate((img: HTMLImageElement) => (img.complete ? img.naturalWidth : 0)))
    .toBe(256);
});

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
