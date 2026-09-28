/**
 * Lo que comparten las specs que recorren TODAS las páginas (accesibilidad,
 * móvil y carga por áreas, fase W8).
 */

import { expect, type Page } from '@playwright/test';

export const CUENTAS = [
  { rol: 'EMPLEADO', email: 'javier.lopez@techcorp.demo' },
  { rol: 'GESTOR', email: 'marta.sanchez@techcorp.demo' },
  { rol: 'RRHH', email: 'elena.rios@techcorp.demo' },
  { rol: 'ADMIN', email: 'raul.ortega@techcorp.demo' },
];

export async function entrar(page: Page, email: string): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
}

/**
 * Espera a que la página de `ruta` esté **pintada y con sus datos**.
 *
 * No vale mirar la URL ni `waitForLoadState('networkidle')`, y las dos cosas
 * engañaron al escribir estas specs:
 *
 * - React Router navega dentro de una transición: la URL cambia al instante,
 *   pero en pantalla **sigue la página anterior** hasta que llega el trozo de
 *   JS de la nueva. Un axe pasado en ese momento analiza la página de antes.
 * - `networkidle` es un estado de la carga del documento, y en una aplicación
 *   de una sola página solo hay una: después de la primera, se resuelve al
 *   momento.
 *
 * Lo que sí cambia cuando la página nueva ya está en pantalla es el enlace del
 * menú lateral, que pasa a `aria-current="page"` en el mismo render. En el móvil
 * ese menú está oculto, pero sigue en el DOM. Después, la página pide sus datos
 * y enseña esqueletos hasta tenerlos.
 */
export async function paginaLista(page: Page, ruta: string): Promise<void> {
  await expect(page.locator(`nav.nx-lateral a[href="${ruta}"]`)).toHaveAttribute('aria-current', 'page');
  await expect(page.locator('.nx-esqueleto')).toHaveCount(0);
}
