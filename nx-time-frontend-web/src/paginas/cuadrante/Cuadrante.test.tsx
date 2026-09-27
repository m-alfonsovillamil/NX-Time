/**
 * Mi cuadrante, las incidencias y la firma mensual.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { cuadrante } from '../../i18n/es/cuadrante';
import { json, pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { Firmas } from './Firmas';
import { Incidencias, detalleDe } from './Incidencias';
import { MiCuadrante, textoDelTramo } from './MiCuadrante';

const C = cuadrante.cuadrante;
const I = cuadrante.incidencias;
const F = cuadrante.firmas;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** Un día del cuadrante tal y como lo manda el backend. */
function dia(fecha: string, extra: Record<string, unknown> = {}) {
  return { fecha, origen: 'SIN_CUADRANTE', minutos: 0, entrada: null, tramos: [], motivo: null, plantilla: null, ...extra };
}

function tramo(horaInicio: string, horaFin: string, minutos: number, cruzaMedianoche = false) {
  return { diaSemana: 1, inicio: 0, fin: 0, horaInicio, horaFin, minutos, cruzaMedianoche };
}

describe('mi cuadrante', () => {
  it('un turno de noche dice que acaba al día siguiente', () => {
    expect(textoDelTramo(tramo('22:00', '06:00', 480, true))).toBe('22:00–06:00 (+1 d)');
    expect(textoDelTramo(tramo('09:00', '14:00', 300))).toBe('09:00–14:00');
  });

  it('pinta la semana con su origen: cuadrante, excepción, no laborable y libre', async () => {
    const llamadas = simularApi({
      'GET /api/v1/cuadrantes/mio': ({ query }) => {
        const desde = query.get('desde') ?? '';
        return [
          dia(desde, { origen: 'CUADRANTE', minutos: 480, tramos: [tramo('09:00', '14:00', 300), tramo('16:00', '19:00', 180)], plantilla: 'Partido' }),
          dia('x2', { origen: 'EXCEPCION', minutos: 240, tramos: [tramo('10:00', '14:00', 240)], motivo: 'Médico' }),
          dia('x3', { origen: 'NO_LABORABLE', motivo: 'Fiesta Nacional de España' }),
          dia('x4', { origen: 'CUADRANTE', tramos: [] }),
        ];
      },
    });
    pintar(<MiCuadrante />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText('09:00–14:00 · 16:00–19:00')).toBeTruthy();
    expect(screen.getByText(`${C.excepcion}: Médico`)).toBeTruthy();
    expect(screen.getByText('Fiesta Nacional de España')).toBeTruthy();
    expect(screen.getByText(C.libre)).toBeTruthy();
    expect(screen.getByText(C.total('12h 00m'))).toBeTruthy();
    expect(screen.getByText(C.plantilla('Partido'))).toBeTruthy();
    // Siete días, de lunes a domingo.
    const [pedida] = llamadas.a('GET', '/api/v1/cuadrantes/mio');
    const desde = new Date(`${pedida?.query.get('desde')}T00:00:00Z`);
    expect(desde.getUTCDay()).toBe(1);
  });

  it('sin cuadrante, lo dice en vez de inventar un horario', async () => {
    simularApi({ 'GET /api/v1/cuadrantes/mio': () => [dia('a'), dia('b')] });
    pintar(<MiCuadrante />, { sesion: sesionDe('EMPLEADO') });
    expect(await screen.findByText(C.vacio)).toBeTruthy();
  });
});

const INCIDENCIA = {
  id: 7,
  usuarioId: 1,
  usuario: 'Javier',
  fecha: '2026-09-21',
  tipo: 'RETRASO',
  minutos: 25,
  horaPrevista: '09:00',
  horaReal: '2026-09-21T07:25:00Z',
  estado: 'PENDIENTE',
  justificacion: null,
  justificadaEn: null,
  comentarioResolucion: null,
  resueltaPor: null,
  resueltaEn: null,
};

