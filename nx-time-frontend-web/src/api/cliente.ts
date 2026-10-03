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
 *    compartida. Y como desde el ADR 030 el refresh es una cookie **que
 *    comparten todas las pestañas**, la promesa no basta: dos pestañas que
 *    renuevan a la vez son el mismo problema, y lo resuelve un cerrojo del
 *    navegador (`navigator.locks`) alrededor de cada refresco.
 *
 * 2. **El arranque en frío de Render.** El plan gratuito duerme el servicio a
 *    los 15 minutos, y despertarlo ha llegado a tardar tres minutos medidos.
 *    Con el timeout por defecto del navegador la primera petición de la mañana
 *    no va lenta: no entra. Aquí se espera hasta 300 s cuando el servidor
 *    puede estar dormido, y se avisa por pantalla a los 3 s — sin el aviso se
 *    ve una página congelada y se cierra antes de que responda.
 *
 * Y una tercera desde la fase W1: **la sesión sobrevive a recargar** (ADR 030).
 * El refresh vive en una cookie `HttpOnly` que el servidor pone y lee; aquí
 * solo se manda con `credentials: 'include'` y se acompaña de la cabecera CSRF.
 */

import createClient, { type Middleware } from 'openapi-fetch';

import type { paths } from './schema';
import { abrirSesion, cerrarSesion, sesionActual } from './sesion';

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

/* ------------------------------------------------------------------ */
/* La cookie de la sesión y su CSRF (ADR 030)                           */
/* ------------------------------------------------------------------ */

const COOKIE_CSRF = 'nx_csrf';
const CABECERA_CSRF = 'X-CSRF-Token';

/**
 * El valor de la cookie CSRF, que el servidor pone legible a propósito.
 *
 * El doble envío funciona porque **otra web no puede leer esta cookie**, así
 * que no puede copiarla en la cabecera: el servidor exige que las dos
 * coincidan antes de aceptar la cookie del refresh.
 */
export function valorCsrf(): string | null {
  if (typeof document === 'undefined') return null;
  for (const trozo of document.cookie.split(';')) {
    const [nombre, ...valor] = trozo.trim().split('=');
    if (nombre === COOKIE_CSRF) {
      const texto = valor.join('=');
      return texto === '' ? null : decodeURIComponent(texto);
    }
  }
  return null;
}

/**
 * Un cerrojo compartido por todas las pestañas del navegador.
 *
 * El refresh es una cookie de todas ellas, y el servidor lo rota: si dos
 * pestañas renovaran a la vez, la segunda llegaría con uno ya usado y el
 * servidor revocaría la sesión de todas. Con el cerrojo van de una en una, y
 * la segunda manda la cookie ya rotada por la primera, porque el navegador
 * pone la cookie al enviar, no al preparar la petición.
 *
 * Sin `navigator.locks` (navegadores de antes de 2022, los tests) se va sin
 * cerrojo: queda la promesa compartida dentro de cada pestaña.
 */
async function conCerrojo<T>(hacer: () => Promise<T>): Promise<T> {
  const cerrojos = globalThis.navigator?.locks;
  if (cerrojos === undefined) return hacer();
  return await cerrojos.request('nx-refresco-de-sesion', () => hacer());
}

/** Otras pestañas se enteran al momento de que se ha cerrado la sesión. */
const canal = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('nx-sesion');
canal?.addEventListener('message', (evento: MessageEvent) => {
  if (evento.data === 'salir') cerrarSesion();
});

interface RespuestaDeSesion {
  token?: string;
  nombre?: string;
  authorities?: string[];
  zonaHoraria?: string;
}

