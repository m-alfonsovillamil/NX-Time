/**
 * Mis ausencias y el calendario: todas las páginas antes de filtrar, pedir una
 * ausencia con sus reglas, y un mes con sus festivos y ausencias.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { Notificaciones } from '../../componentes/Notificaciones';
import { ausencias as A } from '../../i18n/es/ausencias';
import type { components } from '../../api/schema';
import { pintar, sesionDe, simularApi, type Manejador, type Ruta } from '../../pruebas/api';
import { Ausencias, filtrar } from './Ausencias';
import { Calendario, ausenciasDelDia, semanasDelMes } from './Calendario';

/** Como las manda el backend, con sus null. */
function ausencia(id: number, fechaInicio: string, fechaFin: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    fechaInicio,
    fechaFin,
    tipo: 'VACACIONES',
    motivo: null,
    estado: 'APROBADA',
    usuario: { nombre: 'Ana' },
    aprobadoPor: { nombre: 'Marta' },
    fechaResolucion: '2026-01-02T10:00:00Z',
    comentarioResolucion: null,
    diasHabiles: 5,
    ...extra,
  };
}

const DE_2025 = ausencia(1, '2025-12-29', '2026-01-02');
const DE_2026 = ausencia(2, '2026-08-03', '2026-08-07', { tipo: 'ASUNTOS_PROPIOS', estado: 'PENDIENTE', aprobadoPor: null });
const RECHAZADA = ausencia(3, '2026-09-21', '2026-09-21', { tipo: 'MEDICO', estado: 'RECHAZADA', comentarioResolucion: 'Ese día hay auditoría', diasHabiles: 1 });

function api(extra: Partial<Record<Ruta, Manejador>> = {}) {
  return simularApi({
    'GET /api/v1/ausencias/mis-peticiones': () => ({ contenido: [RECHAZADA, DE_2026, DE_2025], hayMas: false }),
    'GET /api/v1/ausencias/saldo-vacaciones': () => ({
      anio: 2026,
      diasTotales: 22,
      diasConsumidos: 5,
      diasPendientes: 5,
      diasDisponibles: 12,
    }),
    ...extra,
  });
}

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('mis ausencias', () => {
  /* ADR 027: el filtro es local, así que primero hacen falta TODAS las páginas. */
  it('pide todas las páginas antes de pintar', async () => {
    const llamadas = api({
      'GET /api/v1/ausencias/mis-peticiones': ({ query }) =>
        query.get('pagina') === '0'
          ? { contenido: [RECHAZADA, DE_2026], hayMas: true }
          : { contenido: [DE_2025], hayMas: false },
    });
    pintar(<Ausencias />, { sesion: sesionDe('EMPLEADO') });

    const tabla = await screen.findByRole('table', { name: A.tabla.titulo });
    expect(within(tabla).getAllByRole('row')).toHaveLength(4);
    expect(llamadas.a('GET', '/api/v1/ausencias/mis-peticiones').map((l) => l.query.get('pagina'))).toEqual(['0', '1']);
  });

  it('enseña el estado, los días hábiles del servidor y la respuesta', async () => {
    api();
    pintar(<Ausencias />, { sesion: sesionDe('EMPLEADO') });

    const fila = (await screen.findByText(A.respuesta('Ese día hay auditoría'))).closest('tr') as HTMLElement;
    expect(within(fila).getByText(A.estados.RECHAZADA)).toBeTruthy();
    expect(within(fila).getByText(A.diasHabiles(1))).toBeTruthy();
    expect(within(fila).getByText(A.tipos.MEDICO)).toBeTruthy();
  });

  it('el saldo de vacaciones del año', async () => {
    api();
    pintar(<Ausencias />, { sesion: sesionDe('EMPLEADO') });

    const disponibles = (await screen.findByText(A.saldo.disponibles)).parentElement;
    expect(disponibles?.textContent).toContain(A.saldo.dias(12));
  });

  it('filtrar por estado deja solo esas, y quitar los filtros las devuelve', async () => {
    api();
    pintar(<Ausencias />, { sesion: sesionDe('EMPLEADO') });
    await screen.findByRole('table');

    await userEvent.selectOptions(screen.getByLabelText(A.filtros.estado), 'PENDIENTE');
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(2);

    await userEvent.click(screen.getByRole('button', { name: A.filtros.quitar }));
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(4);
  });

  it('sin ninguna, lo dice', async () => {
    api({ 'GET /api/v1/ausencias/mis-peticiones': () => ({ contenido: [], hayMas: false }) });
    pintar(<Ausencias />, { sesion: sesionDe('EMPLEADO') });
    expect(await screen.findByText(A.vacioTitulo)).toBeTruthy();
  });
});

describe('el filtro por año', () => {
  /* La que cruza el año nuevo es de los dos años: si no, desaparecería de uno de ellos. */
  it('una ausencia de fin de año sale en los dos años', () => {
    const lista: components['schemas']['AbsenceResponse'][] = [
      { id: 1, fechaInicio: '2025-12-29', fechaFin: '2026-01-02' },
      { id: 2, fechaInicio: '2026-08-03', fechaFin: '2026-08-07' },
    ];
    expect(filtrar(lista, { anio: '2025', tipo: '', estado: '' }).map((a) => a.id)).toEqual([1]);
    expect(filtrar(lista, { anio: '2026', tipo: '', estado: '' }).map((a) => a.id)).toEqual([1, 2]);
  });
});

