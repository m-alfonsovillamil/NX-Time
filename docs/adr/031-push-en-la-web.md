# 31. Push en la web: service worker propio, SDK solo para el token y web instalable

**Estado:** aceptada · **Fecha:** septiembre 2026 · Completa el
[ADR 028](028-push-generico-colgado-del-aviso.md) en lo que dejó «preparado, no
hecho»

## Contexto

El ADR 028 trajo el push a la app Android y dejó la web preparada: la tabla
`dispositivos_push` admite `plataforma = 'WEB'` y las dos rutas de registro no
distinguen de dónde viene el token. Faltaba todo lo del navegador.

Importa más de lo que parece porque **la web es hoy el cliente de quien tenga
iPhone** (no hay app de iOS), y en el iPhone Safari solo entrega push a una web
**añadida a la pantalla de inicio**. Sin web instalable, en iOS no hay push.

## Decisión

### El service worker es nuestro, y no carga el SDK de Firebase

La forma documentada es un `firebase-messaging-sw.js` que hace `importScripts`
del SDK desde `gstatic.com`. No se sigue. Los push del backend son **solo de
datos** (ADR 028): `{ tipo, titulo, cuerpo, ruta }`. Para pintarlos basta el
evento `push` del navegador y `showNotification`, y el SDK solo aportaría el
tratamiento de mensajes con bloque `notification`, que aquí no existen.

A cambio: la CSP no tiene que admitir scripts de Google, el service worker es un
fichero de un centenar de líneas, comentarios incluidos, que se lee entero (`public/sw.js`) y no hay una versión del SDK
en el worker que mantener a la par de la de la página.

El SDK sí se usa **en la página**, y solo para lo que no se puede hacer sin él:
pedir el token de FCM (`getToken` con la clave VAPID y **nuestro** registro del
service worker) y borrarlo (`deleteToken`). Va en un trozo aparte que se carga
al encender el push o al entrar con él encendido: quien no lo usa no se lo
descarga, y el JS inicial solo sube 0,8 kB.

### Mismas reglas que en la app

- **Apagado por defecto, se enciende en Ajustes**, que es cuando se pide el
  permiso. Un permiso pedido al abrir la web se deniega, y en Safari solo se
  puede pedir desde un clic.
- **Es de este navegador, no de la cuenta.** Encender es registrar el token;
  apagar, darlo de baja. Se recuerda en `localStorage` (el token, que hace falta
  para darlo de baja), igual que el tema.
- **Al salir, se olvida**, por cualquier camino: `cerrarSesion()` es el único
  punto por el que pasan salir, la sesión caducada y el aviso de otra pestaña, y
  el módulo de push lo escucha para borrar el token en Google. El sí de este
  navegador se conserva: al volver a entrar se pide un token nuevo y se
  registra, y así el token pasa a ser de quien acaba de entrar si comparten
  navegador (el servidor ya cambia de dueño un token repetido).
- **El texto es genérico**: el service worker enseña lo que manda el servidor
  («Hay novedades en tus ausencias»), nunca el detalle.

### Siempre se pinta, también con la web abierta

Una notificación por tipo (`tag`), que se renueva en vez de amontonarse. Se
pinta aunque haya una pestaña abierta: Safari **retira el permiso** a una web que
recibe push sin enseñar nada, y Chrome, en ese caso, enseña un «este sitio se ha
actualizado en segundo plano» que confunde más. Además, el worker avisa a las
pestañas abiertas y la campana se actualiza sin esperar a su consulta de cada
minuto.

Al pulsarla se abre la página del aviso: la `ruta` lógica es la URL de la web
(ADR 029, decisión 4). Si ya hay una pestaña, esa navega sin recargar; si no, se
abre una. La ruta se pega siempre a la raíz de este origen: un push no puede
mandar a nadie a otra web.

### Web instalable, sin modo sin conexión

`manifest.webmanifest` con los iconos de la marca de la app Android (la «N»
como línea de tiempo, pasada a SVG; `npm run iconos` genera los PNG). El
service worker **no guarda nada en caché**: una web de fichar que enseña datos
viejos sin avisar es peor que una que dice «sin conexión». Fichar sin conexión
es otra decisión, que el ADR 022 dejó como candidata.

### En el backend, el mensaje dice cuánto vive también para la web

El mensaje ya llevaba, para Android, prioridad alta y un día de vida. La web va
por Web Push y no lee ese bloque: sin nada, un push web vive **cuatro semanas**
en los servidores de Google. Ahora lleva también `TTL: 86400` y `Urgency: high`
(`WebpushConfig`).

### El recordatorio de fichar por push: valorado, y no se hace

El plan dejó pendiente valorarlo aquí: con push en la web, el recordatorio que
la app tiene en local podría volver como un push programado desde el servidor.
No se hace, por tres razones:

1. **Pediría el backend despierto a cualquier hora.** Cada persona entra a una
   hora distinta, así que habría envíos repartidos por todo el día. En el plan
   gratuito de Render el servicio se duerme, y una tarea programada con el
   servicio dormido no corre (lo vigila `tareas-nocturnas.yml` precisamente por
   eso).
2. **Y con él, la base despierta.** El 28/09/2026 Neon agotó su cuota del mes
   porque la base no llegaba a dormirse (el health check la tocaba). Un
   recordatorio por persona y día haría lo mismo por diseño.
3. **La app ya lo tiene, en local**, que es donde tiene sentido: el móvil sabe la
   hora sin preguntar a nadie.

Se retomará si el backend pasa a un plan de pago, o si hay app de iOS (que lo
haría en local, como Android).

## Consecuencias

- Hacen falta cinco valores de la consola de Firebase en el build de la web
  (`VITE_FIREBASE_*`: la app web del proyecto y la clave VAPID). **No son
  secretos**: van en el JavaScript que se descarga cualquiera. Sin ellos la web
  funciona igual y Ajustes dice que esta instalación no tiene push, sin error.
- La CSP de la web admite dos orígenes de Google más en `connect-src`
  (`firebaseinstallations.googleapis.com` y `fcmregistrations.googleapis.com`),
  que son los que usa el SDK para pedir y borrar el token.
- `e2e/push.spec.ts` prueba el service worker **de verdad**: le entrega un push
  con la forma de FCM por el protocolo de depuración de Chromium y mira la
  notificación que pinta. Necesita el Chromium completo: en el «headless shell»
  de Playwright el permiso sale siempre denegado dentro del worker. El tramo de
  Google (token y entrega) no se puede probar sin la app web dada de alta, y se
  comprueba en producción.
- En el iPhone hay que instalar la web antes de poder encender nada, y Ajustes
  lo explica en ese caso en vez de enseñar un botón que no funcionaría.
- El JS inicial pasa de 106,2 a 107,0 kB (de 150).
