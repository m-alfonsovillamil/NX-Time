/**
 * El historial del equipo, corregir el fichaje de otra persona y la traza de auditoría.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { equipo } from '../../i18n/es/equipo';
import { historial } from '../../i18n/es/historial';
import { json, pintar, sesionDe, sinContenido, simularApi } from '../../pruebas/api';
import { cambiosDeHora, leerInstantanea } from './DialogoAuditoria';
import { HistorialEquipo } from './HistorialEquipo';

const E = equipo;
const A = equipo.auditoria;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const EMPLEADOS = [
  { id: 2, nombre: 'Javier', email: 'j@x', activo: true, horasSemanales: 40, diasVacaciones: 22, departamentoId: null, departamentoNombre: null },
  { id: 3, nombre: 'Ana', email: 'a@x', activo: false, horasSemanales: 40, diasVacaciones: 22, departamentoId: null, departamentoNombre: null },
];

function fila(id: number, usuarioId: number, nombre: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    horaEntrada: '2026-09-21T07:00:00Z',
    horaSalida: '2026-09-21T15:30:00Z',
    fecha: '2026-09-21',
    usuario: { nombre },
    usuarioId,
    minutosPausaAcumulados: 30,
    segundosPausaAcumulados: 1800,
    ...extra,
  };
}

describe('historial del equipo', () => {
  it('filtrar por persona lo pide al servidor', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/fichaje/gestor/historial': ({ query }) => ({
        contenido: query.get('usuarioId') === '3' ? [] : [fila(10, 2, 'Javier'), fila(11, 3, 'Ana')],
        hayMas: false,
      }),
    });
    pintar(<HistorialEquipo />, { sesion: sesionDe('GESTOR'), ruta: '/equipo' });

    expect(await screen.findByRole('cell', { name: 'Javier' })).toBeTruthy();
    // Neto: 8 h 30 m de jornada menos 30 m de pausa.
    expect(screen.getAllByRole('cell', { name: '8h 00m' })).toHaveLength(2);
    // Quien está de baja sigue saliendo: su historial cuenta.
    expect(screen.getByRole('option', { name: E.deBaja('Ana') })).toBeTruthy();

    await userEvent.selectOptions(screen.getByLabelText(E.filtro), '3');
    expect(await screen.findByText(E.vacioFiltroTitulo('Ana'))).toBeTruthy();
    expect(llamadas.a('GET', '/api/v1/fichaje/gestor/historial').map((l) => l.query.get('usuarioId'))).toEqual([null, '3']);
  });

  it('un GESTOR ve las jornadas, pero ni corregir ni auditoría', async () => {
    simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/fichaje/gestor/historial': () => ({ contenido: [fila(10, 2, 'Javier')], hayMas: false }),
    });
    pintar(<HistorialEquipo />, { sesion: sesionDe('GESTOR') });

    expect(await screen.findByRole('cell', { name: 'Javier' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: E.corregir })).toBeNull();
    expect(screen.queryByRole('columnheader', { name: E.acciones })).toBeNull();
  });

  it('la jornada que cerró el sistema lleva su distintivo', async () => {
    simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/fichaje/gestor/historial': () => ({
        contenido: [fila(10, 2, 'Javier', { cerradaPorElSistema: true }), fila(11, 3, 'Ana')],
        hayMas: false,
      }),
    });
    pintar(<HistorialEquipo />, { sesion: sesionDe('GESTOR') });

    await screen.findByRole('cell', { name: 'Javier' });
    expect(screen.getAllByText(historial.cerradaPorElSistema)).toHaveLength(1);
  });

  /* ADR 015: la corrección del fichaje de otra persona la aprueba ella. */
  it('RRHH propone corregir el fichaje de otra persona, y se le explica quién lo aprueba', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/fichaje/gestor/historial': () => ({
        contenido: [fila(10, 2, 'Javier'), fila(12, 2, 'Javier', { horaSalida: null })],
        hayMas: false,
      }),
      'POST /api/v1/fichaje/{id}/correcciones': () => json({ id: 1, estado: 'PENDIENTE' }, 202),
    });
    pintar(<HistorialEquipo />, { sesion: sesionDe('RRHH') });

    // Solo la cerrada se puede corregir: la abierta se cierra fichando.
    await screen.findAllByRole('cell', { name: 'Javier' });
    expect(screen.getAllByRole('button', { name: E.corregir })).toHaveLength(1);

    await userEvent.click(screen.getByRole('button', { name: E.corregir }));
    const dialogo = await screen.findByRole('dialog', { name: historial.correccion.tituloDeOtro('Javier') });
    expect(within(dialogo).getByText(historial.correccion.explicacionDeOtro('Javier'))).toBeTruthy();
    await userEvent.type(within(dialogo).getByLabelText(historial.correccion.motivo), 'Se olvidó fichar la salida');
    await userEvent.click(within(dialogo).getByRole('button', { name: historial.correccion.enviar }));

    await waitFor(() => expect(llamadas.a('POST', '/api/v1/fichaje/10/correcciones')).toHaveLength(1));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });

  it('la auditoría enseña si la traza es de fiar y qué cambió en cada paso', async () => {
    simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/fichaje/gestor/historial': () => ({ contenido: [fila(10, 2, 'Javier')], hayMas: false }),
      'GET /api/v1/auditoria/integridad/ultima': () => ({
        verificadoEn: '2026-09-27T01:00:00Z',
        movimientos: 900,
        comprobados: 900,
        soloEnlace: 0,
        pendientes: 3,
      }),
      'GET /api/v1/auditoria/fichaje/{id}': () => [
        {
          id: 1,
          registroId: 10,
          accion: 'CREACION',
          valorAnterior: null,
          valorNuevo: '{"horaEntrada":"2026-09-21T07:00:00Z","horaSalida":null}',
          motivo: null,
          fechaHora: '2026-09-21T07:00:00Z',
          modificadoPor: { nombre: 'Javier' },
          ip: '10.0.0.8',
        },
        {
          id: 2,
          registroId: 10,
          accion: 'CORRECCION',
          valorAnterior: '{"horaEntrada":"2026-09-21T07:00:00Z","horaSalida":"2026-09-21T15:30:00Z"}',
          valorNuevo: '{"horaEntrada":"2026-09-21T06:30:00Z","horaSalida":"2026-09-21T15:30:00Z"}',
          motivo: 'Llegó antes y no fichó',
          fechaHora: '2026-09-22T09:00:00Z',
          modificadoPor: { nombre: 'Elena' },
          ip: null,
        },
      ],
    });
    pintar(<HistorialEquipo />, { sesion: sesionDe('RRHH') });

    await userEvent.click(await screen.findByRole('button', { name: E.verAuditoria }));
    const dialogo = await screen.findByRole('dialog', { name: A.titulo });
    expect(await within(dialogo).findByText(A.cadenaPendientes(3))).toBeTruthy();
    expect(await within(dialogo).findByText(A.entradaCambio('09:00 h', '08:30 h'))).toBeTruthy();
    expect(within(dialogo).getByText(A.acciones.CORRECCION as string)).toBeTruthy();
    expect(within(dialogo).getByText(A.por('Elena'))).toBeTruthy();
    expect(within(dialogo).getByText(A.desdeIp('10.0.0.8'))).toBeTruthy();
  });

  it('sin ninguna comprobación todavía, lo dice en vez de dar la traza por buena', async () => {
    simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/fichaje/gestor/historial': () => ({ contenido: [fila(10, 2, 'Javier')], hayMas: false }),
      'GET /api/v1/auditoria/integridad/ultima': () => sinContenido(),
      'GET /api/v1/auditoria/fichaje/{id}': () => [],
    });
    pintar(<HistorialEquipo />, { sesion: sesionDe('ADMIN') });

    await userEvent.click(await screen.findByRole('button', { name: E.verAuditoria }));
    expect(await screen.findByText(A.cadenaSinComprobar)).toBeTruthy();
    expect(await screen.findByText(A.vacioTitulo)).toBeTruthy();
  });
});

describe('los cambios de hora de un paso', () => {
  it('solo lo que cambia, y sin flecha cuando se fija por primera vez', () => {
    expect(
      cambiosDeHora({
        valorAnterior: '{"horaEntrada":"2026-09-21T07:00:00Z","horaSalida":null}',
        valorNuevo: '{"horaEntrada":"2026-09-21T07:00:00Z","horaSalida":"2026-09-21T16:00:00Z"}',
      }),
    ).toEqual([A.salidaFijada('18:00 h')]);
  });

  it('una foto mal formada no rompe nada', () => {
    expect(leerInstantanea('no es json')).toBeNull();
    expect(cambiosDeHora({ valorAnterior: '{', valorNuevo: 'tampoco' })).toEqual([]);
  });
});
