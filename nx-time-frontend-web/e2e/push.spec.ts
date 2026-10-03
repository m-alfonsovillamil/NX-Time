/**
 * El service worker de verdad (`public/sw.js`, fase W9) y la web instalable.
 *
 * El push se entrega con el protocolo de depuración de Chromium
 * (`ServiceWorker.deliverPushMessage`), que dispara el mismo evento `push` que
 * un mensaje de FCM, con la forma que tiene uno de datos: `{ data: {...} }`.
 * Lo que no se puede probar aquí es el tramo de Google de verdad (el token y la
 * entrega): eso necesita la app web dada de alta en Firebase, y se comprueba en
 * producción (ver docs/DESPLIEGUE.md). Encender desde Ajustes sí se prueba, con
 * Google simulado en la red (`simularGoogle`).
 */

import { expect, test, type Page } from '@playwright/test';

import { entrar } from './ayudas';

// El Chromium completo y no el «headless shell» que Playwright usa por
// defecto sin ventana: en ese, el permiso de notificaciones sale siempre
// denegado dentro del service worker, aunque se conceda, y showNotification
// falla. Se vio al escribir este test.
test.use({ channel: 'chromium' });

/** Una sesión de depuración con el dominio ServiceWorker activo, y el id del nuestro. */
async function registroDelServiceWorker(page: Page) {
  const cdp = await page.context().newCDPSession(page);
  const registro = new Promise<string>((resolver) => {
    cdp.on('ServiceWorker.workerRegistrationUpdated', ({ registrations }) => {
      const nuestro = registrations.find((r) => r.scopeURL === 'http://localhost:5173/' && !r.isDeleted);
      if (nuestro !== undefined) resolver(nuestro.registrationId);
    });
  });
  await cdp.send('ServiceWorker.enable');
  await page.evaluate(() => navigator.serviceWorker.ready.then(() => undefined));
  return { cdp, registrationId: await registro };
}

/**
 * Entrega un push por el protocolo de depuración hasta que se pinta, como
 * mucho tres veces. En el runner de Linux del CI, a veces uno no llega a
 * pintarse (en Windows, siempre): pasaba con el segundo de dos seguidos y,
 * desde octubre de 2026, también con el primero de algunos tests. Lo que
 * prueban estos tests es lo que hace el service worker con el push, no la
 * entrega, así que reintentarla no tapa nada.
 */
async function entregarPush(page: Page, data: unknown) {
  const { cdp, registrationId } = await registroDelServiceWorker(page);
  for (let intento = 0; intento < 3; intento++) {
    await cdp.send('ServiceWorker.deliverPushMessage', {
      origin: 'http://localhost:5173',
      registrationId,
      data: JSON.stringify(data),
    });
    const limite = Date.now() + 3000;
    while (Date.now() < limite) {
      if ((await notificaciones(page)).length > 0) return;
      await page.waitForTimeout(100);
    }
  }
}

async function notificaciones(page: Page) {
  return page.evaluate(async () => {
    const registro = await navigator.serviceWorker.ready;
    return (await registro.getNotifications()).map((n) => ({
      titulo: n.title,
      cuerpo: n.body,
      tag: n.tag,
      url: (n.data as { url?: string } | null)?.url ?? null,
    }));
  });
}

test('un push de FCM se pinta con su texto y apunta a la página del aviso', async ({ page, context }) => {
  await context.grantPermissions(['notifications'], { origin: 'http://localhost:5173' });
  await page.goto('/');
  await entregarPush(page, {
    data: { tipo: 'AUSENCIA_RESUELTA', titulo: 'NX Time', cuerpo: 'Hay novedades en tus ausencias', ruta: 'ausencias' },
    from: '123',
    priority: 'high',
  });

  await expect.poll(() => notificaciones(page)).toEqual([
    { titulo: 'NX Time', cuerpo: 'Hay novedades en tus ausencias', tag: 'AUSENCIA_RESUELTA', url: 'http://localhost:5173/ausencias' },
  ]);
});

// Un push por test. Con dos seguidos al mismo registro, en el runner de Linux
// del CI el segundo no llegaba a pintarse (en Windows, 30 de 30 bien), aunque
// se mandara después de ver el primero. No se ha podido aclarar si es cosa de
// entregar push por el protocolo de depuración; varios avisos seguidos se
// comprueban en producción (docs/DESPLIEGUE.md).
for (const ruta of ['//otra-web.example/robo', 'https://otra-web.example/robo']) {
  test(`una ruta que apunta a otra web no saca de esta: ${ruta}`, async ({ page, context }) => {
    await context.grantPermissions(['notifications'], { origin: 'http://localhost:5173' });
    await page.goto('/');
    await entregarPush(page, { data: { tipo: 'X', titulo: 'NX Time', cuerpo: 'Algo', ruta } });

    // Se queda en una ruta de esta web (que no existe, y enseña su 404), nunca en otra.
    await expect
      .poll(async () => (await notificaciones(page)).map((n) => new URL(n.url ?? '').origin))
      .toEqual(['http://localhost:5173']);
  });
}

