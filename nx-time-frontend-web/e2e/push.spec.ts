/**
 * El service worker de verdad (`public/sw.js`, fase W9) y la web instalable.
 *
 * El push se entrega con el protocolo de depuración de Chromium
 * (`ServiceWorker.deliverPushMessage`), que dispara el mismo evento `push` que
 * un mensaje de FCM, con la forma que tiene uno de datos: `{ data: {...} }`.
 * Lo que no se puede probar aquí es el tramo de Google (el token y la entrega):
 * eso necesita la app web dada de alta en Firebase, y se comprueba en
 * producción (ver docs/DESPLIEGUE.md).
 */

import { expect, test, type Page } from '@playwright/test';

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
  const { cdp, registrationId } = await registroDelServiceWorker(page);

  await cdp.send('ServiceWorker.deliverPushMessage', {
    origin: 'http://localhost:5173',
    registrationId,
    data: JSON.stringify({
      data: { tipo: 'AUSENCIA_RESUELTA', titulo: 'NX Time', cuerpo: 'Hay novedades en tus ausencias', ruta: 'ausencias' },
      from: '123',
      priority: 'high',
    }),
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
    const { cdp, registrationId } = await registroDelServiceWorker(page);

    await cdp.send('ServiceWorker.deliverPushMessage', {
      origin: 'http://localhost:5173',
      registrationId,
      data: JSON.stringify({ data: { tipo: 'X', titulo: 'NX Time', cuerpo: 'Algo', ruta } }),
    });

    // Se queda en una ruta de esta web (que no existe, y enseña su 404), nunca en otra.
    await expect
      .poll(async () => (await notificaciones(page)).map((n) => new URL(n.url ?? '').origin))
      .toEqual(['http://localhost:5173']);
  });
}

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
