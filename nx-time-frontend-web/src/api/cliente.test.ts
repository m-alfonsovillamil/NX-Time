/**
 * El refresco del 401, que es la pieza con riesgo real de este cliente.
 *
 * **Por qué este test no es opcional.** El servidor rota el refresh token
 * (fase A11): el que se presenta deja de valer y reenviarlo se interpreta como
 * una copia robada, lo que revoca la familia entera. Al abrir la aplicación
 * salen varias peticiones a la vez y, pasados los 15 minutos del access token,
 * todas reciben 401 casi simultáneamente. Si cada una pidiera su propio
 * refresco, la segunda llegaría con un token ya usado y **el servidor cerraría
 * la sesión** — la protección nueva echando a la gente, que es exactamente lo
 * que le pasó a la app Android antes de que A12 pusiera el mutex.
 *
 * Comprobado que el test sirve: quitando la promesa compartida de
 * `tokenParaReintentar`, tres peticiones producen tres llamadas a
 * `/auth/refresh` en vez de una, y el segundo assert se pone rojo.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { cliente, reiniciarEstadoDeRed, tokenParaReintentar } from './cliente';
import { abrirSesion, cerrarSesion, haySesion, sesionActual } from './sesion';

const VIEJO = 'access-viejo';
const NUEVO = 'access-nuevo';

function json(cuerpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * Un servidor de mentira que exige el token bueno.
 *
 * Cuenta las llamadas a `/auth/refresh` por separado, que es lo único que
 * importa medir aquí.
 */
function servidor({ refrescoFalla = false } = {}) {
  const llamadas = { refrescos: 0, protegidas: 0, con401: 0 };

  const fetchFalso = vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
    const peticion = entrada instanceof Request ? entrada : new Request(entrada, init);
    const ruta = new URL(peticion.url, 'http://localhost').pathname;

    if (ruta === '/auth/refresh') {
      llamadas.refrescos += 1;
      if (refrescoFalla) return json({ detail: 'Refresh token no válido.' }, 401);
      // Rota: devuelve un refresh distinto del que se presentó.
      return json({ token: NUEVO, refreshToken: 'refresh-nuevo' });
    }

    llamadas.protegidas += 1;
    const autorizacion = peticion.headers.get('Authorization');
    if (autorizacion !== `Bearer ${NUEVO}`) {
      llamadas.con401 += 1;
      return json({ detail: 'Token caducado.' }, 401);
    }
    return json({ laborable: true });
  });

  vi.stubGlobal('fetch', fetchFalso);
  return llamadas;
}

beforeEach(() => {
  reiniciarEstadoDeRed();
  abrirSesion({
    accessToken: VIEJO,
    refreshToken: 'refresh-viejo',
    nombre: 'Ana',
    authorities: ['fichaje:escribir'],
  });
});

afterEach(() => {
  cerrarSesion();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('el refresco del 401', () => {
  it('tres peticiones que fallan a la vez producen UN solo refresco', async () => {
    const llamadas = servidor();

    const respuestas = await Promise.all([
      cliente.GET('/api/v1/fichaje/hoy', {}),
      cliente.GET('/api/v1/fichaje/hoy', {}),
      cliente.GET('/api/v1/fichaje/hoy', {}),
    ]);

    expect(llamadas.con401).toBe(3);
    expect(llamadas.refrescos)
      .toBe(1);
    for (const r of respuestas) {
      expect(r.response.status).toBe(200);
    }
  });

  it('guarda el refresh nuevo, no solo el access', () => {
    // Si solo se guardara el access, la siguiente renovación llegaría con un
    // token ya rotado y el servidor cerraría la sesión entera.
    servidor();

    return tokenParaReintentar(VIEJO).then(() => {
      expect(sesionActual()?.accessToken).toBe(NUEVO);
      expect(sesionActual()?.refreshToken).toBe('refresh-nuevo');
    });
  });

  it('si otro ya refrescó mientras esperaba, no pide nada', async () => {
    const llamadas = servidor();

    // Llega un 401 de una petición que llevaba un token ya sustituido.
    abrirSesion({
      accessToken: NUEVO,
      refreshToken: 'refresh-nuevo',
      nombre: 'Ana',
      authorities: [],
    });

    expect(await tokenParaReintentar(VIEJO)).toBe(NUEVO);
    expect(llamadas.refrescos).toBe(0);
  });

  it('si el refresco falla, la sesión se cierra', async () => {
    servidor({ refrescoFalla: true });

    expect(await tokenParaReintentar(VIEJO)).toBeNull();
    expect(haySesion()).toBe(false);
  });

  it('un 401 en /auth no se reintenta', async () => {
    // Un 401 ahí son credenciales malas o un refresh muerto; reintentar con
    // otro token no arregla ninguna de las dos cosas, solo escondería el error.
    const llamadas = servidor();

    const { response } = await cliente.POST('/auth/login', {
      body: { email: 'ana@nxtime.test', contrasena: 'mal' },
    });

    expect(response.status).toBe(401);
    expect(llamadas.refrescos).toBe(0);
  });

  it('sin sesión no hay nada que refrescar', async () => {
    servidor();
    cerrarSesion();

    expect(await tokenParaReintentar(VIEJO)).toBeNull();
  });
});