async function pedirTokensNuevos(): Promise<string | null> {
  const csrf = valorCsrf();
  // Sin la cookie CSRF no hay sesión que renovar: ni se pregunta (y no se
  // despierta al servidor dormido por nada).
  if (csrf === null) return null;
  try {
    const respuesta = await conCerrojo(() =>
      fetch(`${BASE}/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
        // El valor se lee otra vez aquí dentro: si esperó al cerrojo, otra
        // pestaña puede haber renovado y cambiado la cookie mientras tanto.
        headers: { [CABECERA_CSRF]: valorCsrf() ?? csrf },
        signal: AbortSignal.timeout(puedeEstarDormido() ? ESPERA_LARGA_MS : ESPERA_CORTA_MS),
      }),
    );
    anotarRespuesta(respuesta.status);
    if (!respuesta.ok) return null;

    const cuerpo = (await respuesta.json()) as RespuestaDeSesion;
    if (cuerpo.token === undefined) return null;
    // El nombre, las authorities y la zona también: un cambio de rol o de
    // zona llega así.
    abrirSesion({
      accessToken: cuerpo.token,
      nombre: cuerpo.nombre ?? sesionActual()?.nombre ?? '',
      authorities: cuerpo.authorities ?? sesionActual()?.authorities ?? [],
      zonaHoraria: cuerpo.zonaHoraria ?? sesionActual()?.zonaHoraria,
    });
    return cuerpo.token;
  } catch {
    // Sin red, o el servidor no llegó a responder. No se distingue de un
    // refresh caducado desde aquí, y en los dos casos hay que volver a entrar.
    return null;
  }
}

/** Una sola renovación en vuelo por pestaña: quien llega después espera a esta. */
function refrescar(): Promise<string | null> {
  refrescoEnVuelo ??= pedirTokensNuevos().finally(() => {
    refrescoEnVuelo = null;
  });
  return refrescoEnVuelo;
}

let restauracion: Promise<boolean> | null = null;

/**
 * Al abrir la web: si hay una sesión en la cookie, se retoma sin pasar por el
 * login. Es lo que hace que recargar ya no eche a nadie.
 *
 * Una sola vez por carga de página, aunque se llame más (React en modo
 * estricto monta dos veces en desarrollo).
 */
export function restaurarSesion(): Promise<boolean> {
  restauracion ??= refrescar().then((token) => token !== null);
  return restauracion;
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

  const nuevo = await refrescar();
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
  async onRequest({ request, id }) {
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
    pendientesDeLimpiar.set(id, limpiar);

    return new Request(request, { signal: senal });
  },

  async onResponse({ id, response }) {
    pendientesDeLimpiar.get(id)?.();
    pendientesDeLimpiar.delete(id);
    return response;
  },

  async onError({ id }) {
    pendientesDeLimpiar.get(id)?.();
    pendientesDeLimpiar.delete(id);
    return undefined;
  },
};

/*
 * Por el id de cada petición, que openapi-fetch da a los tres ganchos, y no
 * por su URL (4/10/2026). Con la URL, dos peticiones iguales a la vez -- el
 * kiosco preguntando por el emparejamiento, o una consulta repetida al volver
 * a la pestaña -- compartían entrada: la segunda pisaba la limpieza de la
 * primera, y si la primera había tardado más de tres segundos (el arranque en
 * frío), el aviso de «el servidor está despertando» no se iba nunca.
 */
const pendientesDeLimpiar = new Map<string, () => void>();

/**
 * Quita los `null` de las respuestas JSON: en la web, un campo vacío es
 * **siempre** `undefined`.
 *
 * El backend (Jackson) manda los campos vacíos como `"enCurso": null`, pero
 * los tipos generados del contrato los declaran opcionales (`enCurso?: …`),
 * es decir, `undefined`. Con esa mentira en los tipos, `x !== undefined` deja
 * pasar el `null` y lo siguiente es `null.codigo`: así se quedó en blanco
 * «Mi jornada» al fichar contra el backend de verdad (W2), mientras los tests
 * —con datos simulados que omitían el campo— seguían en verde.
 *
 * Arreglarlo aquí, una vez, hace que los tipos digan la verdad en todas las
 * pantallas. Solo se quitan propiedades de objetos: un `null` dentro de un
 * array se queda donde está, porque ahí quitarlo movería los demás.
 */
const sinNulos: Middleware = {
  async onResponse({ response, options }) {
    // Una descarga (`parseAs: 'blob'`) no se toca: el JSON de «descargar mis
    // datos» es una copia legal de lo que hay guardado, y tiene que salir
    // exactamente como está, con sus null.
    if (options.parseAs !== 'json') return response;
    if (!(response.headers.get('Content-Type') ?? '').includes('json')) return response;
    const texto = await response.clone().text();
    if (texto === '') return response;
    let limpio: string;
    try {
      limpio = JSON.stringify(
        JSON.parse(texto, function (this: unknown, _clave, valor: unknown) {
          return valor === null && !Array.isArray(this) ? undefined : valor;
        }),
      );
    } catch {
      return response;
    }
    return new Response(limpio, { status: response.status, statusText: response.statusText, headers: response.headers });
  },
};

export const cliente = createClient<paths>({
  baseUrl: BASE,
  // Para que el navegador mande y guarde las cookies de la sesión (ADR 030).
  // Con la API en otro subdominio, sin esto las ignoraría.
  credentials: 'include',
  // `openapi-fetch` se queda con el `fetch` que haya al crear el cliente, y
  // eso ocurre al importar este módulo. Con esta indirección se resuelve en
  // cada llamada, que es lo que permite sustituirlo en los tests -- y lo que
  // evita quedarse con una versión anterior si algo lo envuelve más tarde.
  fetch: (peticion) => globalThis.fetch(peticion),
});
// El primero en registrarse es el último en ver la respuesta: los `null` se
// quitan también de la que llega tras reintentar un 401.
cliente.use(sinNulos);
cliente.use(arranqueEnFrio);
cliente.use(autenticacion);

/**
 * El cliente de la tablet del kiosco (ADR 033).
 *
 * Aparte del de las personas porque no tiene nada de su sesión: ni cookies, ni
 * refresco, ni `Bearer`. Lleva el token del kiosco con su propio esquema
 * (`Authorization: Kiosco …`), que el backend solo acepta en `/kiosco/**`. Sí
 * comparte el aviso de servidor dormido: es justo donde más se nota, con gente
 * esperando a fichar.
 *
 * @param token se lee en cada petición: la tablet lo recibe a mitad de camino,
 *   al emparejarse, y lo olvida si el servidor dice que ya no vale.
 */
export function clienteDeKiosco(token: () => string | null) {
  const deKiosco = createClient<paths>({ baseUrl: BASE, fetch: (peticion) => globalThis.fetch(peticion) });
  deKiosco.use(sinNulos);
  deKiosco.use(arranqueEnFrio);
  deKiosco.use({
    async onRequest({ request }) {
      const actual = token();
      if (actual !== null) request.headers.set('Authorization', `Kiosco ${actual}`);
      return request;
    },
    async onResponse({ response }) {
      anotarRespuesta(response.status);
      return response;
    },
  });
  return deKiosco;
}

/**
 * Cerrar la sesión: la local siempre, la del servidor si se puede, y la de
 * las demás pestañas.
 *
 * Se avisa al servidor para que revoque la familia entera y borre las
 * cookies, pero no se espera: la sesión local se cierra igual. Si la petición
 * falla, el token caduca solo en 12 horas; dejar a alguien «dentro» porque el
 * logout no llegó sería peor.
 */
export function salir(): void {
  const csrf = valorCsrf();
  if (csrf !== null) {
    void fetch(`${BASE}/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: { [CABECERA_CSRF]: csrf },
    }).catch(() => undefined);
  }
  canal?.postMessage('salir');
  cerrarSesion();
}

/** Solo para los tests: devuelve el módulo a su estado inicial. */
export function reiniciarEstadoDeRed(): void {
  ultimaRespuesta = null;
  esperasLentas = 0;
  refrescoEnVuelo = null;
  restauracion = null;
  pendientesDeLimpiar.clear();
}
