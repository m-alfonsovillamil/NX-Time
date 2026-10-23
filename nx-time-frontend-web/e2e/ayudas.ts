/**
 * Lo que comparten las specs que recorren TODAS las páginas (accesibilidad,
 * móvil y carga por áreas, fase W8).
 */

import { createHmac } from 'node:crypto';

import { expect, type Locator, type Page } from '@playwright/test';

/**
 * Va a una sección por el menú lateral. Desde el 1/10/2026 el menú va por
 * apartados plegables: si el de la sección está cerrado, lo abre antes, como
 * haría una persona. `exact`, porque «Historial» no es «Historial del equipo».
 */
export async function irASeccion(page: Page, nombre: string): Promise<void> {
  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  const enlace = menu.getByRole('link', { name: nombre, exact: true });
  if (!(await enlace.isVisible())) {
    const apartado = menu
      .locator('.nx-subgrupo')
      .filter({ has: page.getByRole('link', { name: nombre, exact: true, includeHidden: true }) });
    await apartado.locator(':scope > button[aria-expanded="false"]').click();
  }
  await enlace.click();
}

/**
 * Lo que falta tras pulsar «Fichar entrada» si hoy no es laborable.
 *
 * Un festivo (o con una ausencia aprobada) la web no ficha sin preguntar: abre
 * un diálogo, y la jornada no empieza hasta confirmarlo. Las specs que fichan
 * no lo tenían en cuenta y la suite entera se ponía en rojo cada festivo
 * nacional; se vio el 12/10/2026. Un día normal esto no hace nada.
 *
 * `pulsar` es cómo se pulsa en esa spec: con el ratón, con el dedo o con el
 * teclado.
 */
export async function iniciarAunqueNoSeaLaborable(
  page: Page,
  pulsar: (boton: Locator) => Promise<void> = (boton) => boton.click(),
): Promise<void> {
  const dialogo = page.getByRole('dialog', { name: 'Hoy no es un día laborable' });
  const enMarcha = page.getByRole('button', { name: 'Fichar salida' });
  await expect(dialogo.or(enMarcha)).toBeVisible();
  if (await dialogo.isVisible()) await pulsar(dialogo.getByRole('button', { name: 'Iniciar igualmente' }));
}

/** Abre todos los apartados de un menú, para las specs que recorren todos sus enlaces. */
export async function desplegarMenu(menu: Locator): Promise<void> {
  const cerrados = menu.locator('button[aria-expanded="false"]');
  while ((await cerrados.count()) > 0) await cerrados.first().click();
}

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
 * La operadora de plataforma de la demo (`application-dev.yml`). Al operador se
 * le exige el segundo factor (ADR 051): la demo se lo deja activado con un
 * secreto conocido (`DemoDataSeeder.SECRETO_TOTP_DE_LUCIA`).
 */
export const OPERADORA = 'lucia.moreno@techcorp.demo';
export const SECRETO_TOTP_DE_LUCIA = Buffer.from('nxtime-demo-lucia-2f', 'ascii');

/** El código TOTP (RFC 6238: SHA-1, 6 cifras, 30 s) de un secreto, `pasos` intervalos después de ahora. */
export function codigoTotp(secreto: Buffer, pasos = 0): string {
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30) + pasos));
  const hmac = createHmac('sha1', secreto).update(contador).digest();
  const desde = (hmac[hmac.length - 1] ?? 0) & 0x0f;
  return String((hmac.readUInt32BE(desde) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

/** Un secreto escrito en Base32 (la clave que enseña la web al activarlo), en bytes. */
export function deBase32(texto: string): Buffer {
  const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bytes: number[] = [];
  let acumulado = 0;
  let bits = 0;
  for (const letra of texto.replace(/\s/g, '')) {
    acumulado = (acumulado << 5) | alfabeto.indexOf(letra);
    bits += 5;
    if (bits >= 8) {
      bytes.push((acumulado >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

/**
 * Entrar con contraseña y código. Un código no vale dos veces (ADR 051), y dos
 * specs seguidas pueden entrar con la misma cuenta dentro de los mismos 30
 * segundos: si el servidor dice que no vale, se espera al código siguiente.
 */
export async function entrarConSegundoFactor(page: Page, email: string, secreto: Buffer): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Código de verificación' })).toBeVisible();

  for (let intento = 0; intento < 3; intento++) {
    // Hasta el principio del intervalo siguiente, y un poco más.
    if (intento > 0) await page.waitForTimeout(30_000 - (Date.now() % 30_000) + 500);
    await page.getByLabel('Código', { exact: true }).fill(codigoTotp(secreto));
    const respuesta = page.waitForResponse((r) => r.url().endsWith('/auth/2fa'));
    await page.getByRole('button', { name: 'Verificar' }).click();
    if ((await respuesta).ok()) break;
  }
  // Dentro: el menú. No «Mi jornada»: al entrar se vuelve a la página de la que
  // se venía, que puede ser otra.
  await expect(page.getByRole('navigation', { name: 'Menú principal' }).first()).toBeVisible();
}

/** Entra como la operadora de la demo, con su código. */
export async function entrarComoOperadora(page: Page): Promise<void> {
  await entrarConSegundoFactor(page, OPERADORA, SECRETO_TOTP_DE_LUCIA);
}

/**
 * Entrar y cerrar el aviso de privacidad de la primera entrada (ADR 047), como
 * lo cierra cualquiera la primera vez: para las specs que miran lo que hay en
 * la página de todos los días, donde el aviso ya no sale.
 */
export async function entrarYCerrarElAviso(page: Page, email: string): Promise<void> {
  const aviso = page.waitForResponse(
    (r) => r.url().includes('/api/v1/perfil/aviso-de-privacidad') && r.request().method() === 'GET',
  );
  await entrar(page, email);
  if ((await (await aviso).json()).visto !== true) {
    await page.getByRole('button', { name: 'Entendido' }).click();
  }
  await expect(page.getByRole('region', { name: 'Tus datos' })).toHaveCount(0);
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
