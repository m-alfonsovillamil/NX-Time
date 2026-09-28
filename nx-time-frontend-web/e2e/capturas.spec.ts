/**
 * Las capturas de la web para el README (`docs/capturas/web/`), sacadas contra
 * el backend de verdad con los datos de demo, como las de la app Android.
 *
 * No es un test: no corre con `npm run e2e` ni en el CI. Solo existe el
 * proyecto `capturas` cuando se lanza `npm run capturas` (ver
 * `playwright.config.ts`). Repetirlas tras cambiar una pantalla es ese comando,
 * con el backend levantado con `dev,demo`, mejor sobre una base recién creada.
 */

import { devices, expect, test, type Page } from '@playwright/test';

import { entrar, paginaLista } from './ayudas';

const DESTINO = '../docs/capturas/web';

async function ir(page: Page, ruta: string): Promise<void> {
  await page.locator(`nav.nx-lateral a[href="${ruta}"]`).click();
  await paginaLista(page, ruta);
}

async function capturar(page: Page, nombre: string): Promise<void> {
  // Sin el cursor de texto parpadeando ni animaciones a medias.
  await page.screenshot({ path: `${DESTINO}/${nombre}.png`, animations: 'disabled', caret: 'hide' });
}

// El navegador lo fija el proyecto, no un `describe`: se quita del descriptor.
const { defaultBrowserType: _navegador, ...pixel7 } = devices['Pixel 7'];

// El móvil primero: la demo deja a Javier con la jornada de hoy abierta, y la
// captura de escritorio la cierra al terminar.
test.describe('móvil', () => {
  test.use(pixel7);

  for (const [tema, nombre] of [
    ['light', 'web-06-movil'],
    ['dark', 'web-07-movil-oscuro'],
  ] as const) {
    test(`empleado en el móvil, tema ${tema}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: tema });
      await entrar(page, 'javier.lopez@techcorp.demo');
      await expect(page.locator('.nx-esqueleto')).toHaveCount(0);
      await capturar(page, nombre);
    });
  }
});

test.describe('escritorio', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test('empleado: la jornada en marcha', async ({ page }) => {
    await entrar(page, 'javier.lopez@techcorp.demo');
    const entrada = page.getByRole('button', { name: 'Fichar entrada' });
    const salida = page.getByRole('button', { name: 'Fichar salida' });
    await expect(entrada.or(salida)).toBeVisible();
    if (await entrada.isVisible()) await entrada.click();
    await expect(page.getByText('Trabajando')).toBeVisible();
    await page.waitForTimeout(1500);
    await capturar(page, 'web-01-mi-jornada');

    // Se deja como estaba: sin jornada abierta.
    await salida.click();
    await page.getByRole('dialog').getByRole('button', { name: 'Terminar' }).click();
    await expect(entrada).toBeVisible();
  });

  test('RRHH: historial del equipo, plantilla y analítica', async ({ page }) => {
    await entrar(page, 'elena.rios@techcorp.demo');
    await ir(page, '/equipo');
    await capturar(page, 'web-02-historial-equipo');
    await ir(page, '/plantilla');
    await capturar(page, 'web-03-plantilla');
    await ir(page, '/analitica');
    await capturar(page, 'web-04-analitica');
  });

  test('gestora: el editor de cuadrantes', async ({ page }) => {
    await entrar(page, 'marta.sanchez@techcorp.demo');
    await ir(page, '/cuadrantes');
    // La demo no siembra cuadrantes, así que la semana del equipo sale vacía:
    // se enseña el editor de una plantilla nueva (sin guardarla), que es lo
    // propio de esta página.
    await page.getByRole('tab', { name: 'Plantillas' }).click();
    await page.getByRole('button', { name: 'Nueva plantilla' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Nueva plantilla' });
    await dialogo.getByLabel('Nombre', { exact: true }).fill('Turno partido');
    let tramo = 0;
    const anadir = async (dia: string, desde: string, hasta: string) => {
      await dialogo.getByRole('button', { name: `Añadir tramo (${dia})` }).click();
      await dialogo.getByLabel('Desde').nth(tramo).fill(desde);
      await dialogo.getByLabel('Hasta').nth(tramo).fill(hasta);
      tramo++;
    };
    for (const dia of ['Lunes', 'Martes', 'Miércoles', 'Jueves']) {
      await anadir(dia, '09:00', '14:00');
      await anadir(dia, '16:00', '19:00');
    }
    await anadir('Viernes', '08:00', '15:00');
    // Arriba del todo y sin el foco resaltando el último campo.
    await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
    await dialogo.locator('.nx-dialogo__contenido').evaluate((el) => el.scrollTo(0, 0));
    await capturar(page, 'web-05-editor-cuadrantes');
  });
});
