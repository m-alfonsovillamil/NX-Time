/**
 * Panel de empresa, informes e integridad contra el backend de verdad. Aquí
 * no se escribe nada, así que se prueba todo: el panel con sus cifras, las
 * dos descargas (el Excel y el PDF llegan enteros y con su nombre) y la
 * comprobación completa de la cadena de auditoría.
 */

import { expect, test } from '@playwright/test';

test('panel de empresa, informes e integridad para RRHH', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('elena.rios@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();

  await menu.getByRole('link', { name: 'Panel de empresa' }).click();
  await expect(page.getByRole('heading', { name: 'Horas por empleado' })).toBeVisible();
  // Al empezar el mes puede no haber ninguna jornada terminada todavía (los
  // datos de demo son de días pasados, y el CI va en UTC y la empresa en
  // Madrid), y sin ellas no hay media que pintar. Falló así el 1/10/2026. Las
  // cifras del gráfico las prueba PanelEmpresa en Vitest.
  await expect(page.getByText(/^(La raya es la media del equipo|Nadie ha fichado todavía este mes\.)/)).toBeVisible();

  await menu.getByRole('link', { name: 'Informes' }).click();
  const excel = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar Excel' }).click();
  const fichero = await excel;
  expect(fichero.suggestedFilename()).toMatch(/\.xlsx$/);
  expect(await fichero.failure()).toBeNull();

  await page.getByLabel('Persona', { exact: true }).selectOption({ label: 'Javier' });
  const pdf = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar PDF' }).click();
  const informe = await pdf;
  expect(informe.suggestedFilename()).toMatch(/\.pdf$/);
  expect(await informe.failure()).toBeNull();

  await menu.getByRole('link', { name: 'Integridad de la auditoría' }).click();
  await page.getByRole('button', { name: 'Comprobar la cadena' }).click();
  await expect(page.getByRole('status').getByText('La traza está intacta.')).toBeVisible({ timeout: 30000 });
});

/*
 * Fase Z2 (ADR 032): la zona horaria de la empresa. Se cambia a Canarias y se
 * deja otra vez en Madrid en el mismo test, porque las demás specs leen horas
 * de la misma base de demo.
 */
test('ADMIN cambia la zona horaria de la empresa y la deja como estaba', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('raul.ortega@techcorp.demo');
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await page.getByRole('navigation', { name: 'Menú principal' }).first()
    .getByRole('link', { name: 'Ajustes de la empresa' }).click();
  const zona = page.getByLabel('Zona horaria');
  await expect(zona).toHaveValue('Europe/Madrid');

  for (const [nueva, antes] of [['Atlantic/Canary', 'Europe/Madrid'], ['Europe/Madrid', 'Atlantic/Canary']] as const) {
    await expect(zona).toHaveValue(antes);
    await zona.selectOption(nueva);
    await page.getByRole('button', { name: 'Guardar' }).click();
    const dialogo = page.getByRole('dialog', { name: '¿Cambiar la zona horaria?' });
    await expect(dialogo).toBeVisible();
    await dialogo.getByRole('button', { name: 'Cambiar la zona' }).click();
    await expect(dialogo).toBeHidden();
    await expect(page.getByRole('status').getByText(/^(Ajustes guardados\.|Zona cambiada\.)/)).toBeVisible();
    // Recargar trae la zona guardada, no la del formulario.
    await page.reload();
    await expect(page.getByLabel('Zona horaria')).toHaveValue(nueva);
  }
});
