/**
 * La analítica: absentismo y puntualidad.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { analitica } from '../../i18n/es/analitica';
import { pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { fechaCorta } from '../../util/fechas';
import { Analitica, textoDeVentana } from './Analitica';

const A = analitica;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const VENTANA = { periodo: 'MES', desde: '2026-09-01', hasta: '2026-09-30', evaluadoHasta: '2026-09-26', alcance: 'EMPRESA', departamento: null };

const RESUMEN = {
  ventana: VENTANA,
  personas: 5,
  absentismo: 3.2,
  absentismoSinJustificar: 0.8,
  puntualidad: 91.5,
  retrasoMedioMinutos: 12.4,
  jornadasIncompletas: 1.1,
  minutosMediosPorDia: 470,
};

function filaAbsentismo(id: number | null, nombre: string, absentismo: number | null, extra: Record<string, unknown> = {}) {
  return {
    id,
    nombre,
    personas: 2,
    diasLaborables: 36,
    diasTrabajados: 34,
    diasAusenciaJustificada: 1,
    diasSinFichaje: 1,
    diasVacaciones: 2,
    absentismo,
    absentismoSinJustificar: 0.5,
    motivos: [],
    ...extra,
  };
}

const ABSENTISMO = {
  ventana: VENTANA,
  agrupacion: 'DEPARTAMENTO',
  total: filaAbsentismo(null, 'Total', 3.2, { motivos: [{ motivo: 'MEDICO', etiqueta: 'Consulta médica', dias: 3 }] }),
  filas: [filaAbsentismo(1, 'Ingeniería', 4.1), filaAbsentismo(2, 'Producto', 1.9)],
};

describe('analítica', () => {
  it('la ventana dice el periodo, hasta cuándo se calculó y de quién es', () => {
    expect(textoDeVentana(VENTANA as never)).toBe(
      `${A.ventana(fechaCorta('2026-09-01'), fechaCorta('2026-09-30'))}, ${A.evaluado(fechaCorta('2026-09-26'))} · ${A.todaLaEmpresa}`,
    );
    expect(textoDeVentana({ ...VENTANA, evaluadoHasta: null, alcance: 'DEPARTAMENTO', departamento: 'Ingeniería' } as never)).toContain(
      `${A.sinDias} · ${A.departamento('Ingeniería')}`,
    );
  });

  it('las cifras, el absentismo por departamento con su desglose por motivo, y cambiar de agrupación', async () => {
    const llamadas = simularApi({
      'GET /api/v1/analitica/resumen': () => RESUMEN,
      'GET /api/v1/analitica/absentismo': ({ query }) => (query.get('agrupar') === 'EMPLEADO' ? { ...ABSENTISMO, filas: [] } : ABSENTISMO),
    });
    pintar(<Analitica />, { sesion: sesionDe('RRHH'), ruta: '/analitica?periodo=TRIMESTRE' });

    expect(await screen.findByText('91,5 %')).toBeTruthy();
    expect(screen.getByText(A.minutosRetraso(12.4))).toBeTruthy();
    const tabla = await screen.findByRole('table', { name: A.tablaAbsentismo });
    expect(within(tabla).getByRole('cell', { name: 'Ingeniería' })).toBeTruthy();
    expect(within(tabla).getByRole('cell', { name: A.total })).toBeTruthy();
    expect(screen.getByText('Consulta médica')).toBeTruthy();
    const [pedida] = llamadas.a('GET', '/api/v1/analitica/absentismo');
    expect([pedida?.query.get('periodo'), pedida?.query.get('agrupar')]).toEqual(['TRIMESTRE', 'DEPARTAMENTO']);

    await userEvent.selectOptions(screen.getByLabelText(A.agrupar), 'EMPLEADO');
    await waitFor(() => expect(llamadas.a('GET', '/api/v1/analitica/absentismo').map((l) => l.query.get('agrupar'))).toContain('EMPLEADO'));
  });

  it('sin días terminados, rayas y no ceros', async () => {
    simularApi({
      'GET /api/v1/analitica/resumen': () => ({
        ...RESUMEN,
        ventana: { ...VENTANA, evaluadoHasta: null },
        absentismo: null,
        absentismoSinJustificar: null,
        puntualidad: null,
        retrasoMedioMinutos: null,
        jornadasIncompletas: null,
        minutosMediosPorDia: null,
      }),
      'GET /api/v1/analitica/absentismo': () => ({ ...ABSENTISMO, filas: [], total: filaAbsentismo(null, 'Total', null) }),
    });
    pintar(<Analitica />, { sesion: sesionDe('RRHH') });

    expect(await screen.findByText(new RegExp(A.sinDias))).toBeTruthy();
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(6);
  });

  /* Un GESTOR sin departamento no tiene nada que calcular: el 409 lo explica. */
  it('el 409 de un gestor sin departamento se enseña tal cual', async () => {
    simularApi({
      'GET /api/v1/analitica/resumen': () => problema(409, 'No tienes departamento: pide que te asignen uno para ver la analítica.'),
    });
    pintar(<Analitica />, { sesion: sesionDe('GESTOR') });
    expect(await screen.findByText('No tienes departamento: pide que te asignen uno para ver la analítica.')).toBeTruthy();
  });

  it('la puntualidad por grupo y el CSV del absentismo', async () => {
    const descargas: Blob[] = [];
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn((b: Blob) => {
          descargas.push(b);
          return 'blob:nx';
        }),
        revokeObjectURL: vi.fn(),
      }),
    );
    const llamadas = simularApi({
      'GET /api/v1/analitica/resumen': () => RESUMEN,
      'GET /api/v1/analitica/absentismo': () => ABSENTISMO,
      'GET /api/v1/analitica/absentismo.csv': () => new Response(new Blob(['grupo;absentismo']), { status: 200 }),
      'GET /api/v1/analitica/puntualidad': () => ({
        ventana: VENTANA,
        agrupacion: 'DEPARTAMENTO',
        total: { id: null, nombre: 'Total', entradasConHorario: 40, puntuales: 36, retrasos: 4, puntualidad: 90, retrasoMedioMinutos: 9, retrasoMedianoMinutos: 7, retrasosHasta30: 3, retrasosDeMasDe30: 1 },
        filas: [{ id: 1, nombre: 'Ingeniería', entradasConHorario: 40, puntuales: 36, retrasos: 4, puntualidad: 90, retrasoMedioMinutos: 9, retrasoMedianoMinutos: 7, retrasosHasta30: 3, retrasosDeMasDe30: 1 }],
      }),
    });
    pintar(<Analitica />, { sesion: sesionDe('RRHH') });

    await userEvent.click(await screen.findByRole('button', { name: A.csv }));
    await waitFor(() => expect(descargas).toHaveLength(1));
    expect(llamadas.a('GET', '/api/v1/analitica/absentismo.csv')).toHaveLength(1);

    await userEvent.click(screen.getByRole('tab', { name: A.puntualidad }));
    const tabla = await screen.findByRole('table', { name: A.tablaPuntualidad });
    expect(within(tabla).getAllByRole('cell', { name: '90,0 %' }).length).toBe(2);
  });
});