describe('solicitar una ausencia', () => {
  it('manda tipo y fechas, sin motivo si no se escribe, y lo confirma', async () => {
    const llamadas = api({ 'POST /api/v1/ausencias': () => ausencia(9, '2026-10-05', '2026-10-06', { estado: 'PENDIENTE' }) });
    pintar(
      <>
        <Ausencias />
        <Notificaciones />
      </>,
      { sesion: sesionDe('EMPLEADO') },
    );

    await userEvent.click(await screen.findByRole('button', { name: A.solicitar }));
    const dialogo = await screen.findByRole('dialog', { name: A.solicitud.titulo });
    await userEvent.selectOptions(within(dialogo).getByLabelText(A.solicitud.tipo), 'ASUNTOS_PROPIOS');
    await userEvent.type(within(dialogo).getByLabelText(A.solicitud.desde), '2026-10-05');
    // El fin se propone igual que el inicio; se cambia.
    expect((within(dialogo).getByLabelText(A.solicitud.hasta) as HTMLInputElement).value).toBe('2026-10-05');
    await userEvent.clear(within(dialogo).getByLabelText(A.solicitud.hasta));
    await userEvent.type(within(dialogo).getByLabelText(A.solicitud.hasta), '2026-10-06');
    await userEvent.click(within(dialogo).getByRole('button', { name: A.solicitud.enviar }));

    expect(await screen.findByText(A.solicitud.enviada)).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/ausencias').map((l) => l.cuerpo)).toEqual([
      { tipo: 'ASUNTOS_PROPIOS', fechaInicio: '2026-10-05', fechaFin: '2026-10-06' },
    ]);
    // La lista se vuelve a pedir: la nueva tiene que aparecer.
    await waitFor(() => expect(llamadas.a('GET', '/api/v1/ausencias/mis-peticiones').length).toBeGreaterThanOrEqual(2));
  });

  it('sin fechas, o con el fin antes del inicio, no se manda nada', async () => {
    const llamadas = api();
    pintar(<Ausencias />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: A.solicitar }));
    const dialogo = await screen.findByRole('dialog', { name: A.solicitud.titulo });
    await userEvent.click(within(dialogo).getByRole('button', { name: A.solicitud.enviar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(A.solicitud.fechasIncompletas);

    await userEvent.type(within(dialogo).getByLabelText(A.solicitud.desde), '2026-10-05');
    await userEvent.clear(within(dialogo).getByLabelText(A.solicitud.hasta));
    await userEvent.type(within(dialogo).getByLabelText(A.solicitud.hasta), '2026-10-01');
    await userEvent.click(within(dialogo).getByRole('button', { name: A.solicitud.enviar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(A.solicitud.fechaInvertida);

    expect(llamadas.a('POST', '/api/v1/ausencias')).toHaveLength(0);
  });
});

describe('el calendario', () => {
  const MES = {
    anio: 2026,
    mes: 10,
    incluyeEquipo: false,
    festivos: [{ id: 1, fecha: '2026-10-12', descripcion: 'Fiesta Nacional de España', ambito: 'NACIONAL', editable: false }],
    ausencias: [
      { id: 7, usuarioId: 1, usuario: 'Ana', fechaInicio: '2026-10-05', fechaFin: '2026-10-06', tipo: 'VACACIONES', estado: 'APROBADA', propia: true },
      { id: 8, usuarioId: 2, usuario: 'Luis', fechaInicio: '2026-10-05', fechaFin: '2026-10-05', tipo: 'MEDICO', estado: 'RECHAZADA', propia: false },
    ],
  };

  it('las semanas empiezan en lunes y completan con los días de los meses de al lado', () => {
    const semanas = semanasDelMes(2026, 10);
    expect(semanas[0]).toEqual(['2026-09-28', '2026-09-29', '2026-09-30', '2026-10-01', '2026-10-02', '2026-10-03', '2026-10-04']);
    expect(semanas.at(-1)?.at(-1)).toBe('2026-11-01');
  });

  it('una ausencia rechazada no ocupa el día', () => {
    const delMes: components['schemas']['CalendarAbsenceDTO'][] = [
      { id: 7, fechaInicio: '2026-10-05', fechaFin: '2026-10-06', estado: 'APROBADA' },
      { id: 8, fechaInicio: '2026-10-05', fechaFin: '2026-10-05', estado: 'RECHAZADA' },
    ];
    expect(ausenciasDelDia(delMes, '2026-10-05').map((a) => a.id)).toEqual([7]);
  });

  it('cada día dice lo que tiene, también a quien no ve la pantalla', async () => {
    simularApi({ 'GET /api/v1/calendario': () => MES });
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-20T10:00:00Z'));
    try {
      pintar(<Calendario />, { sesion: sesionDe('EMPLEADO') });

      const festivo = await screen.findByRole('button', { name: /12 de octubre.*Festivo, Fiesta Nacional de España/ });
      expect(festivo).toBeTruthy();
      await userEvent.click(screen.getByRole('button', { name: /5 de octubre.*1 persona ausente/ }));
      expect(await screen.findByText(A.calendario.propia)).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it('el interruptor del equipo solo sale con permiso, y pide el mes con el equipo', async () => {
    const llamadas = simularApi({ 'GET /api/v1/calendario': () => MES });
    pintar(<Calendario />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByLabelText(A.calendario.verEquipo));

    await waitFor(() =>
      expect(llamadas.a('GET', '/api/v1/calendario').map((l) => l.query.get('equipo'))).toContain('true'),
    );
  });

  it('un EMPLEADO no ve el interruptor del equipo', async () => {
    simularApi({ 'GET /api/v1/calendario': () => MES });
    pintar(<Calendario />, { sesion: sesionDe('EMPLEADO') });

    await screen.findByRole('table');
    expect(screen.queryByLabelText(A.calendario.verEquipo)).toBeNull();
  });
});
