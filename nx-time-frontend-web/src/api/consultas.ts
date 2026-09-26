/**
 * Cómo pide datos cada pantalla: TanStack Query encima de `cliente.ts`.
 *
 * Hasta W0 la única pantalla con datos, `Fichar.tsx`, hacía `useState` +
 * `useEffect` a mano. Con las treinta del plan (ADR 029) serían treinta copias
 * de cargar, fallar, reintentar, invalidar tras escribir y paginar, y cada una
 * con su propio despiste. TanStack Query da eso una vez, más dos cosas que aquí
 * importan de verdad:
 *
 * - **Volver a la pestaña refresca.** Quien ficha desde el móvil y vuelve a la
 *   web ve la jornada como está, no como la dejó (`refetchOnWindowFocus`).
 * - **Una escritura invalida lo que cambia**, por clave, sin que la pantalla
 *   tenga que saber quién más enseña ese dato.
 *
 * Lo que no hace TanStack Query lo hace `cliente.ts` y no se toca: el refresco
 * del 401 serializado y la espera larga del arranque en frío de Render.
 *
 * Aquí solo está el pegamento: convertir la respuesta de `openapi-fetch` en
 * datos o en un error con el mensaje ya listo para enseñar ([pedir]), y los dos
 * hooks que se repiten en todas las pantallas ([useMutacion] y
 * [useListaPaginada]). Para leer se usa `useQuery` directamente: envolverlo no
 * aportaría nada que no dé ya `pedir`.
 */

import {
  QueryClient,
  useInfiniteQuery,
  useMutation,
  useQueryClient,
  type QueryKey,
} from '@tanstack/react-query';
import { useMemo } from 'react';

import { notificar } from '../componentes/Notificaciones';
import { mensajeDeError, mensajeDeRed } from '../util/errores';

/**
 * Un fallo con el mensaje ya pensado para quien lo lee.
 *
 * `status` es `null` cuando no llegó respuesta (sin red, CORS, timeout del
 * arranque en frío): así se distingue «el servidor dijo que no» de «no se
 * sabe qué dijo», que es lo que decide si merece la pena reintentar.
 */
export class ErrorDeApi extends Error {
  constructor(
    message: string,
    readonly status: number | null,
  ) {
    super(message);
    this.name = 'ErrorDeApi';
  }
}

/** La forma que devuelve `openapi-fetch`, sin atarse a una ruta concreta. */
interface Respuesta<D> {
  data?: D;
  error?: unknown;
  response: Response;
}

async function esperar<D>(peticion: Promise<Respuesta<D>>): Promise<Respuesta<D>> {
  try {
    return await peticion;
  } catch (fallo) {
    throw new ErrorDeApi(mensajeDeRed(fallo), null);
  }
}

/**
 * Los datos de una respuesta, o un [ErrorDeApi] con el `detail` del servidor.
 *
 * Es lo que va dentro de cada `queryFn` y `mutationFn`:
 * `pedir(cliente.GET('/api/v1/...', {}))`.
 */
export async function pedir<D>(peticion: Promise<Respuesta<D>>): Promise<D> {
  const { data, error, response } = await esperar(peticion);
  if (!response.ok) throw new ErrorDeApi(mensajeDeError(error, response.status), response.status);
  // Un 204 de una ruta que no devuelve nada (marcar leído, borrar) llega sin
  // `data`, y su tipo ya lo dice: no hay nada que comprobar.
  return data as D;
}

/**
 * Como [pedir], pero con el código de la respuesta.
 *
 * Para las rutas en las que el código **es** la respuesta: repartir una
 * jornada contesta 200 si se ha aplicado y 202 si se ha pedido, con dos
 * cuerpos distintos, y la pantalla tiene que decir cuál de las dos ha pasado.
 */
export async function pedirConEstado<D>(peticion: Promise<Respuesta<D>>): Promise<{ datos: D; status: number }> {
  const { data, error, response } = await esperar(peticion);
  if (!response.ok) throw new ErrorDeApi(mensajeDeError(error, response.status), response.status);
  return { datos: data as D, status: response.status };
}

/**
 * Como [pedir], pero un 204 es «no hay» y no un error.
 *
 * Existe por `GET /fichaje/activo`, que responde 204 cuando no hay jornada
 * abierta: la ausencia de jornada es un estado normal de la pantalla.
 */
export async function pedirOpcional<D>(peticion: Promise<Respuesta<D>>): Promise<D | null> {
  const { data, error, response } = await esperar(peticion);
  if (!response.ok) throw new ErrorDeApi(mensajeDeError(error, response.status), response.status);
  return response.status === 204 || data === undefined ? null : data;
}

/**
 * Se reintenta una vez lo que pudo ser pasajero, y nunca un 4xx.
 *
 * Un 400 o un 403 no cambian por insistir, y reintentarlos solo retrasa el
 * mensaje. Un 502 sí puede ser Render levantando la instancia; para esperas
 * largas ya está el arranque en frío de `cliente.ts`, así que basta con una.
 */
