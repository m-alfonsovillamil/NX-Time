/**
 * Las capturas de la web para el README (`docs/capturas/web/`), sacadas contra
 * el backend de verdad con los datos de demo, como las de la app Android.
 *
 * No es un test: no corre con `npm run e2e` ni en el CI. Solo existe el
 * proyecto `capturas` cuando se lanza `npm run capturas` (ver
 * `playwright.config.ts`). Repetirlas tras cambiar una pantalla es ese comando,
 * con el backend levantado con `dev,demo`, mejor sobre una base recién creada.
 */

import { chromium, devices, expect, test, type Page } from '@playwright/test';

import { entrar, irASeccion, paginaLista } from './ayudas';

const DESTINO = '../docs/capturas/web';

async function ir(page: Page, ruta: string): Promise<void> {
  // Desde octubre de 2026 el menú va por apartados plegables: se abre el de
  // la página, como haría una persona, y el lateral se deja arriba del todo.
  const lateral = page.locator('nav.nx-lateral');
  const enlace = lateral.locator(`a[href="${ruta}"]`);
  if (!(await enlace.isVisible())) {
    await lateral.locator('.nx-subgrupo').filter({ has: page.locator(`a[href="${ruta}"]`) }).locator(':scope > button').click();
  }
  await enlace.click();
  await paginaLista(page, ruta);
  await lateral.evaluate((nav) => nav.scrollTo(0, 0));
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

  test('RRHH: historial del equipo, plantilla, analítica e integridad', async ({ page }) => {
    await entrar(page, 'elena.rios@techcorp.demo');
    await ir(page, '/equipo');
    await capturar(page, 'web-02-historial-equipo');
    await ir(page, '/plantilla');
    await capturar(page, 'web-03-plantilla');
    await ir(page, '/analitica');
    await capturar(page, 'web-04-analitica');
    // La integridad con la cadena recién comprobada: el estado, en verde, es lo
    // que cuenta la página.
    await ir(page, '/integridad');
    await page.getByRole('button', { name: 'Comprobar la cadena' }).click();
    await expect(page.getByRole('status').getByText('La traza está intacta.')).toBeVisible({ timeout: 30_000 });
    await page.mouse.move(1400, 880);
    await capturar(page, 'web-10-integridad');
  });

  test('la pantalla de entrar, con su panel de marca', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByLabel('Correo electrónico')).toBeVisible();
    // Los botones de «Entrar con Google / Microsoft» (ADR 036) llegan del
    // servidor un instante después, y solo si este tiene el SSO configurado:
    // para que salgan en la captura, el backend tiene que arrancarse con él
    // (ver la cabecera de sso.spec.ts). Sin él, la captura sale sin botones.
    await page.waitForLoadState('networkidle');
    await capturar(page, 'web-12-acceso');
  });

  test('gestora: el calendario laboral y el editor de cuadrantes', async ({ page }) => {
    await entrar(page, 'marta.sanchez@techcorp.demo');
    // El calendario laboral: el mes en rejilla con sus festivos, y la lista al lado.
    await ir(page, '/calendario-laboral');
    await expect(page.getByRole('heading', { name: /^Festivos de / })).toBeVisible();
    await page.mouse.move(1400, 880);
    await capturar(page, 'web-11-calendario-laboral');
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

  // La última del escritorio: deja una jornada de segundos en el historial,
  // que no tiene que salir en la captura del historial del equipo.
  test('empleado: la jornada en marcha', async ({ page }) => {
    // Más alta que las demás: fichar va arriba y el resumen debajo, y a 900 px
    // la captura cortaba las cifras y el gráfico por la mitad.
    await page.setViewportSize({ width: 1440, height: 1060 });
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

  test('ADMIN: el menú por apartados plegables', async ({ page }) => {
    await entrar(page, 'raul.ortega@techcorp.demo');
    // La página abierta despliega su apartado; se abre otro para que se vea
    // cómo queda con dos abiertos y el resto plegado.
    await irASeccion(page, 'Plantilla');
    await paginaLista(page, '/plantilla');
    const lateral = page.locator('nav.nx-lateral');
    await lateral.getByRole('button', { name: 'Informes y control' }).click();
    // «Jornada y fichajes» se abrió al entrar (en /fichar): se pliega, que es
    // como lo dejaría quien ya no está en esa página.
    await lateral.getByRole('button', { name: 'Jornada y fichajes' }).click();
    await lateral.evaluate((nav) => nav.scrollTo(0, 0));
    await page.mouse.move(1400, 880);
    await capturar(page, 'web-08-menu-por-apartados');
  });
});

test.describe('tablet', () => {
  test('el kiosco de fichaje en una tablet', async ({ browser }) => {
    // Un Chromium aparte con una cámara de prueba (el patrón que se mueve), que
    // es lo que ve una tablet de verdad mientras espera una tarjeta.
    const conCamara = await chromium.launch({
      args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'],
    });
    const tablet = await (
      await conCamara.newContext({
        baseURL: 'http://localhost:5173',
        viewport: { width: 1180, height: 820 },
        hasTouch: true,
        permissions: ['camera'],
      })
    ).newPage();
    await tablet.goto('/kiosco');
    const codigo = (await tablet.locator('.nx-kiosco__codigo').textContent())?.replace(/\s/g, '') ?? '';
    const admin = await (await browser.newContext()).newPage();
    await entrar(admin, 'raul.ortega@techcorp.demo');
    await irASeccion(admin, 'Ajustes de la empresa');
    await admin.getByLabel('Código de la tablet').fill(codigo);
    await admin.getByLabel('Nombre del kiosco').fill('Entrada del almacén');
    await admin.getByRole('button', { name: 'Dar de alta' }).click();
    await expect(tablet.getByText('Entrada del almacén')).toBeVisible({ timeout: 15_000 });
    await expect(tablet.getByText('El servidor está despertando')).toBeHidden({ timeout: 15_000 });
    await tablet.waitForTimeout(1500);
    await capturar(tablet, 'web-09-kiosco');

    // Se deja como estaba: el kiosco, revocado.
    await admin.reload();
    // Todos los que se llamen así: una captura que falló a medias deja el suyo.
    const activos = admin
      .getByRole('listitem')
      .filter({ hasText: 'Entrada del almacén' })
      .getByRole('button', { name: 'Revocar' });
    while ((await activos.count()) > 0) {
      await activos.first().click();
      await admin.getByRole('dialog').getByRole('button', { name: 'Revocar' }).click();
      await expect(admin.getByRole('dialog')).toBeHidden();
    }
    await conCamara.close();
  });
});
