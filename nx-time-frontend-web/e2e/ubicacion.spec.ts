/**
 * Presencial o a distancia y la ubicación al fichar (ADR 055 y 056), contra el
 * backend de verdad y en los tres proyectos: en un iPhone, la web es el único
 * cliente. La geolocalización del navegador de verdad es lo que Vitest no puede
 * probar, y en el CI, con el build, también la `Permissions-Policy` de
 * `render.yaml`: con `geolocation=()` todo saldría «sin ubicación».
 *
 * Enciende los dos ajustes y los deja apagados al acabar: las demás specs fichan
 * con esta misma empresa y no esperan esos diálogos.
 */

import { expect, test, type Browser, type Locator, type Page } from '@playwright/test';

import { entrar, irASeccion } from './ayudas';

const AVISO = 'Tu empresa apunta dónde fichas';

async function cambiarAjuste(page: Page, casilla: string, valor: boolean, confirmar?: string) {
  const marca = page.getByLabel(casilla);
  if ((await marca.isChecked()) === valor) return;
  await marca.setChecked(valor);
  const tarjeta = page.locator('.nx-columna', { has: marca }).last();
  await tarjeta.getByRole('button', { name: 'Aplicar' }).click();
  if (confirmar !== undefined) await page.getByRole('dialog').getByRole('button', { name: confirmar }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.getByLabel(casilla)).toBeChecked({ checked: valor });
  await expect(tarjeta.getByRole('button', { name: 'Aplicar' })).toBeDisabled();
}

/** En una ventana de escritorio aparte: es el ADMIN quien lo enciende, no quien ficha. */
async function ajustes(browser: Browser, encender: boolean) {
  // El tamaño, explícito: `newContext` hereda el móvil del proyecto, y aquí se usa el menú lateral.
  const contexto = await browser.newContext({
    baseURL: 'http://localhost:5173',
    viewport: { width: 1280, height: 720 },
    isMobile: false,
    hasTouch: false,
  });
  const page = await contexto.newPage();
  await entrar(page, 'raul.ortega@techcorp.demo');
  await irASeccion(page, 'Ajustes de la empresa');
  await cambiarAjuste(page, 'Pedir la ubicación al entrar y al salir', encender, encender ? 'Pedir la ubicación' : undefined);
  await cambiarAjuste(page, 'Preguntar presencial o a distancia al entrar', encender);
  await contexto.close();
}

function entrada(page: Page) {
  return page.getByRole('button', { name: 'Fichar entrada' });
}

function salida(page: Page) {
  return page.getByRole('button', { name: 'Fichar salida' });
}

/**
 * El aviso sale la primera vez que esta persona ficha en este navegador, y cada
 * test tiene el suyo: se acepta si sale, antes de lo que venga después. No se
 * escribe en `localStorage` para saltarlo: lo que se guarda es interno.
 */
async function entendidoSiAvisa(page: Page, despues: Locator) {
  const aviso = page.getByRole('dialog', { name: AVISO });
  await expect(aviso.or(despues)).toBeVisible();
  if (await aviso.isVisible()) await aviso.getByRole('button', { name: 'Entendido' }).click();
}

async function terminar(page: Page) {
  await salida(page).click();
  const confirmar = page.getByRole('dialog', { name: '¿Terminas la jornada?' });
  await entendidoSiAvisa(page, confirmar);
  await confirmar.getByRole('button', { name: 'Terminar' }).click();
  await expect(entrada(page)).toBeVisible();
}

/** Entra como Javier, sin jornada abierta. */
async function entrarComoJavier(page: Page) {
  await entrar(page, 'javier.lopez@techcorp.demo');
  await expect(entrada(page).or(salida(page))).toBeVisible();
  if (await salida(page).isVisible()) {
    const reanudar = page.getByRole('button', { name: 'Reanudar' });
    if (await reanudar.isVisible()) await reanudar.click();
    await terminar(page);
  }
}

