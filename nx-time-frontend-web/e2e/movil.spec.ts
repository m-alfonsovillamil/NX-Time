/**
 * La web en el móvil (fase W8). Mientras no haya app de iOS, la web ES el
 * cliente de quien tenga iPhone, así que esto corre en dos proyectos de
 * `playwright.config.ts`: `movil` (Chromium, como un Android) e `iphone`
 * (WebKit, el motor de Safari, que es el único que hay en un iPhone).
 *
 * Lo que se comprueba es lo que en escritorio no se ve:
 *
 * - que el menú es la barra inferior con «Más», y no la lateral;
 * - que se puede hacer una jornada entera con el dedo;
 * - que **ninguna página se sale por los lados**. Se recorren todas las de cada
 *   cuenta de demo, como en `accesibilidad.spec.ts`. Y no basta con mirar la
 *   página: la tabla va dentro de un contenedor con `overflow-x: auto`, así que
 *   una tabla que no se hubiera vuelto tarjetas se desplazaría dentro de su
 *   caja sin ensanchar la página. También se mira eso.
 *
 * **Lo que aquí NO se puede probar: que en WebKit recargar mantiene la
 * sesión.** Las cookies de la sesión son `Secure` (ADR 030). Sobre
 * `http://localhost`, que es como corren estos tests, WebKit las guarda pero ni
 * las envía ni deja que la página lea `nx_csrf`, así que tras recargar la web
 * no intenta renovar y vuelve al login. Chrome sí hace la excepción de
 * localhost, y `jornada.spec.ts` lo prueba ahí. En producción todo va por
 * https y esto no aplica, pero en Safari solo se ha comprobado a mano, en un
 * iPhone de verdad.
 */

import { expect, test, type Page } from '@playwright/test';

import { CUENTAS, desplegarMenu, entrar, paginaLista } from './ayudas';

/** Lo que se sale por la derecha: la página, o algo que se desplaza de lado. */
async function desbordes(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const fuera: string[] = [];
    const ancho = document.documentElement.clientWidth;
    if (document.documentElement.scrollWidth > ancho + 1) {
      fuera.push(`la página mide ${document.documentElement.scrollWidth} px en una pantalla de ${ancho}`);
    }
    for (const el of document.querySelectorAll<HTMLElement>('.nx-tabla-contenedor')) {
      if (el.scrollWidth > el.clientWidth + 1) {
        const tabla = el.querySelector('caption')?.textContent ?? 'sin título';
        fuera.push(`la tabla «${tabla}» se desplaza de lado (${el.scrollWidth} px en ${el.clientWidth})`);
      }
    }
    return fuera;
  });
}

test('el menú del móvil: barra inferior y «Más» con todas las secciones', async ({ page }) => {
  await entrar(page, 'elena.rios@techcorp.demo');

  const barra = page.locator('nav.nx-inferior');
  await expect(barra).toBeVisible();
  await expect(page.locator('nav.nx-lateral')).toBeHidden();

  await barra.getByRole('button', { name: 'Más' }).click();
  const todas = page.getByRole('dialog', { name: 'Todas las secciones' });
  await expect(todas).toBeVisible();
  // En «Más», el mismo menú por apartados: Plantilla está en Organización, cerrado.
  await todas.getByRole('button', { name: 'Organización' }).click();
  await todas.getByRole('link', { name: 'Plantilla' }).click();
  await expect(todas).toBeHidden();
  await expect(page).toHaveURL(/\/plantilla$/);
  await expect(page.getByRole('table', { name: 'Personas de la empresa' })).toBeVisible();
});

test('una jornada entera con el dedo', async ({ page }) => {
  await entrar(page, 'javier.lopez@techcorp.demo');
  const entrada = page.getByRole('button', { name: 'Fichar entrada' });
  const salida = page.getByRole('button', { name: 'Fichar salida' });
  await expect(entrada.or(salida)).toBeVisible();

  const terminar = async () => {
    await salida.tap();
    const dialogo = page.getByRole('dialog', { name: '¿Terminas la jornada?' });
    await dialogo.getByRole('button', { name: 'Terminar' }).tap();
    await expect(entrada).toBeVisible();
  };

  if (await salida.isVisible()) {
    const reanudar = page.getByRole('button', { name: 'Reanudar' });
    if (await reanudar.isVisible()) await reanudar.tap();
    await terminar();
  }

  await entrada.tap();
  await expect(page.getByText('Trabajando')).toBeVisible();
  await page.getByRole('button', { name: 'Pausar' }).tap();
  await expect(page.getByText('En pausa', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Reanudar' }).tap();
  await expect(page.getByText('Trabajando')).toBeVisible();
  await terminar();
});

for (const { rol, email } of CUENTAS) {
  test(`ninguna página de ${rol} se sale por los lados`, async ({ page }) => {
    await entrar(page, email);
    const barra = page.locator('nav.nx-inferior');

    // Las rutas salen del menú completo de «Más», que tiene todas.
    await barra.getByRole('button', { name: 'Más' }).click();
    const todas = page.getByRole('dialog', { name: 'Todas las secciones' });
    await desplegarMenu(todas);
    const rutas = await todas
      .getByRole('link')
      .evaluateAll((enlaces) => enlaces.map((a) => a.getAttribute('href') ?? ''));
    await page.keyboard.press('Escape');
    expect(rutas.length).toBeGreaterThan(3);

    for (const ruta of rutas) {
      await barra.getByRole('button', { name: 'Más' }).click();
      await todas.locator(`a[href="${ruta}"]`).click();
      await paginaLista(page, ruta);
      expect.soft(await desbordes(page), `${ruta} (${rol})`).toEqual([]);
    }
  });
}
