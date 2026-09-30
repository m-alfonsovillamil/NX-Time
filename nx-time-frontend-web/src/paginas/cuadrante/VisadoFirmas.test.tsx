/**
 * El visado de firmas.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { cuadrante } from '../../i18n/es/cuadrante';
import { pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { hoyEnEmpresa, primeroDeMes, sumarDias } from '../../util/fechas';
import { VisadoFirmas, resumenDe } from './VisadoFirmas';

const V = cuadrante.visado;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function firma(id: number, extra: Record<string, unknown> = {}) {
  return {
    id,
    usuarioId: id,
    usuario: null,
    anio: 2026,
    mes: 8,
    estado: 'VIGENTE',
    hash: 'abc',
    jornadas: 20,
    segundosNetos: 20 * 8 * 3600,
    firmadaEn: '2026-09-01T08:00:00Z',
    invalidadaEn: null,
    motivoInvalidacion: null,
    visadaPor: null,
    visadaEn: null,
    ...extra,
  };
}

const EQUIPO = [
  { usuarioId: 2, usuario: 'Javier', estado: 'VIGENTE', firma: firma(10) },
  { usuarioId: 3, usuario: 'Ana', estado: 'VIGENTE', firma: firma(11, { visadaPor: 'Elena', visadaEn: '2026-09-02T08:00:00Z' }) },
  { usuarioId: 4, usuario: 'Carlos', estado: 'INVALIDADA', firma: firma(12, { estado: 'INVALIDADA', motivoInvalidacion: 'Se corrigió el fichaje del 14/08.' }) },
  { usuarioId: 5, usuario: 'Lucía', estado: 'SIN_FIRMAR', firma: null },
];

describe('visado de firmas', () => {
  it('el resumen cuenta firmadas y visadas', () => {
    expect(resumenDe(EQUIPO as never[])).toEqual({ firmadas: 2, visadas: 1, total: 4 });
  });

  it('empieza en el mes anterior y solo ofrece visar una firma vigente sin visar', async () => {
    const llamadas = simularApi({
      'GET /api/v1/firmas/equipo': () => EQUIPO,
      'POST /api/v1/firmas/{id}/visado': () => firma(10, { visadaPor: 'Elena' }),
    });
    pintar(<VisadoFirmas />, { sesion: sesionDe('RRHH') });

    expect(await screen.findByText(V.resumen(2, 4, 1))).toBeTruthy();
    const anterior = sumarDias(primeroDeMes(hoyEnEmpresa()), -1);
    const [pedida] = llamadas.a('GET', '/api/v1/firmas/equipo');
    expect([pedida?.query.get('anio'), pedida?.query.get('mes')]).toEqual([anterior.slice(0, 4), String(Number(anterior.slice(5, 7)))]);

    expect(within(screen.getByRole('group', { name: V.accionesDe('Ana') })).queryByRole('button', { name: V.visar })).toBeNull();
    expect(within(screen.getByRole('group', { name: V.accionesDe('Carlos') })).queryByRole('button', { name: V.visar })).toBeNull();
    expect(screen.queryByRole('group', { name: V.accionesDe('Lucía') })).toBeNull();
    expect(screen.getByText(V.motivo('Se corrigió el fichaje del 14/08.'))).toBeTruthy();

    await userEvent.click(within(screen.getByRole('group', { name: V.accionesDe('Javier') })).getByRole('button', { name: V.visar }));
    await waitFor(() => expect(llamadas.a('POST', '/api/v1/firmas/10/visado')).toHaveLength(1));
  });

  it('visar la propia: el servidor dice que no y se lee', async () => {
    simularApi({
      'GET /api/v1/firmas/equipo': () => [EQUIPO[0]],
      'POST /api/v1/firmas/{id}/visado': () => problema(403, 'No puedes visar tu propia firma.'),
    });
    pintar(<VisadoFirmas />, { sesion: sesionDe('RRHH') });

    await userEvent.click(await screen.findByRole('button', { name: V.visar }));
    expect((await screen.findByRole('alert')).textContent).toBe('No puedes visar tu propia firma.');
  });
});
