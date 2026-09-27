/**
 * El cliente HTTP: tipado por el contrato, con el refresco del 401 serializado.
 *
 * Los tipos salen de `schema.d.ts`, que se genera de `docs/openapi.json`, así
 * que pedir una ruta que no existe o mandar un campo mal escrito **no
 * compila**. Eso ya ha evitado un fallo conocido: el login espera `contrasena`
 * y no `password`, y con `password` el servidor responde 400.
 *
 * ## Lo que de verdad hay aquí
 *
 * Dos cosas que no son opcionales en este backend:
 *
 * 1. **Un solo refresco en vuelo.** Al abrir la aplicación salen varias
 *    peticiones a la vez; pasados los 15 minutos del access token, todas
 *    reciben 401 casi simultáneamente. Como el servidor **rota** el refresh
 *    (fase A11), dos refrescos en paralelo significan que el segundo llega con
 *    un token ya usado: el servidor lo interpreta como una copia robada y
 *    revoca la familia entera. Es decir, sin esto la protección nueva echaría
 *    a la gente de la aplicación. Es el mismo problema que `RefrescoDeToken`
 *    resolvió en Android con un mutex, y la misma solución: una única promesa
 *    compartida.
 *
 * 2. **El arranque en frío de Render.** El plan gratuito duerme el servicio a
 *    los 15 minutos, y despertarlo ha llegado a tardar tres minutos medidos.
 *    Con el timeout por defecto del navegador la primera petición de la mañana
 *    no va lenta: no entra. Aquí se espera hasta 300 s cuando el servidor
 *    puede estar dormido, y se avisa por pantalla a los 3 s — sin el aviso se
 *    ve una página congelada y se cierra antes de que responda.
 */

import createClient, { type Middleware } from 'openapi-fetch';

import type { paths } from './schema';
import { cerrarSesion, renovarTokens, sesionActual } from './sesion';

/**
 * A dónde se habla.
 *
 * Sin `VITE_API_URL` se usa el **origen de la página**, que en desarrollo es el
 * servidor de Vite y su proxy manda `/api` y `/auth` al backend local. Se
 * resuelve a una URL absoluta y no se deja vacío a propósito: una ruta relativa
 * solo la sabe resolver un navegador contra su documento, y `new Request()`
 * fuera de uno —en los tests, o el día que algo se renderice en servidor— la
 * rechaza con `Invalid URL`. Con el origen delante, el comportamiento es el
 * mismo en los dos sitios.
 */
const BASE = import.meta.env['VITE_API_URL'] || globalThis.location?.origin || '';

/* ------------------------------------------------------------------ */
/* Arranque en frío                                                    */
/* ------------------------------------------------------------------ */

/** Render duerme a los 15 min; se deja margen porque el apagado no es al segundo. */
const UMBRAL_DORMIDO_MS = 10 * 60 * 1000;
/** Medido: un login en frío tardó 160 s un día y más de 180 s otro. */
const ESPERA_LARGA_MS = 300_000;
const ESPERA_CORTA_MS = 30_000;
/** A partir de aquí la interfaz dice que el servidor está despertando. */
const RETARDO_AVISO_MS = 3_000;

let ultimaRespuesta: number | null = null;
let esperasLentas = 0;
const oyentesDespertando = new Set<() => void>();

function puedeEstarDormido(): boolean {
  return ultimaRespuesta === null || Date.now() - ultimaRespuesta >= UMBRAL_DORMIDO_MS;
}

/**
 * Un 5xx no demuestra que el servidor esté despierto: mientras Render levanta
 * la instancia quien responde es su proxy (502/503), no la aplicación.
 */
function anotarRespuesta(status: number): void {
  if (status < 500) ultimaRespuesta = Date.now();
}

function cambiarEsperasLentas(delta: number): void {
  esperasLentas += delta;
  for (const oyente of oyentesDespertando) oyente();
}

export function servidorDespertando(): boolean {
  return esperasLentas > 0;
}

export function suscribirseADespertando(oyente: () => void): () => void {
  oyentesDespertando.add(oyente);
  return () => {
    oyentesDespertando.delete(oyente);
  };
}

/* ------------------------------------------------------------------ */
/* Refresco serializado                                                */
/* ------------------------------------------------------------------ */

/**
 * La renovación en curso, si la hay.
 *
 * Que sea **una sola promesa compartida** es justo el punto: quien llega
 * después no pide otro refresco, espera a este. Se resuelve con el access
 * token nuevo, o con `null` si la sesión ya no se puede salvar.
 */
let refrescoEnVuelo: Promise<string | null> | null = null;

async function pedirTokensNuevos(refreshToken: string): Promise<string | null> {
  try {
    const respuesta = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
      signal: AbortSignal.timeout(puedeEstarDormido() ? ESPERA_LARGA_MS : ESPERA_CORTA_MS),
    });
    anotarRespuesta(respuesta.status);
    if (!respuesta.ok) return null;

    const cuerpo = (await respuesta.json()) as { token: string; refreshToken: string };
    // Los dos, y de una vez: el refresh que se acaba de usar ya no vale.
    renovarTokens(cuerpo.token, cuerpo.refreshToken);
    return cuerpo.token;
  } catch {
    // Sin red, o el servidor no llegó a responder. No se distingue de un
    // refresh caducado desde aquí, y en los dos casos hay que volver a entrar.
    return null;
  }
}

