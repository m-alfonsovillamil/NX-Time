/*
 * El service worker de NX Time (fase W9, ADR 031): pinta las notificaciones
 * push y, al pulsarlas, abre la página del aviso. Nada más.
 *
 * SIN el SDK de Firebase, a propósito. Los push del backend son solo de datos
 * (ADR 028): `{ tipo, titulo, cuerpo, ruta }`, con un texto genérico que dice
 * de qué va y no qué pasa. Para enseñar eso basta el evento `push` del
 * navegador; el SDK solo haría falta para mensajes con bloque `notification`,
 * y cargarlo aquí sería un `importScripts` de gstatic.com que la CSP tendría
 * que admitir. El SDK sí se usa en la página, y solo para pedir el token.
 *
 * SIN caché ni modo sin conexión, también a propósito: una web de fichar que
 * enseña datos viejos sin avisar es peor que una que dice «sin conexión».
 * Fichar sin conexión es otra decisión (ADR 022 la dejó candidata).
 *
 * Es JavaScript suelto y no pasa por Vite: un service worker se sirve desde la
 * raíz, con nombre fijo, y el navegador lo compara byte a byte para saber si ha
 * cambiado. Lo prueba `e2e/push.spec.ts`, entregándole un push de verdad por
 * el protocolo de depuración de Chromium.
 */

/* eslint-env serviceworker */

// Una versión nueva entra en cuanto se instala, sin esperar a que se cierren
// todas las pestañas: no guarda nada que pueda quedar a medias.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (evento) => evento.waitUntil(self.clients.claim()));

/**
 * Los datos del mensaje. FCM entrega un mensaje de datos como
 * `{ data: {...}, from, priority, fcmMessageId }`; se acepta también el objeto
 * suelto, que es lo que llega si alguien prueba a mano.
 */
function datosDelPush(evento) {
  if (!evento.data) return null;
  try {
    const cuerpo = evento.data.json();
    const datos = cuerpo && typeof cuerpo.data === 'object' ? cuerpo.data : cuerpo;
    return datos && typeof datos === 'object' ? datos : null;
  } catch {
    return null;
  }
}

/**
 * La URL de la página del aviso, SIEMPRE de este origen. La ruta es lógica
 * (`ausencias`, `correcciones/pendientes`) y coincide con la URL de la web
 * (ADR 029). Se le quitan las barras del principio antes de pegarla a la raíz,
 * así que `//otra-web.example` o `https://otra-web.example` se quedan en una
 * ruta de esta web (que no existe y enseña su 404), nunca en otra.
 */
function urlDe(ruta) {
  const limpia = typeof ruta === 'string' ? ruta.replace(/^\/+/, '') : '';
  const url = new URL('/' + limpia, self.location.origin);
  return url.origin === self.location.origin ? url.href : new URL('/', self.location.origin).href;
}

async function ventanas() {
  return self.clients.matchAll({ type: 'window', includeUncontrolled: true });
}

self.addEventListener('push', (evento) => {
  const datos = datosDelPush(evento);
  if (datos === null) return;
  evento.waitUntil(
    (async () => {
      // Siempre se enseña, también con la web abierta: Safari retira el
      // permiso a una web que recibe push sin enseñar nada, y en Chrome sale
      // un «Este sitio se ha actualizado en segundo plano» que confunde más.
      await self.registration.showNotification(datos.titulo || 'NX Time', {
        body: datos.cuerpo || '',
        // Una notificación por tipo: tres avisos de ausencias seguidos no son
        // tres notificaciones, es una que se renueva.
        tag: datos.tipo || 'nx-time',
        renotify: true,
        icon: '/iconos/icono-192.png',
        badge: '/iconos/icono-192.png',
        lang: 'es',
        data: { url: urlDe(datos.ruta) },
      });
      // Y a las pestañas abiertas, para que la campana se actualice sin
      // esperar a su consulta de cada cinco minutos.
      for (const ventana of await ventanas()) {
        ventana.postMessage({ nxTime: 'push', tipo: datos.tipo || null });
      }
    })(),
  );
});

self.addEventListener('notificationclick', (evento) => {
  evento.notification.close();
  const url = (evento.notification.data && evento.notification.data.url) || urlDe('');
  evento.waitUntil(
    (async () => {
      // Si ya hay una pestaña de la web, se usa esa: la página navega sola
      // (sin recargar) al recibir el mensaje. Si no, una nueva.
      for (const ventana of await ventanas()) {
        if (new URL(ventana.url).origin !== self.location.origin) continue;
        await ventana.focus();
        ventana.postMessage({ nxTime: 'abrir', url });
        return;
      }
      await self.clients.openWindow(url);
    })(),
  );
});
