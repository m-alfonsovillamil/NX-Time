/**
 * El segundo factor contra el backend de verdad (ADR 051): entrar con el
 * código, y activarlo y desactivarlo desde Ajustes como lo haría cualquiera,
 * con el QR, la clave, la contraseña y los códigos de recuperación.
 *
 * Las reglas finas (un código no vale dos veces, la ventana, el límite de
 * intentos, a quién se le exige) las prueba `SegundoFactorIT`. Esto prueba que
 * la web las lleva bien de punta a punta.
 *
 * Sofía, de la Consultora Ibérica, no la usa ninguna otra spec: aquí se le
 * activa y, al final, se le quita con un código de recuperación, para que la
 * spec se pueda repetir sobre la misma base.
 */

import { expect, test, type Page } from '@playwright/test';

import { codigoTotp, deBase32, entrarConSegundoFactor, OPERADORA, SECRETO_TOTP_DE_LUCIA } from './ayudas';

const SOFIA = 'sofia.dominguez@iberica.demo';

async function irAAjustes(page: Page): Promise<void> {
  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('link', { name: 'Ajustes' }).click();
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible();
}

async function cerrarSesion(page: Page): Promise<void> {
  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
}

test('con segundo factor, la contraseña sola no entra: un código malo se dice, el bueno abre la sesión', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(OPERADORA);
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.getByRole('heading', { name: 'Código de verificación' })).toBeVisible();
  await page.getByLabel('Código', { exact: true }).fill('000000');
  await page.getByRole('button', { name: 'Verificar' }).click();
  await expect(page.getByRole('alert')).toContainText('El código no es correcto');

  // El bueno. Si el de este intervalo ya lo gastó otra spec, el servidor dice
  // lo mismo que con uno malo: entonces se espera al siguiente.
  for (let intento = 0; intento < 3; intento++) {
    if (intento > 0) await page.waitForTimeout(30_000 - (Date.now() % 30_000) + 500);
    await page.getByLabel('Código', { exact: true }).fill(codigoTotp(SECRETO_TOTP_DE_LUCIA));
    const respuesta = page.waitForResponse((r) => r.url().endsWith('/auth/2fa'));
    await page.getByRole('button', { name: 'Verificar' }).click();
    if ((await respuesta).ok()) break;
  }
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
});

test('activarlo desde Ajustes, entrar con él y quitarlo con un código de recuperación', async ({ page }) => {
  test.setTimeout(150_000);
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(SOFIA);
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  // Activarlo: el QR y la clave, el código y la contraseña.
  await irAAjustes(page);
  await page.getByRole('button', { name: 'Activarlo' }).click();
  const dialogo = page.getByRole('dialog', { name: 'Activar el segundo factor' });
  await expect(dialogo.getByRole('img', { name: /Código QR/ })).toBeVisible();
  const secreto = deBase32((await dialogo.getByTestId('clave-totp').textContent()) ?? '');
  expect(secreto).toHaveLength(20);
  await dialogo.getByLabel('Código de la aplicación').fill(codigoTotp(secreto));
  await dialogo.getByLabel('Tu contraseña').fill('demo1234');
  await dialogo.getByRole('button', { name: 'Activar' }).click();

  // Los códigos de recuperación, una sola vez.
  const lista = dialogo.getByRole('list', { name: 'Tus códigos de recuperación' });
  await expect(lista.getByRole('listitem')).toHaveCount(10);
  const recuperacion = (await lista.getByRole('listitem').first().textContent()) ?? '';
  expect(recuperacion).toMatch(/^[a-z2-9]{5}-[a-z2-9]{5}$/);
  await dialogo.getByLabel('Los he guardado').check();
  await dialogo.getByRole('button', { name: 'Listo' }).click();
  await expect(page.getByText('Te quedan 10 códigos de recuperación.')).toBeVisible();

  // Desde ahora, la contraseña sola ya no entra.
  await cerrarSesion(page);
  await entrarConSegundoFactor(page, SOFIA, secreto);

  // Quitarlo, con la contraseña y un código de recuperación.
  await irAAjustes(page);
  await page.getByRole('button', { name: 'Desactivarlo' }).click();
  const quitar = page.getByRole('dialog', { name: '¿Desactivar el segundo factor?' });
  await quitar.getByLabel('Tu contraseña').fill('demo1234');
  await quitar.getByLabel('Código').fill(recuperacion);
  await quitar.getByRole('button', { name: 'Desactivar' }).click();
  await expect(page.getByRole('status').getByText('Segundo factor desactivado.')).toBeVisible();
  await expect(page.getByText('No lo tienes activado.')).toBeVisible();
});