/**
 * Devuelve el access token con el que reintentar, o `null` si hay que rendirse.
 *
 * @param tokenQueFallo el que llevaba la petición rechazada. Sirve para
 *   distinguir "mi token ha caducado" de "otro ya lo renovó mientras yo
 *   esperaba": sin esa comprobación, compartir la promesa solo serializaría
 *   las llamadas en vez de evitarlas.
 */
export async function tokenParaReintentar(tokenQueFallo: string | undefined): Promise<string | null> {
  const actual = sesionActual();
  if (!actual) return null;
  if (tokenQueFallo !== undefined && actual.accessToken !== tokenQueFallo) {
    return actual.accessToken;
  }

  refrescoEnVuelo ??= pedirTokensNuevos(actual.refreshToken).finally(() => {
    refrescoEnVuelo = null;
  });

  const nuevo = await refrescoEnVuelo;
  if (nuevo === null) cerrarSesion();
  return nuevo;
}

/* ------------------------------------------------------------------ */
/* El cliente                                                          */
/* ------------------------------------------------------------------ */

const autenticacion: Middleware = {
  async onRequest({ request }) {
    const sesion = sesionActual();
    if (sesion && !request.headers.has('Authorization')) {
      request.headers.set('Authorization', `Bearer ${sesion.accessToken}`);
    }
    return request;
  },

  async onResponse({ request, response }) {
    anotarRespuesta(response.status);
    if (response.status !== 401) return response;

    // `/auth/*` no se reintenta: un 401 ahí son credenciales malas o un
    // refresh muerto, y volver a intentarlo con otro token no arregla ninguna
    // de las dos cosas -- solo escondería el error.
    if (new URL(request.url).pathname.startsWith('/auth/')) return response;

    const cabecera = request.headers.get('Authorization') ?? undefined;
    const nuevo = await tokenParaReintentar(cabecera?.replace(/^Bearer /, ''));
    if (nuevo === null) return response;

    const reintento = new Request(request, {
      headers: new Headers(request.headers),
    });
    reintento.headers.set('Authorization', `Bearer ${nuevo}`);
    const segunda = await fetch(reintento);
    anotarRespuesta(segunda.status);
    return segunda;
  },
};

/**
 * Espera larga solo cuando el servidor puede estar dormido.
 *
 * En una pantalla normal, tres minutos mirando una rueda son peores que un
 * error; lo que decide no es el endpoint sino **cuánto hace de la última
 * respuesta**, igual que en `ArranqueEnFrio.kt`.
 */
const arranqueEnFrio: Middleware = {
  async onRequest({ request }) {
    if (!puedeEstarDormido()) {
      return new Request(request, { signal: AbortSignal.timeout(ESPERA_CORTA_MS) });
    }

    let avisado = false;
    const aviso = setTimeout(() => {
      avisado = true;
      cambiarEsperasLentas(+1);
    }, RETARDO_AVISO_MS);

    const senal = AbortSignal.timeout(ESPERA_LARGA_MS);
    const limpiar = () => {
      clearTimeout(aviso);
      if (avisado) {
        avisado = false;
        cambiarEsperasLentas(-1);
      }
    };
    // El middleware no tiene un `finally` que abarque la respuesta, así que la
    // limpieza cuelga de la propia señal y de un microtask por si la petición
    // termina antes.
    senal.addEventListener('abort', limpiar, { once: true });
    pendientesDeLimpiar.set(request.url, limpiar);

    return new Request(request, { signal: senal });
  },

  async onResponse({ request, response }) {
    pendientesDeLimpiar.get(request.url)?.();
    pendientesDeLimpiar.delete(request.url);
    return response;
  },

  async onError({ request }) {
    pendientesDeLimpiar.get(request.url)?.();
    pendientesDeLimpiar.delete(request.url);
    return undefined;
  },
};

const pendientesDeLimpiar = new Map<string, () => void>();

export const cliente = createClient<paths>({
  baseUrl: BASE,
  // `openapi-fetch` se queda con el `fetch` que haya al crear el cliente, y
  // eso ocurre al importar este módulo. Con esta indirección se resuelve en
  // cada llamada, que es lo que permite sustituirlo en los tests -- y lo que
  // evita quedarse con una versión anterior si algo lo envuelve más tarde.
  fetch: (peticion) => globalThis.fetch(peticion),
});
cliente.use(arranqueEnFrio);
cliente.use(autenticacion);

/**
 * Cerrar la sesión: la local siempre, y la del servidor si se puede.
 *
 * Se avisa al servidor para que revoque la familia entera, pero no se espera:
 * la sesión local se cierra igual. Si la petición falla, el token caduca solo
 * en 12 horas; dejar a alguien «dentro» porque el logout no llegó sería peor.
 */
export function salir(): void {
  const refreshToken = sesionActual()?.refreshToken;
  if (refreshToken !== undefined) {
    void cliente.POST('/auth/logout', { body: { refreshToken } }).catch(() => undefined);
  }
  cerrarSesion();
}

/** Solo para los tests: devuelve el módulo a su estado inicial. */
export function reiniciarEstadoDeRed(): void {
  ultimaRespuesta = null;
  esperasLentas = 0;
  refrescoEnVuelo = null;
  pendientesDeLimpiar.clear();
}
