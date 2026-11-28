/**
 * El justificante de una ausencia (ADR 062) en un navegador de verdad: el
 * multipart solo se puede comprobar aquí (ver `paginas/cuenta/adjuntos.ts`).
 *
 * Escribe en la base de demo, pero lo deja como estaba: el ADMIN activa los
 * justificantes, Javier adjunta uno y lo quita, y el ADMIN los deja como los
 * encontró (desde el 28/11/2026 la demo de TechCorp los trae encendidos).
 */

import { expect, test, type Browser, type Page } from '@playwright/test';

import { entrar, irASeccion } from './ayudas';

const PDF = Buffer.from('%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF', 'ascii');

/** Los deja como se pide y dice cómo estaban, para volver a dejarlos igual. */
async function activarJustificantes(browser: Browser, activar: boolean): Promise<boolean> {
  const page: Page = await browser.newPage();
  await entrar(page, 'raul.ortega@techcorp.demo');
  await irASeccion(page, 'Ajustes de la empresa');
  const tarjeta = page.getByRole('region', { name: 'Justificantes en las ausencias' });
  const casilla = tarjeta.getByLabel('Dejar adjuntar justificantes');
  await expect(casilla).toBeVisible();
  const antes = await casilla.isChecked();
  if (antes !== activar) {
    await casilla.setChecked(activar);
    await tarjeta.getByRole('button', { name: 'Aplicar' }).click();
    if (activar) {
      const dialogo = page.getByRole('dialog', { name: '¿Dejar adjuntar justificantes?' });
      await dialogo.getByRole('button', { name: 'Sí, dejar adjuntarlos' }).click();
      await expect(page.getByRole('status').getByText('Ya se pueden adjuntar justificantes.')).toBeVisible();
    } else {
      await expect(page.getByRole('status').getByText(/^Ya no se pueden adjuntar/)).toBeVisible();
    }
  }
  await page.close();
  return antes;
}

test('una persona adjunta el justificante de su ausencia y lo quita', async ({ browser, page }) => {
  const antes = await activarJustificantes(browser, true);
  try {
    await entrar(page, 'javier.lopez@techcorp.demo');
    await irASeccion(page, 'Ausencias');
    const tabla = page.getByRole('table', { name: 'Mis solicitudes de ausencia' });
    await expect(tabla.getByRole('button', { name: 'Adjuntar justificante' }).first()).toBeVisible();

    const elegir = page.waitForEvent('filechooser');
    await tabla.getByRole('button', { name: 'Adjuntar justificante' }).first().click();
    await (await elegir).setFiles({ name: 'parte.pdf', mimeType: 'application/pdf', buffer: PDF });
    await expect(page.getByRole('status').getByText('Justificante adjuntado. Solo lo veis tú y RRHH.')).toBeVisible();

    const ver = tabla.getByRole('button', { name: 'Ver parte.pdf' });
    await expect(ver).toBeVisible();
    const descarga = page.waitForEvent('download');
    await ver.click();
    expect((await descarga).suggestedFilename()).toBe('parte.pdf');

    await tabla.getByRole('button', { name: 'Quitar' }).click();
    await expect(page.getByRole('status').getByText('Justificante quitado.')).toBeVisible();
    await expect(ver).toBeHidden();
  } finally {
    await activarJustificantes(browser, antes);
  }
});
