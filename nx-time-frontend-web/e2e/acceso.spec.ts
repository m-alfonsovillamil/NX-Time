/**
 * Las páginas de acceso sin sesión: desde el login se llega a recuperar la
 * contraseña y a registrar una empresa, y se vuelve.
 *
 * No registra empresas ni cambia contraseñas: dejaría una empresa o una
 * contraseña distinta en la base de demo en cada ejecución. El ciclo entero
 * (pedir el código, leerlo del correo en MailHog, elegir contraseña y entrar
 * con ella, y registrar una empresa y recargar dentro) se comprobó a mano al
 * cerrar W3 (ver el PR de W3c).
 */

import { expect, test } from '@playwright/test';

test('del login a recuperar el acceso y a registrar una empresa', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('link', { name: /olvidado tu contraseña/ }).click();
  await expect(page.getByRole('heading', { name: 'Elegir contraseña' })).toBeVisible();
  await expect(page).toHaveURL(/\/recuperar-acceso$/);
  await page.getByRole('link', { name: 'Volver a entrar' }).click();

  await page.getByRole('link', { name: 'Registrar una empresa' }).click();
  await expect(page.getByRole('heading', { name: 'Registrar empresa' })).toBeVisible();
  await page.getByRole('link', { name: 'Ya tengo cuenta: entrar' }).click();
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
});

/*
 * Lo que hace útil un enlace a la web desde un correo o un aviso. Hasta
 * octubre de 2026 pedía entrar y después dejaba en «Mi jornada»: no se veía
 * porque la única prueba entraba desde `/fichar`.
 */
test('sin sesión, un enlace a una página lleva a entrar y después a esa página', async ({ page }) => {
  await page.goto('/historial');
  await page.getByLabel('Correo electrónico').fill('javier.lopez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Historial' })).toBeVisible();
  await expect(page).toHaveURL(/\/historial$/);
});

test('y si esa página no es para la cuenta que entra, a su jornada y no a un aviso de permisos', async ({ page }) => {
  await page.goto('/informes');
  await page.getByLabel('Correo electrónico').fill('javier.lopez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Mi jornada' })).toBeVisible();
  await expect(page).toHaveURL(/\/fichar$/);
});