/**
 * El tramo de Google, simulado: las dos APIs de Firebase que usa `getToken`
 * (instalaciones y registro en FCM) y la suscripción del navegador, que en el
 * Chromium de Playwright no existe (no trae servicio de push). Lo demás es de
 * verdad: el permiso, el SDK de Firebase, el service worker y el registro del
 * token en el backend.
 */
async function simularGoogle(page: Page, registro: { status: number; token?: string }) {
  await page.addInitScript(() => {
    const suscripcion = {
      endpoint: 'https://fcm.googleapis.com/fcm/send/e2e',
      expirationTime: null,
      options: { userVisibleOnly: true, applicationServerKey: null },
      getKey: (nombre: string) => new Uint8Array(nombre === 'auth' ? 16 : 65).fill(7).buffer,
      toJSON: () => ({}),
      unsubscribe: async () => true,
    };
    let suscrita = false;
    PushManager.prototype.subscribe = async function () {
      suscrita = true;
      return suscripcion as unknown as PushSubscription;
    };
    PushManager.prototype.getSubscription = async function () {
      return suscrita ? (suscripcion as unknown as PushSubscription) : null;
    };
  });
  await page.route('https://firebaseinstallations.googleapis.com/**', (ruta) =>
    ruta.fulfill({
      json: {
        name: 'projects/1/installations/fid-e2e',
        fid: 'fid-e2e',
        refreshToken: 'refresco-e2e',
        authToken: { token: 'autorizacion-e2e', expiresIn: '604800s' },
      },
    }),
  );
  await page.route('https://fcmregistrations.googleapis.com/**', (ruta) =>
    ruta.request().method() === 'DELETE'
      ? ruta.fulfill({ json: {} })
      : registro.status === 200
        ? ruta.fulfill({ json: { token: registro.token } })
        : ruta.fulfill({
            status: registro.status,
            json: { error: { code: registro.status, message: 'API desactivada', status: 'PERMISSION_DENIED' } },
          }),
  );
}

async function irAAjustes(page: Page) {
  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('link', { name: 'Ajustes' }).click();
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible();
}

test('encender las notificaciones en Ajustes registra el token en el servidor, y apagarlas lo da de baja', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['notifications'], { origin: 'http://localhost:5173' });
  // Uno distinto en cada ejecución: el backend no admite el mismo token dos veces para dos personas.
  const token = `token-e2e-${Date.now()}`;
  await simularGoogle(page, { status: 200, token });
  await entrar(page, 'javier.lopez@techcorp.demo');
  await irAAjustes(page);

  const alta = page.waitForResponse((r) => r.url().endsWith('/api/v1/dispositivos-push') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Recibir notificaciones aquí' }).click();
  const respuesta = await alta;
  expect(respuesta.ok()).toBe(true);
  expect(respuesta.request().postDataJSON()).toEqual({ token, plataforma: 'WEB' });
  await expect(page.getByText('Encendidas', { exact: true })).toBeVisible();

  const baja = page.waitForResponse((r) => r.url().endsWith('/api/v1/dispositivos-push/baja'));
  await page.getByRole('button', { name: 'Dejar de recibirlas aquí' }).click();
  expect((await baja).ok()).toBe(true);
  await expect(page.getByRole('button', { name: 'Recibir notificaciones aquí' })).toBeVisible();
});

test('si Google no da el token, la página dice qué falló y deja volver a intentarlo', async ({ page, context }) => {
  await context.grantPermissions(['notifications'], { origin: 'http://localhost:5173' });
  await simularGoogle(page, { status: 403 });
  await entrar(page, 'javier.lopez@techcorp.demo');
  await irAAjustes(page);

  await page.getByRole('button', { name: 'Recibir notificaciones aquí' }).click();

  await expect(page.getByRole('alert')).toContainText('El servicio de avisos no ha respondido');
  await expect(page.getByRole('alert')).toContainText('Detalle:');
  await expect(page.getByRole('button', { name: 'Volver a intentarlo' })).toBeVisible();
});

test('la web es instalable: manifest con sus iconos', async ({ page, request }) => {
  await page.goto('/');
  const enlace = await page.locator('link[rel="manifest"]').getAttribute('href');
  expect(enlace).toBe('/manifest.webmanifest');

  const manifest = (await (await request.get('/manifest.webmanifest')).json()) as {
    name: string;
    display: string;
    start_url: string;
    icons: { src: string; sizes: string; purpose: string }[];
  };
  expect(manifest).toMatchObject({ name: 'NX Time', display: 'standalone', start_url: '/' });
  // Chrome pide al menos uno de 192 y otro de 512; el «maskable» es el que
  // Android recorta con su forma.
  expect(manifest.icons.map((i) => `${i.sizes} ${i.purpose}`)).toEqual(
    expect.arrayContaining(['192x192 any', '512x512 any', '512x512 maskable']),
  );
  for (const icono of manifest.icons) {
    const respuesta = await request.get(icono.src);
    expect(respuesta.status(), icono.src).toBe(200);
    expect(respuesta.headers()['content-type']).toContain('image/png');
  }
});
