/**
 * El push de la web (W9): qué estado ve cada navegador, qué se manda al
 * servidor al encender y apagar, y que el token sigue a la sesión. Firebase va
 * simulado: lo que se prueba es lo nuestro, no su SDK. El service worker se
 * prueba de verdad en `e2e/push.spec.ts`.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../api/cliente';
import { abrirSesion, cerrarSesion } from '../api/sesion';
import { sesionDe, simularApi, sinContenido } from '../pruebas/api';
import {
  apagarPush,
  encenderPush,
  ESPERA_MAXIMA_MS,
  estadoPush,
  FalloDePush,
  PermisoDenegado,
  seguirLaSesion,
} from './push';

const firebase = vi.hoisted(() => ({
  getToken: vi.fn(async () => 'token-web-1'),
  deleteToken: vi.fn(async () => true),
  isSupported: vi.fn(async () => true),
}));
vi.mock('firebase/app', () => ({ getApps: () => [], initializeApp: () => ({}) }));
vi.mock('firebase/messaging', () => ({ getMessaging: () => ({}), ...firebase }));

/** Un `Notification` de mentira cuyo permiso cambia al contestar, como el de verdad. */
function simularPermiso(inicial: NotificationPermission, respuesta: NotificationPermission = 'granted') {
  const falso = {
    permission: inicial,
    requestPermission: vi.fn(async () => {
      falso.permission = respuesta;
      return respuesta;
    }),
  };
  vi.stubGlobal('Notification', falso);
  return falso;
}

function configurarFirebase() {
  vi.stubEnv('VITE_FIREBASE_API_KEY', 'clave');
  vi.stubEnv('VITE_FIREBASE_PROJECT_ID', 'nx-time');
  vi.stubEnv('VITE_FIREBASE_SENDER_ID', '123');
  vi.stubEnv('VITE_FIREBASE_APP_ID', '1:123:web:abc');
  vi.stubEnv('VITE_FIREBASE_VAPID_KEY', 'vapid');
}

beforeEach(() => {
  localStorage.clear();
  reiniciarEstadoDeRed();
  configurarFirebase();
  vi.stubGlobal('PushManager', class {});
  Object.defineProperty(navigator, 'serviceWorker', {
    value: { ready: Promise.resolve({}), register: vi.fn(async () => ({})) },
    configurable: true,
  });
  simularPermiso('default');
});

afterEach(() => {
  cerrarSesion();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  vi.restoreAllMocks();
  Reflect.deleteProperty(navigator, 'serviceWorker');
});

describe('lo que cada navegador puede hacer', () => {
  it('sin las claves de Firebase en el build, no hay push y se dice', () => {
    vi.unstubAllEnvs();
    expect(estadoPush()).toBe('sin-configurar');
  });

  it('sin Push API, no está soportado', () => {
    vi.stubGlobal('PushManager', undefined);
    Reflect.deleteProperty(globalThis, 'PushManager');
    expect(estadoPush()).toBe('no-soportado');
  });

  it('en un iPhone desde Safari, sin instalar, pide añadirla a la pantalla de inicio', () => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile Safari/604.1',
    );
    expect(estadoPush()).toBe('instalar-primero');
  });

  it('con el permiso denegado, está bloqueado', () => {
    simularPermiso('denied');
    expect(estadoPush()).toBe('bloqueado');
  });

  it('por defecto, apagado', () => {
    expect(estadoPush()).toBe('apagado');
  });
});

describe('encender y apagar', () => {
  it('encender pide permiso, pide el token y lo registra como WEB', async () => {
    const api = simularApi({ 'POST /api/v1/dispositivos-push': () => sinContenido() });

    await encenderPush();

    expect(api.a('POST', '/api/v1/dispositivos-push')[0]?.cuerpo).toEqual({ token: 'token-web-1', plataforma: 'WEB' });
    expect(firebase.getToken).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ vapidKey: 'vapid' }));
    expect(estadoPush()).toBe('encendido');
  });

  it('si no se da el permiso, no se pide token ni se registra nada', async () => {
    simularPermiso('default', 'denied');
    const api = simularApi({});

    await expect(encenderPush()).rejects.toBeInstanceOf(PermisoDenegado);

    expect(firebase.getToken).not.toHaveBeenCalled();
    expect(api.llamadas).toEqual([]);
    expect(estadoPush()).toBe('bloqueado');
  });

  it('si el servidor no lo registra, siguen apagadas y se dice que fue el servidor', async () => {
    simularApi({ 'POST /api/v1/dispositivos-push': () => new Response(null, { status: 500 }) });

    await expect(encenderPush()).rejects.toMatchObject({ paso: 'servidor' });

    expect(estadoPush()).toBe('apagado');
  });

  it('va diciendo por qué paso va, en orden', async () => {
    simularApi({ 'POST /api/v1/dispositivos-push': () => sinContenido() });
    const pasos: string[] = [];

    await encenderPush((paso) => pasos.push(paso));

    expect(pasos).toEqual(['permiso', 'sdk', 'service-worker', 'token', 'servidor']);
  });

  it('con el permiso ya concedido, no se vuelve a preguntar', async () => {
    const permiso = simularPermiso('granted');
    simularApi({ 'POST /api/v1/dispositivos-push': () => sinContenido() });

    await encenderPush();

    expect(permiso.requestPermission).not.toHaveBeenCalled();
    expect(estadoPush()).toBe('encendido');
  });

  it('si se cierra la pregunta sin contestar, se distingue de denegarla', async () => {
    simularPermiso('default', 'default');
    simularApi({});

    const fallo = await encenderPush().catch((e: unknown) => e);

    expect(fallo).toBeInstanceOf(PermisoDenegado);
    expect((fallo as PermisoDenegado).respuesta).toBe('default');
    expect(estadoPush()).toBe('apagado');
  });
});