describe('incidencias', () => {
  it('el detalle compara la hora real con la prevista, en hora de España', () => {
    expect(detalleDe({ tipo: 'RETRASO', minutos: 25, horaPrevista: '09:00', horaReal: '2026-09-21T07:25:00Z' })).toBe(
      I.detalleRetraso('09:25', '09:00', '25m'),
    );
  });

  it('explicar una propia manda el texto', async () => {
    const llamadas = simularApi({
      'GET /api/v1/incidencias/mias': () => [INCIDENCIA],
      'POST /api/v1/incidencias/{id}/justificacion': () => ({ ...INCIDENCIA, estado: 'JUSTIFICADA', justificacion: 'Atasco' }),
    });
    pintar(<Incidencias />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: I.justificar }));
    const dialogo = await screen.findByRole('dialog', { name: I.justificarTitulo });
    await userEvent.type(within(dialogo).getByLabelText(I.texto), 'Atasco en la A-6');
    await userEvent.click(within(dialogo).getByRole('button', { name: I.enviar }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/incidencias/7/justificacion').map((l) => l.cuerpo)).toEqual([
        { texto: 'Atasco en la A-6' },
      ]),
    );
  });

  it('una decidida ya no se puede explicar', async () => {
    simularApi({ 'GET /api/v1/incidencias/mias': () => [{ ...INCIDENCIA, estado: 'ACEPTADA', resueltaPor: 'Marta' }] });
    pintar(<Incidencias />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(I.resueltaPor('Marta'))).toBeTruthy();
    expect(screen.queryByRole('button', { name: I.justificar })).toBeNull();
  });

  it('un EMPLEADO no ve la pestaña del equipo', async () => {
    simularApi({ 'GET /api/v1/incidencias/mias': () => [] });
    pintar(<Incidencias />, { sesion: sesionDe('EMPLEADO') });
    expect(await screen.findByText(I.vacio)).toBeTruthy();
    expect(screen.queryByRole('tab', { name: I.delEquipo })).toBeNull();
  });

  /* Rechazar pide motivo: es lo que verá la persona. */
  it('en el equipo, rechazar exige motivo y aceptar no', async () => {
    const llamadas = simularApi({
      'GET /api/v1/incidencias/mias': () => [],
      'GET /api/v1/incidencias/equipo': () => ({ contenido: [INCIDENCIA, { ...INCIDENCIA, id: 8 }], hayMas: false }),
      'POST /api/v1/incidencias/{id}/resolucion': () => ({ ...INCIDENCIA, estado: 'ACEPTADA' }),
    });
    pintar(<Incidencias />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByRole('tab', { name: I.delEquipo }));
    const [primera, segunda] = await screen.findAllByRole('listitem');
    await userEvent.click(within(primera as HTMLElement).getByRole('button', { name: I.aceptar }));

    await userEvent.click(within(segunda as HTMLElement).getByRole('button', { name: I.rechazar }));
    const dialogo = await screen.findByRole('dialog', { name: I.rechazarTitulo });
    await userEvent.click(within(dialogo).getByRole('button', { name: I.rechazar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(I.comentarioVacio);
    await userEvent.type(within(dialogo).getByLabelText(I.comentario), 'Sin justificante');
    await userEvent.click(within(dialogo).getByRole('button', { name: I.rechazar }));

    await waitFor(() =>
      expect(llamadas.llamadas.filter((l) => l.metodo === 'POST').map((l) => [l.ruta, l.cuerpo])).toEqual([
        ['/api/v1/incidencias/7/resolucion', { aceptar: true }],
        ['/api/v1/incidencias/8/resolucion', { aceptar: false, comentario: 'Sin justificante' }],
      ]),
    );
  });
});

describe('la firma mensual', () => {
  const AGOSTO = { anio: 2026, mes: 8, jornadas: 20, segundosNetos: 20 * 8 * 3600, puedeFirmar: true, bloqueo: null, firma: null };
  const JULIO_FIRMADO = {
    anio: 2026,
    mes: 7,
    jornadas: 22,
    segundosNetos: 22 * 8 * 3600,
    puedeFirmar: false,
    bloqueo: null,
    firma: { id: 3, anio: 2026, mes: 7, estado: 'VIGENTE', hash: 'abcdef0123456789', firmadaEn: '2026-08-01T08:00:00Z', visadaPor: 'Elena' },
  };

  it('firmar un mes pide confirmación con lo que se firma, y lo manda', async () => {
    const llamadas = simularApi({
      'GET /api/v1/firmas/mias': () => [AGOSTO, JULIO_FIRMADO],
      'POST /api/v1/firmas': () => json({ id: 4, estado: 'VIGENTE' }, 201),
    });
    pintar(<Firmas />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: F.firmar }));
    const dialogo = await screen.findByRole('dialog', { name: F.confirmarTitulo('Agosto de 2026') });
    expect(within(dialogo).getByText(F.confirmarTexto('agosto de 2026', 20, '160h 00m'))).toBeTruthy();
    await userEvent.click(within(dialogo).getByRole('button', { name: F.firmar }));

    await waitFor(() => expect(llamadas.a('POST', '/api/v1/firmas').map((l) => l.cuerpo)).toEqual([{ anio: 2026, mes: 8 }]));
  });

  it('un mes firmado enseña la huella y quién lo visó, y se puede comprobar', async () => {
    simularApi({
      'GET /api/v1/firmas/mias': () => [JULIO_FIRMADO],
      'GET /api/v1/firmas/{id}/verificacion': () => ({ firmaId: 3, estado: 'VIGENTE', coincide: true }),
    });
    pintar(<Firmas />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(F.visado('Elena'))).toBeTruthy();
    expect(screen.getByText(/huella abcdef012345…/)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: F.comprobar }));
    expect(await screen.findByText(F.coincide)).toBeTruthy();
  });

  /* 422: jornadas abiertas o cerradas por el sistema. El mensaje del servidor dice cuáles. */
  it('si el servidor no deja firmar, se lee su motivo en el diálogo', async () => {
    simularApi({
      'GET /api/v1/firmas/mias': () => [AGOSTO],
      'POST /api/v1/firmas': () => problema(422, 'Tienes una jornada cerrada por el sistema el 12/08.'),
    });
    pintar(<Firmas />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: F.firmar }));
    const dialogo = await screen.findByRole('dialog');
    await userEvent.click(within(dialogo).getByRole('button', { name: F.firmar }));

    expect((await within(dialogo).findByRole('alert')).textContent).toBe('Tienes una jornada cerrada por el sistema el 12/08.');
  });

  it('una firma invalidada dice por qué y vuelve a pedir firma', async () => {
    simularApi({
      'GET /api/v1/firmas/mias': () => [
        { ...AGOSTO, firma: { id: 5, estado: 'INVALIDADA', motivoInvalidacion: 'Se corrigió el fichaje del 14/08.' } },
      ],
    });
    pintar(<Firmas />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(F.invalidadaPorque('Se corrigió el fichaje del 14/08.'))).toBeTruthy();
    expect(screen.getByText(F.invalidada)).toBeTruthy();
    expect(screen.getByRole('button', { name: F.firmar })).toBeTruthy();
  });
});
