/**
 * Entrar con Google o con Microsoft (ADR 036), con un navegador de verdad.
 *
 * El backend prueba las reglas (`SsoIT`). Esto prueba lo que solo existe en un
 * navegador: que la cookie de la ida vuelve del proveedor, que las de la sesión
 * se quedan puestas al aterrizar en la web, que la web la retoma sola, y que
 * vincular reconoce la sesión abierta.
 *
 * **Necesita el proveedor de mentira y el backend apuntando a él**:
 *
 * ```
 * node e2e/proveedor-oidc.mjs
 * java -jar … --spring.profiles.active=dev,demo \
 *   --application.security.sso.url-publica=http://localhost:5173 \
 *   --application.security.sso.url-web=http://localhost:5173 \
 *   --application.security.sso.google.client-id=e2e --application.security.sso.google.client-secret=e2e \
 *   --application.security.sso.google.authorization-uri=http://localhost:9099/google/autorizar \
 *   --application.security.sso.google.token-uri=http://localhost:9099/token \
 *   --application.security.sso.google.jwk-set-uri=http://localhost:9099/jwks \
 *   (y lo mismo con «microsoft»)
 * ```
 *
 * La URL pública es la de la web porque Vite hace de proxy de `/auth`: para el
 * navegador, la API y la web son el mismo sitio, como en producción.
 *
 * En local, sin eso, las specs se saltan. En el CI no: allí tiene que estar.
 */

import { expect, test, type Page } from '@playwright/test';

const JAVIER = 'javier.lopez@techcorp.demo';

test.beforeEach(async ({ request }) => {
  const respuesta = await request.get('/auth/sso/proveedores');
  const proveedores = respuesta.ok() ? ((await respuesta.json()) as unknown[]) : [];
  if (process.env['CI'] !== undefined) {
    expect(proveedores, 'el backend del CI tiene que tener el SSO de mentira configurado').toHaveLength(2);
  }
  test.skip(proveedores.length === 0, 'El backend no tiene el SSO configurado: ver la cabecera de esta spec.');
});

/** En la página del proveedor de mentira, elige una cuenta. */
async function elegir(page: Page, cuenta: string | RegExp): Promise<void> {
  await expect(page.getByRole('heading', { name: /Elige una cuenta/ })).toBeVisible();
  await page.getByRole('link', { name: cuenta }).click();
}

async function cerrarSesion(page: Page): Promise<void> {
  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
}

async function irAAjustes(page: Page): Promise<void> {
  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('link', { name: 'Ajustes' }).click();
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible();
}

/** Deja a Javier sin esa cuenta vinculada, para que la spec se pueda repetir. */
async function desvincular(page: Page, proveedor: string): Promise<void> {
  await irAAjustes(page);
  await page.getByRole('button', { name: `Desvincular ${proveedor}` }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Desvincular' }).click();
  await expect(page.getByRole('link', { name: `Vincular ${proveedor}` })).toBeVisible();
}

test('entrar con Google: del botón a «Mi jornada», y la sesión aguanta una recarga', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar con Google' }).click();
  await elegir(page, /Javier/);

  // De vuelta en la web y dentro, sin haber tecleado nada.
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
  await expect(page).toHaveURL(/\/fichar$/);

  // La sesión es la de siempre: está en las cookies, y recargar la retoma.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  // Quedó vinculada sola, por el correo; se quita para dejarlo como estaba.
  await irAAjustes(page);
  await expect(page.getByText(new RegExp(JAVIER.replace('.', '\\.')))).toBeVisible();
  await desvincular(page, 'Google');
});

test('quien no tiene cuenta en NX Time vuelve al acceso sabiendo por qué, y sin sesión', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar con Google' }).click();
  await elegir(page, /sin cuenta/);

  await expect(page.getByRole('alert')).toContainText('No hay ninguna cuenta de NX Time');
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  // El motivo no se queda en la dirección.
  await expect(page).toHaveURL(/\/$/);

  await page.reload();
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('cancelar en el proveedor vuelve al acceso', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar con Google' }).click();
  await elegir(page, 'Cancelar');

  await expect(page.getByRole('alert')).toContainText('No has terminado de entrar');
  await expect(page.getByRole('link', { name: 'Entrar con Google' })).toBeVisible();
});

test('una cuenta de Microsoft sin correo garantizado no entra por el botón, pero se vincula desde Ajustes y entonces sí', async ({
  page,
}) => {
  // Por el botón, no: Microsoft no garantiza el correo de esa cuenta del trabajo.
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar con Microsoft' }).click();
  await elegir(page, /cuenta del trabajo/);
  await expect(page.getByRole('alert')).toContainText('No hemos podido comprobar');

  // Con la contraseña, y desde Ajustes, sí se vincula.
  await page.getByLabel('Correo electrónico').fill(JAVIER);
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
  await irAAjustes(page);
  await page.getByRole('link', { name: 'Vincular Microsoft' }).click();
  await elegir(page, /cuenta del trabajo/);

  // Vuelve a Ajustes, con la sesión que tenía, y lo dice.
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible();
  await expect(page.getByText('Cuenta vinculada: ya puedes entrar con ella.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Desvincular Microsoft' })).toBeVisible();
  await expect(page).toHaveURL(/\/ajustes$/);

  // Y a partir de ahí, entra con ella.
  await cerrarSesion(page);
  await page.getByRole('link', { name: 'Entrar con Microsoft' }).click();
  await elegir(page, /cuenta del trabajo/);
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await desvincular(page, 'Microsoft');
});