describe('cada paso que falla dice cuál fue', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('un service worker que no llega a activarse no deja el botón pensando para siempre', async () => {
    vi.useFakeTimers();
    Object.defineProperty(navigator, 'serviceWorker', {
      value: { ready: new Promise(() => undefined), register: vi.fn(async () => ({})) },
      configurable: true,
    });
    const api = simularApi({});

    const fallo = encenderPush().catch((e: unknown) => e);
    await vi.advanceTimersByTimeAsync(ESPERA_MAXIMA_MS);

    expect(await fallo).toMatchObject({ paso: 'service-worker', detalle: expect.stringContaining('Sin respuesta') });
    expect(firebase.getToken).not.toHaveBeenCalled();
    expect(api.llamadas).toEqual([]);
  });

  it('si no se puede registrar el service worker, lo dice con el error del navegador', async () => {
    Object.defineProperty(navigator, 'serviceWorker', {
      value: { ready: Promise.resolve({}), register: vi.fn(async () => Promise.reject(new Error('SecurityError'))) },
      configurable: true,
    });
    simularApi({});

    const fallo = await encenderPush().catch((e: unknown) => e);

    expect(fallo).toBeInstanceOf(FalloDePush);
    expect(fallo).toMatchObject({ paso: 'service-worker', detalle: 'SecurityError' });
  });

  it('si Google no da el token, el fallo es del token', async () => {
    firebase.getToken.mockRejectedValueOnce(new Error('Registration failed - push service error'));
    const api = simularApi({});

    const fallo = await encenderPush().catch((e: unknown) => e);

    expect(fallo).toMatchObject({ paso: 'token', detalle: 'Registration failed - push service error' });
    expect(api.llamadas).toEqual([]);
    expect(estadoPush()).toBe('apagado');
  });

  it('si Google no contesta, también acaba', async () => {
    vi.useFakeTimers();
    firebase.getToken.mockReturnValueOnce(new Promise<string>(() => undefined));
    simularApi({});

    const fallo = encenderPush().catch((e: unknown) => e);
    await vi.advanceTimersByTimeAsync(ESPERA_MAXIMA_MS);

    expect(await fallo).toMatchObject({ paso: 'token' });
  });

  it('si el navegador no admite el push de Firebase, el fallo es del SDK', async () => {
    firebase.isSupported.mockResolvedValueOnce(false);
    simularApi({});

    await expect(encenderPush()).rejects.toMatchObject({ paso: 'sdk' });
    expect(firebase.getToken).not.toHaveBeenCalled();
  });

  it('apagar da de baja ese token en el servidor y lo borra en Google', async () => {
    const api = simularApi({
      'POST /api/v1/dispositivos-push': () => sinContenido(),
      'POST /api/v1/dispositivos-push/baja': () => sinContenido(),
    });
    await encenderPush();

    await apagarPush();

    expect(api.a('POST', '/api/v1/dispositivos-push/baja')[0]?.cuerpo).toEqual({ token: 'token-web-1' });
    expect(firebase.deleteToken).toHaveBeenCalled();
    expect(estadoPush()).toBe('apagado');
  });

  it('apagar las apaga aunque el servidor no conteste', async () => {
    simularApi({
      'POST /api/v1/dispositivos-push': () => sinContenido(),
      'POST /api/v1/dispositivos-push/baja': () => new Response(null, { status: 503 }),
    });
    await encenderPush();

    await apagarPush();

    expect(firebase.deleteToken).toHaveBeenCalled();
    expect(estadoPush()).toBe('apagado');
  });
});

describe('el token sigue a la sesión', () => {
  it('al salir se borra en Google, y al volver a entrar se registra otra vez', async () => {
    const api = simularApi({ 'POST /api/v1/dispositivos-push': () => sinContenido() });
    await encenderPush();
    abrirSesion(sesionDe('EMPLEADO'));
    const dejar = seguirLaSesion();

    cerrarSesion();
    await vi.waitFor(() => expect(firebase.deleteToken).toHaveBeenCalled());
    // Se conserva el sí de este navegador: al volver, siguen encendidas.
    expect(estadoPush()).toBe('encendido');

    firebase.getToken.mockResolvedValueOnce('token-web-2');
    abrirSesion(sesionDe('EMPLEADO'));
    await vi.waitFor(() => expect(api.a('POST', '/api/v1/dispositivos-push')).toHaveLength(2));
    expect(api.a('POST', '/api/v1/dispositivos-push')[1]?.cuerpo).toEqual({ token: 'token-web-2', plataforma: 'WEB' });
    dejar();
  });

  it('con el push apagado, entrar y salir no tocan Firebase', async () => {
    const api = simularApi({});
    const dejar = seguirLaSesion();

    abrirSesion(sesionDe('EMPLEADO'));
    cerrarSesion();
    await new Promise((r) => setTimeout(r, 0));

    expect(firebase.getToken).not.toHaveBeenCalled();
    expect(firebase.deleteToken).not.toHaveBeenCalled();
    expect(api.llamadas).toEqual([]);
    dejar();
  });
});
