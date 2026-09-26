/**
 * Lo que piden las pantallas de la jornada propia, y sus claves de caché.
 *
 * Las claves son un árbol a propósito: todo lo de fichaje cuelga de
 * `['fichaje']` y todo lo del resumen de `['dashboard']`. Fichar, añadir una
 * pausa o repartir cambian cosas de los dos árboles, y invalidar las dos
 * raíces vuelve a pedir lo que se esté viendo sin que cada escritura tenga
 * que saber qué pantallas hay abiertas.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir, pedirOpcional } from '../../api/consultas';
import type { components } from '../../api/schema';

export type Jornada = components['schemas']['TimeEntryResponse'];
export type ProyectosParaFichar = components['schemas']['ClockProjectsResponse'];
export type HorasDelDia = components['schemas']['DailyHoursResponse'];

export const CLAVES = {
  fichaje: ['fichaje'] as const,
  activo: ['fichaje', 'activo'] as const,
  hoy: ['fichaje', 'hoy'] as const,
  proyectos: ['fichaje', 'proyectos'] as const,
  historial: ['fichaje', 'historial'] as const,
  pausas: (fichajeId: number) => ['fichaje', fichajeId, 'pausas'] as const,
  imputaciones: (fichajeId: number) => ['fichaje', fichajeId, 'imputaciones'] as const,

  dashboard: ['dashboard'] as const,
  resumen: ['dashboard', 'resumen'] as const,
  horasPorDia: (desde: string, hasta: string) => ['dashboard', 'horas-por-dia', desde, hasta] as const,

  cuadrante: (desde: string, hasta: string) => ['cuadrantes', 'mio', desde, hasta] as const,
};

/** Lo que hay que volver a pedir después de cualquier cosa que cambie el tiempo trabajado. */
export const TRAS_CAMBIAR_TIEMPO = [CLAVES.fichaje, CLAVES.dashboard] as const;

export function useJornadaActiva() {
  return useQuery({
    queryKey: CLAVES.activo,
    // 204 sin cuerpo: no hay jornada abierta. No es un error.
    queryFn: () => pedirOpcional(cliente.GET('/api/v1/fichaje/activo', {})),
  });
}

export function pedirEstadoDeHoy() {
  return pedir(cliente.GET('/api/v1/fichaje/hoy', {}));
}

export function useEstadoDeHoy() {
  return useQuery({ queryKey: CLAVES.hoy, queryFn: pedirEstadoDeHoy });
}

export function useProyectosParaFichar() {
  return useQuery({
    queryKey: CLAVES.proyectos,
    queryFn: () => pedir(cliente.GET('/api/v1/fichaje/proyectos', {})),
  });
}

export function useResumen() {
  return useQuery({
    queryKey: CLAVES.resumen,
    queryFn: () => pedir(cliente.GET('/api/v1/dashboard/resumen', {})),
  });
}

export function useHorasPorDia(desde: string, hasta: string) {
  return useQuery({
    queryKey: CLAVES.horasPorDia(desde, hasta),
    queryFn: () => pedir(cliente.GET('/api/v1/dashboard/horas-por-dia', { params: { query: { desde, hasta } } })),
  });
}

/** El horario teórico de un día (fase B1): a qué hora tocaba entrar. */
export function useCuadranteDelDia(dia: string) {
  return useQuery({
    queryKey: CLAVES.cuadrante(dia, dia),
    queryFn: () => pedir(cliente.GET('/api/v1/cuadrantes/mio', { params: { query: { desde: dia, hasta: dia } } })),
    select: (dias) => dias[0] ?? null,
    // Fichar no cambia a qué hora tocaba entrar: no hace falta volver a pedirlo.
    staleTime: 10 * 60_000,
  });
}

/**
 * Los segundos trabajados de una jornada, ya descontadas las pausas.
 *
 * **En pausa, el reloj se para en el inicio de la pausa**: la pausa en curso
 * todavía no está en `segundosPausaAcumulados` (el servidor la suma al
 * reanudar), y contar hasta «ahora» la contaría como trabajo. Por eso el
 * backend manda `inicioPausaActual` desde W2. La app Android lo esquivaba
 * congelando el número en memoria, y al abrirla ya en pausa enseñaba 0.
 */
export function segundosDeJornada(jornada: Jornada | null, ahora: Date = new Date()): number {
  if (jornada === null || jornada.horaEntrada === undefined) return 0;
  const entrada = new Date(jornada.horaEntrada).getTime();
  const fin =
    jornada.horaSalida !== undefined
      ? new Date(jornada.horaSalida).getTime()
      : jornada.enPausa === true && jornada.inicioPausaActual !== undefined
        ? new Date(jornada.inicioPausaActual).getTime()
        : ahora.getTime();
  const brutos = (fin - entrada) / 1000;
  return Math.max(0, Math.floor(brutos - (jornada.segundosPausaAcumulados ?? 0)));
}
