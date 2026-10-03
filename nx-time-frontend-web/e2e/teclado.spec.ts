/**
 * Lo que axe no puede comprobar (fase W8): que se puede usar la web **solo con
 * el teclado**. Entrar, saltar el menú, fichar y manejar un diálogo: el foco
 * entra en él, no se escapa con el tabulador, Escape lo cierra y el foco vuelve
 * al botón que lo abrió.
 *
 * Ningún clic en todo el recorrido: si un paso solo se pudiera hacer con el
 * ratón, este test no llegaría al final.
 */

import { expect, test, type Locator, type Page } from '@playwright/test';

/** Tabula hasta que el foco llega a `destino`, como haría una persona. */
async function tabularHasta(page: Page, destino: Locator, maximo = 40): Promise<void> {
  for (let i = 0; i < maximo; i++) {
    if (await destino.evaluate((el) => el === document.activeElement)) return;
    await page.keyboard.press('Tab');
  }
  throw new Error(`El tabulador no llega a ${destino.toString()} en ${maximo} pulsaciones`);
}

test('entrar, saltar el menú, fichar y usar un diálogo solo con el teclado', async ({ page }) => {
  await page.goto('/');

  await test.step('el login se rellena y se envía con el teclado', async () => {
    await tabularHasta(page, page.getByLabel('Correo electrónico'));
    await page.keyboard.type('javier.lopez@techcorp.demo');
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Contraseña')).toBeFocused();
    await page.keyboard.type('demo1234');
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
  });

  await test.step('lo primero del tabulador es «Saltar al contenido», y salta', async () => {
    await page.keyboard.press('Tab');
    const saltar = page.getByRole('link', { name: 'Saltar al contenido' });
    await expect(saltar).toBeFocused();
    // Escondido hasta que tiene el foco: si no se viera, no serviría de nada.
    await expect(saltar).toBeInViewport();
    await page.keyboard.press('Enter');
    await expect(page.locator('#contenido')).toBeFocused();
  });

  const entrada = page.getByRole('button', { name: 'Fichar entrada' });
  const salida = page.getByRole('button', { name: 'Fichar salida' });
  await expect(entrada.or(salida)).toBeVisible();

  // Una jornada abierta de otra ejecución se cierra antes (con el ratón: esto
  // es preparación, no parte de lo que se comprueba).
  if (await salida.isVisible()) {
    const reanudar = page.getByRole('button', { name: 'Reanudar' });
    if (await reanudar.isVisible()) await reanudar.click();
    await salida.click();
    await page.getByRole('dialog').getByRole('button', { name: 'Terminar' }).click();
    await page.locator('#contenido').focus();
  }

  await test.step('fichar con el teclado', async () => {
    await tabularHasta(page, entrada);
    await page.keyboard.press('Enter');
    await expect(salida).toBeVisible();
  });

  const dialogo = page.getByRole('dialog', { name: '¿Terminas la jornada?' });
  const focoDentro = () => dialogo.evaluate((d) => d.contains(document.activeElement));
  // Tras el último botón, Chrome pasa el foco a su propia interfaz (la barra
  // de direcciones) antes de volver al diálogo, y desde la página eso se ve
  // como `body`. Es lo que hace `showModal()` a propósito, para que una página
  // no pueda atrapar a nadie. Lo que no puede pasar es que el foco caiga en
  // algo de la página que está detrás.
  const focoNoSeEscapa = () =>
    dialogo.evaluate((d) => d.contains(document.activeElement) || document.activeElement === document.body);

  await test.step('el diálogo recibe el foco y no lo deja escapar', async () => {
    await tabularHasta(page, salida);
    await page.keyboard.press('Enter');
    await expect(dialogo).toBeVisible();
    expect(await focoDentro()).toBe(true);
    let dentro = 0;
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press('Tab');
      expect(await focoNoSeEscapa(), `el foco se escapó del diálogo en el Tab ${i + 1}`).toBe(true);
      if (await focoDentro()) dentro++;
    }
    // Y vuelve: con dos botones, cuatro de cada seis pulsaciones caen dentro.
    expect(dentro).toBeGreaterThanOrEqual(4);
  });

  await test.step('Escape lo cierra y el foco vuelve al botón que lo abrió', async () => {
    await page.keyboard.press('Escape');
    await expect(dialogo).toBeHidden();
    await expect(salida).toBeFocused();
  });

  await test.step('y se termina la jornada sin ratón', async () => {
    // Con el diálogo recién cerrado, en el runner de Linux el primer Enter a
    // veces se pierde (pasó varias veces en octubre de 2026). El foco sí está
    // en el botón; se vuelve a pulsar, con el teclado, mientras no se abra.
    await expect(async () => {
      if (!(await dialogo.isVisible())) await page.keyboard.press('Enter');
      await expect(dialogo).toBeVisible({ timeout: 1000 });
    }).toPass({ timeout: 8000 });
    await tabularHasta(page, dialogo.getByRole('button', { name: 'Terminar' }));
    await page.keyboard.press('Enter');
    await expect(entrada).toBeVisible();
  });
});
