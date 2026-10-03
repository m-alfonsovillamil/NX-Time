/**
 * Las notificaciones push en la web (fase W9, ADR 031), con las mismas reglas
 * que en la app Android (ADR 028):
 *
 * - **Se encienden en este navegador, no en la cuenta.** Apagadas por defecto;
 *   se encienden en Ajustes, que es cuando se pide el permiso (uno pedido al
 *   abrir la web, sin contexto, se deniega). Encender es registrar el token en
 *   el servidor, y apagar, darlo de baja: no hay bandera en la cuenta.
 * - **Al salir, se olvida.** Se borra el token en Google; en el siguiente envío
 *   Google responde `UNREGISTERED` y el backend borra la fila. Al volver a
 *   entrar, si seguían encendidas, se pide otro y se registra (así, además, el
 *   token pasa a ser de quien acaba de entrar, si comparten navegador).
 *
 * El SDK de Firebase solo se usa aquí, para pedir el token, y se carga **al
 * encenderlas o al entrar con ellas encendidas**, en un trozo aparte: quien no
 * las usa no se lo descarga. El service worker (`public/sw.js`) no lo usa.
 *
 * En el iPhone, Safari solo entrega push a una web **añadida a la pantalla de
 * inicio**; desde el navegador ni siquiera existe `PushManager`. Por eso ese
 * caso tiene su propio estado, que explica cómo instalarla.
 */

import { cliente } from '../api/cliente';
import { pedir } from '../api/consultas';
import { haySesion, suscribirse } from '../api/sesion';

export type EstadoPush =
  /** Faltan las claves de Firebase en el build (`VITE_FIREBASE_*`). */
  | 'sin-configurar'
  /** iPhone o iPad desde Safari, sin instalar: hay que añadirla a la pantalla de inicio. */
  | 'instalar-primero'
  /** El navegador no tiene service workers o Push API. */
  | 'no-soportado'
  /** Se denegó el permiso: solo se puede devolver desde el navegador. */
  | 'bloqueado'
  | 'apagado'
  | 'encendido';

interface Configuracion {
  apiKey: string;
  projectId: string;
  messagingSenderId: string;
  appId: string;
  vapidKey: string;
}

/**
 * Las claves de la app web en Firebase. Ninguna es un secreto: van dentro del
 * JavaScript que se descarga cualquiera. Sin todas, no hay push y la web lo
 * dice en Ajustes, sin error.
 */
export function configuracion(): Configuracion | null {
  const env = import.meta.env;
  const valores = {
    apiKey: env['VITE_FIREBASE_API_KEY'],
    projectId: env['VITE_FIREBASE_PROJECT_ID'],
    messagingSenderId: env['VITE_FIREBASE_SENDER_ID'],
    appId: env['VITE_FIREBASE_APP_ID'],
    vapidKey: env['VITE_FIREBASE_VAPID_KEY'],
  };
  return Object.values(valores).every((v) => typeof v === 'string' && v !== '')
    ? (valores as Configuracion)
    : null;
}

/* ------------------------------------------------------------------ */
/* Lo que se recuerda en este navegador                                */
/* ------------------------------------------------------------------ */

/**
 * El token registrado, que es a la vez la marca de «encendidas». Hace falta el
 * valor, y no solo un sí o un no, para darlo de baja al apagarlas. Es una
 * preferencia de este navegador, como el tema: todo acceso va en try/catch.
 */
const CLAVE = 'nx-push-token';

function tokenGuardado(): string | null {
  try {
    return globalThis.localStorage?.getItem(CLAVE) ?? null;
  } catch {
    return null;
  }
}

function guardarToken(token: string | null): void {
  try {
    if (token === null) globalThis.localStorage?.removeItem(CLAVE);
    else globalThis.localStorage?.setItem(CLAVE, token);
  } catch {
    // Sin almacenamiento, siguen encendidas hasta cerrar la pestaña.
  }
}

/* ------------------------------------------------------------------ */
/* Qué se puede hacer en este navegador                                */
/* ------------------------------------------------------------------ */

function esIosSinInstalar(): boolean {
  const nav = globalThis.navigator;
  if (nav === undefined) return false;
  // Los iPad modernos dicen ser un Mac; se distinguen por la pantalla táctil.
  const ios = /iPhone|iPad|iPod/.test(nav.userAgent) || (/Macintosh/.test(nav.userAgent) && nav.maxTouchPoints > 1);
  const instalada =
    globalThis.matchMedia?.('(display-mode: standalone)').matches === true ||
    (nav as Navigator & { standalone?: boolean }).standalone === true;
  return ios && !instalada;
}

