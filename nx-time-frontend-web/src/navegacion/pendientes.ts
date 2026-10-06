/**
 * Lo que espera una decisión de quien gestiona: las bandejas y cuánto hay en cada una.
 *
 * Lo usan el panel de gestión (las cifras grandes) y el menú (el número al
 * lado de cada entrada), **con la misma consulta y la misma clave de caché**:
 * un «3» en el menú que en el panel son dos sería peor que no poner nada.
 * Resolver algo en una bandeja invalida `['dashboard']`, así que los dos se
 * ponen al día a la vez.
 *
 * **Cada número es exactamente lo que se ve al abrir su bandeja** (lo
 * garantiza `/dashboard/pendientes`), no un total de la empresa. Sin permiso
 * para una bandeja, no hay número; y tampoco el de una bandeja que la web aún
 * no tenga, porque no llevaría a ningún sitio.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../api/cliente';
import { pedir } from '../api/consultas';
import type { components } from '../api/schema';
import { useSesion } from '../api/useSesion';
import { menuPara } from './secciones';

/** La clave de los contadores: la invalida quien resuelve algo de una bandeja. */
export const CLAVE_PENDIENTES = ['dashboard', 'pendientes'] as const;

export type Pendientes = components['schemas']['PendingWorkResponse'];

export interface Bandeja {
  /** La ruta de la sección a la que lleva, la misma que en el catálogo. */
  ruta: string;
  cuantos: (p: Pendientes) => number;
}

const BANDEJAS: readonly Bandeja[] = [
  { ruta: 'ausencias-equipo/pendientes', cuantos: (p) => p.ausencias ?? 0 },
  { ruta: 'correcciones/pendientes', cuantos: (p) => p.correcciones ?? 0 },
  { ruta: 'horas-extra', cuantos: (p) => p.horasExtra ?? 0 },
  { ruta: 'borrados', cuantos: (p) => p.borrados ?? 0 },
];

/** El permiso sin el que `/dashboard/pendientes` responde 403. */
const PERMISO = 'fichaje:leer:equipo';

/** Las bandejas que esta persona puede abrir y que la web ya tiene. */
export function bandejasPara(authorities: readonly string[]): Bandeja[] {
  if (!authorities.includes(PERMISO)) return [];
  const abiertas = new Set(menuPara(authorities).map((s) => s.ruta));
  // «Horas extra» también es de lo mío y está en el menú de todos: la bandeja
  // del equipo pide su propio permiso.
  return BANDEJAS.filter(
    (b) => abiertas.has(b.ruta) && (b.ruta !== 'horas-extra' || authorities.includes('horasextra:revisar')),
  );
}

const CADA_CINCO_MINUTOS = 5 * 60_000;

/** La consulta de los contadores. Sin el permiso, ni se pide. */
export function usePendientes() {
  const { sesion } = useSesion();
  return useQuery({
    queryKey: CLAVE_PENDIENTES,
    queryFn: () => pedir(cliente.GET('/api/v1/dashboard/pendientes', {})),
    enabled: (sesion?.authorities ?? []).includes(PERMISO),
    // Como la campana (ADR 034): cada cinco minutos y solo con la pestaña a la
    // vista, para no tener despierta la base por una pestaña olvidada.
    refetchInterval: CADA_CINCO_MINUTOS,
  });
}

/**
 * Para el menú: cuánto hay pendiente en cada ruta. Solo las que tienen algo;
 * mientras carga o si falla, ninguna (un número en el menú es un extra: su
 * fallo no se enseña).
 */
export function usePendientesPorRuta(): ReadonlyMap<string, number> {
  const { sesion } = useSesion();
  const { data } = usePendientes();
  const porRuta = new Map<string, number>();
  if (data === undefined) return porRuta;
  for (const bandeja of bandejasPara(sesion?.authorities ?? [])) {
    const n = bandeja.cuantos(data);
    if (n > 0) porRuta.set(bandeja.ruta, n);
  }
  return porRuta;
}