/** Lo que falta tras pulsar «Fichar entrada»: el día no laborable, si toca, la modalidad y el proyecto. */
async function elegirYEmpezar(page: Page, modalidad: string) {
  const laborable = page.getByRole('dialog', { name: 'Hoy no es un día laborable' });
  const pregunta = page.getByRole('dialog', { name: '¿Dónde trabajas hoy?' });
  await entendidoSiAvisa(page, laborable.or(pregunta));
  await expect(laborable.or(pregunta)).toBeVisible();
  if (await laborable.isVisible()) await laborable.getByRole('button', { name: 'Iniciar igualmente' }).click();
  await pregunta.getByRole('button', { name: modalidad }).click();

  const proyecto = page.getByRole('dialog', { name: '¿En qué proyecto vas a trabajar?' });
  await expect(proyecto.or(salida(page))).toBeVisible();
  if (await proyecto.isVisible()) await proyecto.getByRole('button', { name: 'Empezar' }).click();
  await expect(salida(page)).toBeVisible();
}

/** Por el menú de cada pantalla: en WebKit, recargar en localhost pierde la sesión (ver `movil.spec`). */
async function irAlHistorial(page: Page) {
  const barra = page.locator('nav.nx-inferior');
  if (!(await barra.isVisible())) return irASeccion(page, 'Historial');
  const directo = barra.getByRole('link', { name: 'Historial', exact: true });
  if (await directo.isVisible()) return directo.click();
  await barra.getByRole('button', { name: 'Más' }).click();
  const todas = page.getByRole('dialog', { name: 'Todas las secciones' });
  const enlace = todas.getByRole('link', { name: 'Historial', exact: true });
  if (!(await enlace.isVisible())) {
    await todas
      .locator('.nx-subgrupo')
      .filter({ has: page.getByRole('link', { name: 'Historial', exact: true, includeHidden: true }) })
      .locator(':scope > button[aria-expanded="false"]')
      .click();
  }
  await enlace.click();
}

test.describe('con la ubicación y la modalidad encendidas', () => {
  test.describe.configure({ mode: 'serial' });
  test.beforeAll(async ({ browser }) => ajustes(browser, true));
  test.afterAll(async ({ browser }) => ajustes(browser, false));

  test.describe('con permiso', () => {
    test.use({ geolocation: { latitude: 42.24061, longitude: -8.72069, accuracy: 25 }, permissions: ['geolocation'] });

    test('la primera vez avisa, y la jornada apunta dónde se entró y se salió', async ({ page }) => {
      await entrar(page, 'javier.lopez@techcorp.demo');
      await expect(entrada(page).or(salida(page))).toBeVisible();
      if (await salida(page).isVisible()) {
        const reanudar = page.getByRole('button', { name: 'Reanudar' });
        if (await reanudar.isVisible()) await reanudar.click();
        await salida(page).click();
        await page.getByRole('dialog', { name: AVISO }).getByRole('button', { name: 'Entendido' }).click();
        await page.getByRole('dialog', { name: '¿Terminas la jornada?' }).getByRole('button', { name: 'Terminar' }).click();
        await expect(entrada(page)).toBeVisible();
        await entrada(page).click();
      } else {
        await entrada(page).click();
        await page.getByRole('dialog', { name: AVISO }).getByRole('button', { name: 'Entendido' }).click();
      }
      await elegirYEmpezar(page, 'A distancia');
      await terminar(page);

      await irAlHistorial(page);
      const enlace = page.getByRole('link', { name: /^Ver en el mapa: entrada de las / }).first();
      await expect(enlace).toBeVisible();
      // Redondeada a 4 decimales: es lo que guarda el servidor (ADR 056).
      await expect(enlace).toHaveAttribute('href', /mlat=42\.2406&mlon=-8\.7207/);
      await expect(page.getByRole('link', { name: /^Ver en el mapa: salida de las / }).first()).toBeVisible();
      await expect(page.getByText('A distancia').first()).toBeVisible();
    });
  });

  test('sin permiso se ficha igual, y la jornada queda «sin ubicación»', async ({ page }) => {
    // Lo que hace el navegador al decir que no, sin depender de cómo lo pregunta cada motor.
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'geolocation', {
        value: {
          getCurrentPosition: (_bien: unknown, mal: (e: unknown) => void) => mal({ code: 1, PERMISSION_DENIED: 1 }),
        },
      });
    });
    await entrarComoJavier(page);

    await entrada(page).click();
    await elegirYEmpezar(page, 'Presencial');
    await terminar(page);

    await irAlHistorial(page);
    await expect(page.getByText('Sin ubicación').first()).toBeVisible();
  });
});