function mereceReintentar(fallos: number, error: unknown): boolean {
  if (fallos >= 1) return false;
  return !(error instanceof ErrorDeApi && error.status !== null && error.status < 500);
}

export function crearClienteDeConsultas({ sinReintentos = false } = {}): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: sinReintentos ? false : mereceReintentar,
        // Medio minuto: navegar entre pantallas no vuelve a pedir lo que se
        // acaba de pedir, y volver a la pestaña sí (eso no mira `staleTime`
        // si el dato ya está pasado).
        staleTime: 30_000,
      },
      mutations: { retry: false },
    },
  });
}

/* ------------------------------------------------------------------ */
/* Escribir                                                            */
/* ------------------------------------------------------------------ */

interface OpcionesDeMutacion<V, D> {
  /** Lo que ha cambiado. Se invalida al terminar bien, y con ello se vuelve a pedir si se está viendo. */
  invalida?: readonly QueryKey[];
  /** Si se da, se enseña como notificación al terminar bien. */
  exito?: string | ((datos: D, variables: V) => string);
  /** Para lo que la pantalla quiera hacer con la respuesta (cerrar un diálogo, navegar). */
  alTerminar?: (datos: D, variables: V) => void;
}

/**
 * Una escritura: `useMutation` más invalidar y avisar.
 *
 * El error **no** se notifica aquí: lo enseña la pantalla, al lado del botón
 * que lo ha causado (`mutacion.error?.message`). Un aviso flotante para un
 * error de formulario se va antes de que se lea y deja el formulario sin
 * explicación.
 */
export function useMutacion<V, D>(
  hacer: (variables: V) => Promise<D>,
  { invalida = [], exito, alTerminar }: OpcionesDeMutacion<V, D> = {},
) {
  const consultas = useQueryClient();
  return useMutation<D, ErrorDeApi, V>({
    mutationFn: hacer,
    onSuccess: async (datos, variables) => {
      alTerminar?.(datos, variables);
      if (exito !== undefined) notificar(typeof exito === 'string' ? exito : exito(datos, variables));
      await Promise.all(invalida.map((clave) => consultas.invalidateQueries({ queryKey: clave })));
    },
  });
}

/* ------------------------------------------------------------------ */
/* Listas por páginas (ADR 027)                                        */
/* ------------------------------------------------------------------ */

/** Lo común a todos los `PaginaDTO*` del contrato. */
export interface Pagina<T> {
  contenido?: T[];
  hayMas?: boolean;
}

/**
 * Quita repetidos por `id`, quedándose con la primera aparición.
 *
 * Hacen falta, no son teoría: si entra un elemento nuevo mientras se lee, la
 * página siguiente empieza un puesto más tarde y repite el último de la
 * anterior (ADR 027). Pintarlo dos veces confunde, y con `key={id}` React
 * además avisa.
 */
export function sinRepetidos<T extends { id?: number }>(elementos: readonly T[]): T[] {
  const vistos = new Set<number>();
  return elementos.filter((e) => {
    if (e.id === undefined) return true;
    if (vistos.has(e.id)) return false;
    vistos.add(e.id);
    return true;
  });
}

/**
 * Una lista que se lee al llegar al final: avisos, ausencias del equipo, historial reciente.
 *
 * Va con [FinDeLista], que pide la página siguiente al hacerse visible y
 * ofrece reintentar si falla, como `finDeLista` en Android.
 */
export function useListaPaginada<T extends { id?: number }>(
  clave: QueryKey,
  pedirPagina: (pagina: number) => Promise<Pagina<T>>,
  { activa = true }: { activa?: boolean } = {},
) {
  const consulta = useInfiniteQuery({
    queryKey: clave,
    queryFn: ({ pageParam }) => pedirPagina(pageParam),
    initialPageParam: 0,
    getNextPageParam: (ultima, _todas, pagina) => (ultima.hayMas === true ? pagina + 1 : undefined),
    enabled: activa,
  });
  const paginas = consulta.data?.pages;
  const elementos = useMemo(() => sinRepetidos(paginas?.flatMap((p) => p.contenido ?? []) ?? []), [paginas]);
  return { ...consulta, elementos };
}

/** Por si un servidor con un fallo dijera siempre `hayMas`: mejor cortar que colgar la pestaña. */
const MAXIMO_DE_PAGINAS = 100;

/**
 * Todas las páginas de una lista, de una vez.
 *
 * Para las que **suman o filtran** un periodo: un total de horas hecho con la
 * primera página es un total falso, y eso es peor que tardar (ADR 027). Las
 * demás listas van con [useListaPaginada].
 */
export async function todasLasPaginas<T extends { id?: number }>(
  pedirPagina: (pagina: number) => Promise<Pagina<T>>,
): Promise<T[]> {
  const todos: T[] = [];
  for (let pagina = 0; pagina < MAXIMO_DE_PAGINAS; pagina++) {
    const actual = await pedirPagina(pagina);
    todos.push(...(actual.contenido ?? []));
    if (actual.hayMas !== true) break;
  }
  return sinRepetidos(todos);
}
