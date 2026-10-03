/**
 * Horas extra y correcciones contra el backend de verdad, sin decidir nada:
 * la demo trae avisos de horas extra abiertos y correcciones pendientes, y
 * aceptarlos o rechazarlos aquí cambiaría lo que ven las siguientes
 * ejecuciones. Decidir se comprobó a mano al cerrar W4b (ver su PR).
 */

import { expect, test } from '@playwright/test';

import { irASeccion } from './ayudas';

test('horas extra y correcciones de una gestora', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('marta.sanchez@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await irASeccion(page, 'Horas extra');
  await expect(page.getByRole('heading', { name: 'Horas extra' })).toBeVisible();
  await expect(page.getByRole('meter')).toBeVisible();

  // La bandeja del equipo: avisos de otras personas, con sus botones.
  await page.getByRole('tab', { name: 'Pendientes de revisar' }).click();
  await expect(page.getByRole('button', { name: 'Son horas extra' }).first()).toBeVisible();

  await irASeccion(page, 'Correcciones');
  await expect(page.getByRole('heading', { name: 'Correcciones' })).toBeVisible();
  await page.getByRole('tab', { name: 'Las que he pedido' }).click();
  await expect(page.getByRole('tabpanel')).toBeVisible();
});