function hayPush(): boolean {
  return (
    'serviceWorker' in (globalThis.navigator ?? {}) && 'PushManager' in globalThis && 'Notification' in globalThis
  );
}

export function estadoPush(): EstadoPush {
  if (configuracion() === null) return 'sin-configurar';
  if (esIosSinInstalar()) return 'instalar-primero';
  if (!hayPush()) return 'no-soportado';
  if (Notification.permission === 'denied') return 'bloqueado';
  return tokenGuardado() !== null && Notification.permission === 'granted' ? 'encendido' : 'apagado';
}

/* ------------------------------------------------------------------ */
/* Los pasos de encender, y qué falló                                   */
/* ------------------------------------------------------------------ */

/** Los pasos de encender, en orden. La página va diciendo por cuál va. */
export type PasoPush = 'permiso' | 'sdk' | 'service-worker' | 'token' | 'servidor';

/**
 * Un fallo al encender, con el paso en que ocurrió. Antes todo acababa en un
 * «No se han podido activar» genérico, y un paso que no contestaba no acababa
 * en nada: el botón se quedaba pensando para siempre y parecía que no hacía
 * nada (1/10/2026).
 */
export class FalloDePush extends Error {
  readonly paso: PasoPush;
  /** Lo que dijo el navegador o Firebase, para enseñarlo como detalle. */
  readonly detalle: string;

  constructor(paso: PasoPush, causa: unknown) {
    const detalle = causa instanceof Error ? causa.message : String(causa);
    super(`${paso}: ${detalle}`);
    this.name = 'FalloDePush';
    this.paso = paso;
    this.detalle = detalle;
  }
}

/** No se dio el permiso: se denegó (`denied`) o se cerró la pregunta (`default`). */
export class PermisoDenegado extends FalloDePush {
  readonly respuesta: NotificationPermission;

  constructor(respuesta: NotificationPermission) {
    super('permiso', respuesta);
    this.name = 'PermisoDenegado';
    this.respuesta = respuesta;
  }
}

/**
 * Lo que se espera a cada paso que depende del navegador o de Google. Ninguno
 * tarda más de un par de segundos cuando funciona; si uno no contesta, mejor
 * decirlo que dejar el botón pensando. El permiso no tiene límite (lo contesta
 * una persona) y el registro en el servidor tampoco: el cliente ya espera al
 * arranque en frío de Render.
 */
export const ESPERA_MAXIMA_MS = 15_000;

function conTiempo<T>(promesa: Promise<T>, ms: number): Promise<T> {
  let reloj: ReturnType<typeof setTimeout> | undefined;
  const plazo = new Promise<never>((_, rechazar) => {
    reloj = setTimeout(() => rechazar(new Error(`Sin respuesta en ${ms / 1000} s.`)), ms);
  });
  return Promise.race([promesa, plazo]).finally(() => clearTimeout(reloj));
}

/** Hace un paso y, si falla o no contesta a tiempo, lo dice con su nombre. */
async function enPaso<T>(paso: PasoPush, trabajo: () => Promise<T>, ms: number | null = ESPERA_MAXIMA_MS): Promise<T> {
  try {
    return await (ms === null ? trabajo() : conTiempo(trabajo(), ms));
  } catch (e) {
    throw e instanceof FalloDePush ? e : new FalloDePush(paso, e);
  }
}

/* ------------------------------------------------------------------ */
/* El service worker y el token                                         */
/* ------------------------------------------------------------------ */

/**
 * Registra `public/sw.js`. Se hace al arrancar y no solo al encender el push:
 * es también lo que pinta la notificación y abre su página al pulsarla, y
 * Chrome lo usa para ofrecer instalar la web. Si falla, la web sigue igual,
 * pero queda en la consola: encender lo vuelve a intentar y dirá qué pasó.
 */
export async function registrarServiceWorker(): Promise<void> {
  if (!('serviceWorker' in (globalThis.navigator ?? {}))) return;
  try {
    await navigator.serviceWorker.register('/sw.js');
  } catch (e) {
    console.warn('[NX Time] No se ha podido registrar el service worker de las notificaciones.', e);
  }
}

/**
 * El service worker, registrado y activo. Se registra otra vez aunque ya lo
 * esté (devuelve el mismo registro) porque `serviceWorker.ready` a secas no
 * acaba nunca si el registro del arranque falló.
 */
