/**
 * Mi cuadrante, las incidencias y la firma mensual contra el backend de
 * verdad, sin escribir nada: la demo no trae cuadrantes, así que lo que se ve
 * es «sin cuadrante», y la firma enseña los meses terminados de la persona.
 *
 * Con datos (una plantilla con turno partido y noche, una excepción) y
 * firmando un mes de verdad se comprobó a mano al cerrar W4a (ver su PR):
 * firmar deja el mes firmado en la base de demo para las siguientes
 * ejecuciones.
 */

import { expect, test } from '@playwright/test';

import { irASeccion } from './ayudas';

test('cuadrante, incidencias y firma mensual', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('carlos.ruiz@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await irASeccion(page, 'Mi cuadrante');
  await expect(page.getByRole('heading', { name: 'Mi cuadrante' })).toBeVisible();
  await expect(page.getByText(/No tienes cuadrante asignado|Total de la semana/)).toBeVisible();

  await irASeccion(page, 'Incidencias');
  await expect(page.getByRole('heading', { name: 'Incidencias de cuadrante' })).toBeVisible();

  await irASeccion(page, 'Firma mensual');
  await expect(page.getByRole('heading', { name: 'Firma mensual' })).toBeVisible();
  // Algún mes terminado con su estado: la demo trae meses de jornadas.
  await expect(page.getByText(/^(Firmado|Sin firmar|Hay que volver a firmar)$/).first()).toBeVisible();
});
