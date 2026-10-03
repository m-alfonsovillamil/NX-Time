/**
 * Gestión de ofertas y canal de denuncias contra el backend de verdad, sin
 * escribir nada: publicar avisaría a toda la plantilla y valorar o cerrar un
 * expediente no se deshace. Todo eso se comprobó a mano al cerrar W6e (ver su
 * PR); aquí, que se ven las candidaturas con su CV y la bandeja del canal.
 */

import { expect, test } from '@playwright/test';

import { irASeccion } from './ayudas';

async function entrar(page: import('@playwright/test').Page, email: string) {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
}

test('gestión de ofertas con sus candidaturas', async ({ page }) => {
  await entrar(page, 'marta.sanchez@techcorp.demo');
  await irASeccion(page, 'Gestión de ofertas');
  // La vacante de la demo que ya tiene candidaturas.
  await page.getByRole('group', { name: /Acciones de «Desarrollador/ }).getByRole('button', { name: 'Candidaturas' }).click();
  await expect(page).toHaveURL(/oferta=\d+/);
  await expect(page.getByRole('list', { name: 'Candidaturas' }).getByRole('button', { name: 'Descargar el CV' }).first()).toBeVisible();
});

test('bandeja del canal de denuncias para quien instruye', async ({ page }) => {
  await entrar(page, 'raul.ortega@techcorp.demo');
  await irASeccion(page, 'Denuncias recibidas');
  await expect(page.getByRole('heading', { name: 'Canal interno' })).toBeVisible();
  await page.getByRole('button', { name: /^Abrir/ }).first().click();
  await expect(page.getByRole('dialog').getByText(/^(Denuncia anónima|La presentó)/)).toBeVisible();
});