async function serviceWorkerActivo(): Promise<ServiceWorkerRegistration> {
  return enPaso('service-worker', async () => {
    await navigator.serviceWorker.register('/sw.js');
    return navigator.serviceWorker.ready;
  });
}

async function firebase() {
  const config = configuracion();
  if (config === null) throw new FalloDePush('sdk', 'Sin configuración de Firebase.');
  const [{ getApps, initializeApp }, mensajeria] = await Promise.all([
    import('firebase/app'),
    import('firebase/messaging'),
  ]);
  if (!(await mensajeria.isSupported())) throw new FalloDePush('sdk', 'Este navegador no admite notificaciones push.');
  const { vapidKey, ...opciones } = config;
  const app = getApps()[0] ?? initializeApp(opciones);
  return { messaging: mensajeria.getMessaging(app), mensajeria, vapidKey };
}

async function pedirToken(alAvanzar: (paso: PasoPush) => void = () => undefined): Promise<string> {
  alAvanzar('sdk');
  const { messaging, mensajeria, vapidKey } = await enPaso('sdk', firebase);
  alAvanzar('service-worker');
  const registro = await serviceWorkerActivo();
  alAvanzar('token');
  return enPaso('token', () => mensajeria.getToken(messaging, { vapidKey, serviceWorkerRegistration: registro }));
}

async function registrarEnServidor(token: string): Promise<void> {
  await pedir(cliente.POST('/api/v1/dispositivos-push', { body: { token, plataforma: 'WEB' } }));
}

/** Borra el token en Google. Lo que falle da igual: ya no se va a usar. */
async function borrarEnGoogle(): Promise<void> {
  try {
    const { messaging, mensajeria } = await firebase();
    await mensajeria.deleteToken(messaging);
  } catch {
    // Si Google no contesta, el servidor lo borrará cuando le diga UNREGISTERED.
  }
}

/* ------------------------------------------------------------------ */
/* Encender y apagar (Ajustes)                                          */
/* ------------------------------------------------------------------ */

/**
 * Pide permiso, pide el token y lo registra. Tiene que llamarse desde un clic:
 * Safari no deja pedir el permiso de otro modo. `alAvanzar` dice por qué paso
 * va, y si algo falla se lanza un {@link FalloDePush} con el paso.
 */
export async function encenderPush(alAvanzar: (paso: PasoPush) => void = () => undefined): Promise<void> {
  alAvanzar('permiso');
  // Si ya estaba concedido (se encendieron y luego se perdió el token), no se
  // vuelve a preguntar: requestPermission contestaría lo mismo, pero no hace falta.
  const permiso =
    Notification.permission === 'granted'
      ? 'granted'
      : await enPaso('permiso', () => Notification.requestPermission(), null);
  if (permiso !== 'granted') throw new PermisoDenegado(permiso);
  const token = await pedirToken(alAvanzar);
  alAvanzar('servidor');
  await enPaso('servidor', () => registrarEnServidor(token), null);
  guardarToken(token);
}

/** Da de baja el token en el servidor y en Google. Apagadas aunque algo falle. */
export async function apagarPush(): Promise<void> {
  const token = tokenGuardado();
  guardarToken(null);
  if (token !== null) {
    try {
      await pedir(cliente.POST('/api/v1/dispositivos-push/baja', { body: { token } }));
    } catch {
      // Borrado en Google, el servidor lo quitará en el siguiente envío.
    }
  }
  await borrarEnGoogle();
}

/* ------------------------------------------------------------------ */
/* Entrar y salir                                                       */
/* ------------------------------------------------------------------ */

/**
 * Sigue a la sesión: al entrar con el push encendido, pide el token y lo
 * registra (el token puede haber cambiado, o ser de quien estuvo antes en este
 * navegador); al salir, por el camino que sea, lo borra en Google. El sí o no
 * de este navegador se conserva: al volver a entrar, siguen encendidas.
 */
export function seguirLaSesion(): () => void {
  let habia = haySesion();
  return suscribirse(() => {
    const hay = haySesion();
    if (hay && !habia && estadoPush() === 'encendido') {
      void pedirToken()
        .then(async (token) => {
          await registrarEnServidor(token);
          guardarToken(token);
        })
        .catch(() => undefined);
    }
    if (!hay && habia && tokenGuardado() !== null) void borrarEnGoogle();
    habia = hay;
  });
}
