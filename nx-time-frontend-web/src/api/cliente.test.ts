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
 *
 * Desde la fase W1 (ADR 030) el refresh no está en la página: es una cookie
 * `HttpOnly` que el navegador manda solo. Lo que se comprueba aquí es lo que
 * sí hace la página: pedir con `credentials: 'include'`, sin cuerpo, y con la
 * cabecera CSRF copiada de la cookie `nx_csrf`.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { cliente, reiniciarEstadoDeRed, restaurarSesion, salir, tokenParaReintentar } from './cliente';
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
const CSRF = 'valor-csrf';

/** Lo que llegó al servidor en cada refresco y cada logout. */
interface PeticionDeSesion {
  ruta: string;
  csrf: string | null;
  credenciales: RequestCredentials;
  cuerpo: string;
}

function servidor({ refrescoFalla = false } = {}) {
  const llamadas = { refrescos: 0, protegidas: 0, con401: 0, deSesion: [] as PeticionDeSesion[] };

  const fetchFalso = vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
    const peticion = entrada instanceof Request ? entrada : new Request(entrada, init);
    const ruta = new URL(peticion.url, 'http://localhost').pathname;

    if (ruta === '/auth/refresh' || ruta === '/auth/logout') {
      llamadas.deSesion.push({
        ruta,
        csrf: peticion.headers.get('X-CSRF-Token'),
        credenciales: peticion.credentials,
        cuerpo: await peticion.text(),
      });
    }
    if (ruta === '/auth/logout') return new Response(null, { status: 200 });
    if (ruta === '/auth/refresh') {
      llamadas.refrescos += 1;
      if (refrescoFalla) return json({ detail: 'Refresh token no válido.' }, 401);
      // Sin refresh en el cuerpo: va en la cookie, que aquí no se ve.
      return json({ token: NUEVO, nombre: 'Ana', authorities: ['fichaje:escribir', 'fichaje:leer'] });
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

/** La cookie CSRF que pone el servidor al entrar; `null` la quita. */
function cookieCsrf(valor: string | null) {
  document.cookie = valor === null ? 'nx_csrf=; max-age=0' : `nx_csrf=${valor}`;
}

beforeEach(() => {
  reiniciarEstadoDeRed();
  cookieCsrf(CSRF);
  abrirSesion({ accessToken: VIEJO, nombre: 'Ana', authorities: ['fichaje:escribir'] });
});

afterEach(() => {
  cerrarSesion();
  cookieCsrf(null);
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

  /*
   * El refresh viaja solo, en la cookie. Lo que tiene que poner la página es
   * `credentials: 'include'` (sin él, el navegador no manda la cookie a otro
   * subdominio) y la cabecera CSRF; y NO un cuerpo con un refresh que ya no
   * tiene.
   */
  it('renueva con la cookie: credenciales, cabecera CSRF y sin cuerpo', async () => {
    const llamadas = servidor();

    await tokenParaReintentar(VIEJO);

    expect(llamadas.deSesion).toEqual([{ ruta: '/auth/refresh', csrf: CSRF, credenciales: 'include', cuerpo: '' }]);
    expect(sesionActual()?.accessToken).toBe(NUEVO);
  });

  it('el refresco trae las authorities: un cambio de rol llega sin volver a entrar', async () => {
    servidor();

    await tokenParaReintentar(VIEJO);

    expect(sesionActual()?.authorities).toEqual(['fichaje:escribir', 'fichaje:leer']);
  });

  it('sin la cookie CSRF no hay sesión que renovar, y ni se pregunta', async () => {
    const llamadas = servidor();
    cookieCsrf(null);

    expect(await tokenParaReintentar(VIEJO)).toBeNull();
    expect(llamadas.refrescos).toBe(0);
    expect(haySesion()).toBe(false);
  });

  it('si otro ya refrescó mientras esperaba, no pide nada', async () => {
    const llamadas = servidor();

    // Llega un 401 de una petición que llevaba un token ya sustituido.
    abrirSesion({ accessToken: NUEVO, nombre: 'Ana', authorities: [] });

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

/*
 * Recargar la página ya no cierra la sesión (ADR 030): al arrancar, la web
 * intenta retomar la de la cookie.
 */
describe('al abrir la web', () => {
  beforeEach(() => {
    cerrarSesion();
  });

  it('con la cookie, retoma la sesión sin pasar por el login', async () => {
    const llamadas = servidor();

    expect(await restaurarSesion()).toBe(true);
    expect(sesionActual()).toEqual({
      accessToken: NUEVO,
      nombre: 'Ana',
      authorities: ['fichaje:escribir', 'fichaje:leer'],
    });
    expect(llamadas.refrescos).toBe(1);
  });

  it('sin la cookie, no pregunta al servidor: no hay sesión y no se le despierta', async () => {
    const llamadas = servidor();
    cookieCsrf(null);

    expect(await restaurarSesion()).toBe(false);
    expect(llamadas.refrescos).toBe(0);
  });

  it('si la sesión de la cookie ya no vale, se queda fuera sin error', async () => {
    servidor({ refrescoFalla: true });

    expect(await restaurarSesion()).toBe(false);
    expect(haySesion()).toBe(false);
  });

  /* React en modo estricto monta dos veces: no pueden salir dos refrescos, que el servidor rota. */
  it('pedirla dos veces a la vez es un solo refresco', async () => {
    const llamadas = servidor();

    await Promise.all([restaurarSesion(), restaurarSesion()]);

    expect(llamadas.refrescos).toBe(1);
  });
});

describe('cerrar la sesión', () => {
  it('avisa al servidor con la cookie y el CSRF, sin cuerpo, y cierra la local', async () => {
    const llamadas = servidor();

    salir();

    expect(haySesion()).toBe(false);
    await vi.waitFor(() =>
      expect(llamadas.deSesion).toEqual([{ ruta: '/auth/logout', csrf: CSRF, credenciales: 'include', cuerpo: '' }]),
    );
  });
});
