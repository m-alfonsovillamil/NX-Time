/**
 * Lo que piden las pantallas de ausencias y del calendario, y sus claves.
 *
 * Solicitar una ausencia cambia las tres cosas que cuelgan de aquí (la lista,
 * el saldo y el calendario) y el resumen de «Mi jornada» (ausencias sin
 * responder), así que la solicitud invalida los tres árboles.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir, todasLasPaginas } from '../../api/consultas';
import type { components } from '../../api/schema';

export type Ausencia = components['schemas']['AbsenceResponse'];
export type DiaDelCalendario = components['schemas']['CalendarResponse'];

export const CLAVES_AUSENCIAS = {
  ausencias: ['ausencias'] as const,
  mias: ['ausencias', 'mias'] as const,
  saldo: (anio: number) => ['ausencias', 'saldo', anio] as const,
  calendario: ['calendario'] as const,
  mes: (anio: number, mes: number, equipo: boolean) => ['calendario', anio, mes, equipo] as const,
};

export const TRAS_SOLICITAR = [CLAVES_AUSENCIAS.ausencias, CLAVES_AUSENCIAS.calendario, ['dashboard']] as const;

/**
 * **Todas** mis peticiones, de todas las páginas.
 *
 * Los filtros de año, tipo y estado son locales, como en la app: filtrar solo
 * la primera página escondería las de años anteriores sin avisar (ADR 027).
 * Son unas pocas decenas por persona y año.
 */
export function useMisAusencias() {
  return useQuery({
    queryKey: CLAVES_AUSENCIAS.mias,
    queryFn: () =>
      todasLasPaginas((pagina) =>
        pedir(cliente.GET('/api/v1/ausencias/mis-peticiones', { params: { query: { pagina, tamano: 200 } } })),
      ),
  });
}

export function useSaldoDeVacaciones(anio: number) {
  return useQuery({
    queryKey: CLAVES_AUSENCIAS.saldo(anio),
    queryFn: () => pedir(cliente.GET('/api/v1/ausencias/saldo-vacaciones', { params: { query: { anio } } })),
  });
}

export function useMesDelCalendario(anio: number, mes: number, equipo: boolean) {
  return useQuery({
    queryKey: CLAVES_AUSENCIAS.mes(anio, mes, equipo),
    queryFn: () => pedir(cliente.GET('/api/v1/calendario', { params: { query: { anio, mes, equipo } } })),
    // Pasar de un mes al siguiente y volver no debería parpadear.
    placeholderData: (anterior) => anterior,
  });
}
