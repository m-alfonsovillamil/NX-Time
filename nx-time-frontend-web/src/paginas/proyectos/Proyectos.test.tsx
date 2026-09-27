/**
 * Proyectos y calendario laboral.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { proyectos } from '../../i18n/es/proyectos';
import { json, pintar, problema, sesionDe, sinContenido, simularApi } from '../../pruebas/api';
import { hoyEnEspana } from '../../util/fechas';
import { CalendarioLaboral } from './CalendarioLaboral';
import { Proyectos } from './Proyectos';

const P = proyectos;
const D = proyectos.detalle;
const C = proyectos.calendario;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const PROYECTO = {
  id: 3,
  codigo: 'NX-CORE',
  nombre: 'Núcleo',
  descripcion: null,
  fechaInicio: '2026-01-01',
  fechaFin: null,
  activo: true,
  asignados: 2,
};

const DETALLE = {
  proyecto: PROYECTO,
  asignaciones: [
    { id: 30, usuarioId: 2, usuario: 'Javier', proyectoId: 3, fechaInicio: '2026-01-01', fechaFin: null, vigente: true },
    { id: 31, usuarioId: 3, usuario: 'Ana', proyectoId: 3, fechaInicio: '2026-01-01', fechaFin: '2026-06-30', vigente: false },
  ],
  anio: 2026,
  mes: 9,
  horas: [{ usuarioId: 2, nombre: 'Javier', minutos: 7200 }],
};

describe('proyectos', () => {
  it('lista, reparto del mes y crear uno nuevo', async () => {
    const llamadas = simularApi({
      'GET /api/v1/proyectos': () => [PROYECTO],
      'GET /api/v1/proyectos/horas': () => ({ anio: 2026, mes: 9, proyectos: [{ proyectoId: 3, codigo: 'NX-CORE', nombre: 'Núcleo', minutos: 9000 }] }),
      'POST /api/v1/proyectos': () => json({ ...PROYECTO, id: 4 }, 201),
    });
    pintar(<Proyectos />, { sesion: sesionDe('GESTOR') });

    expect(await screen.findByText('NX-CORE · Núcleo')).toBeTruthy();
    expect(screen.getByText('150h 00m')).toBeTruthy();
    expect(screen.getByRole('cell', { name: P.personas(2) })).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: P.nuevo }));
    const dialogo = await screen.findByRole('dialog', { name: P.nuevo });
    await userEvent.type(within(dialogo).getByLabelText(P.codigo), 'NX-WEB');
    await userEvent.type(within(dialogo).getByLabelText(P.nombre), 'Web');
    await userEvent.click(within(dialogo).getByRole('button', { name: P.crear }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/proyectos').map((l) => l.cuerpo)).toEqual([{ codigo: 'NX-WEB', nombre: 'Web', fechaInicio: hoyEnEspana() }]),
    );
  });

  it('el detalle enseña quién ha estado y sus horas; sacar pone fin hoy', async () => {
    const llamadas = simularApi({
      'GET /api/v1/proyectos': () => [PROYECTO],
      'GET /api/v1/proyectos/horas': () => ({ proyectos: [] }),
      'GET /api/v1/proyectos/{id}': () => DETALLE,
      'PATCH /api/v1/proyectos/asignaciones/{asignacionId}': () => ({ ...DETALLE.asignaciones[0], vigente: false }),
    });
    pintar(<Proyectos />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByRole('button', { name: `${P.ver} NX-CORE` }));
    const quien = await screen.findByRole('list', { name: D.quienHaEstado });
    const [javier, ana] = within(quien).getAllByRole('listitem');
    // Con asignaciones no se ofrece borrar: se cierra.
    expect(screen.queryByRole('button', { name: D.borrar })).toBeNull();
    expect(within(ana as HTMLElement).queryByRole('button', { name: D.sacar })).toBeNull();
    expect(screen.getByText('120h 00m')).toBeTruthy();

    await userEvent.click(within(javier as HTMLElement).getByRole('button', { name: D.sacar }));
    const dialogo = await screen.findByRole('dialog', { name: D.sacarTitulo('Javier') });
    await userEvent.click(within(dialogo).getByRole('button', { name: D.sacar }));
    await waitFor(() =>
      expect(llamadas.a('PATCH', '/api/v1/proyectos/asignaciones/30').map((l) => l.cuerpo)).toEqual([{ fechaFin: hoyEnEspana() }]),
    );
  });

  /* En varios proyectos sí (ADR 017); dos veces en el mismo con fechas que se pisan, no. */
  it('asignar a alguien que ya está en el proyecto esas fechas enseña el motivo del servidor', async () => {
    simularApi({
      'GET /api/v1/proyectos': () => [PROYECTO],
      'GET /api/v1/proyectos/horas': () => ({ proyectos: [] }),
      'GET /api/v1/proyectos/{id}': () => DETALLE,
      'GET /api/v1/gestor/mis-empleados': () => [
        { id: 5, nombre: 'Lucía', email: 'l@x', activo: true, horasSemanales: 40, diasVacaciones: 22, departamentoId: null, departamentoNombre: null, rol: 'EMPLEADO' },
      ],
      'POST /api/v1/proyectos/{id}/asignaciones': () => problema(409, 'Lucía ya tiene una asignación en este proyecto en alguna de esas fechas.'),
    });
    pintar(<Proyectos />, { sesion: sesionDe('GESTOR'), ruta: '/proyectos?proyecto=3' });

    await userEvent.click(await screen.findByRole('button', { name: D.asignar }));
    const dialogo = await screen.findByRole('dialog', { name: D.asignar });
    await waitFor(() => expect(within(dialogo).getByRole('option', { name: 'Lucía' })).toBeTruthy());
    await userEvent.selectOptions(within(dialogo).getByLabelText(D.persona), '5');
    await userEvent.click(within(dialogo).getByRole('button', { name: D.asignarConfirmar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe('Lucía ya tiene una asignación en este proyecto en alguna de esas fechas.');
  });
});

describe('calendario laboral', () => {
  const MES = {
    anio: 2026,
    mes: 10,
    incluyeEquipo: false,
    festivos: [
      { id: null, fecha: '2026-10-12', descripcion: 'Fiesta Nacional de España', ambito: 'NACIONAL', editable: false },
      { id: 8, fecha: '2026-10-09', descripcion: 'Día de la Comunitat Valenciana', ambito: 'AUTONOMICO', editable: true },
    ],
    ausencias: [],
  };

  it('los nacionales no se tocan; los demás se cambian o se quitan', async () => {
    const llamadas = simularApi({
      'GET /api/v1/calendario': () => MES,
      'DELETE /api/v1/calendario/festivos/{id}': () => sinContenido(),
    });
    pintar(<CalendarioLaboral />, { sesion: sesionDe('GESTOR') });

    const lista = await screen.findByRole('list');
    const [autonomico, nacional] = within(lista).getAllByRole('listitem');
    expect(within(nacional as HTMLElement).getByText(C.noEditable)).toBeTruthy();
    expect(within(nacional as HTMLElement).queryByRole('button')).toBeNull();

    await userEvent.click(within(autonomico as HTMLElement).getByRole('button', { name: C.quitar }));
    const dialogo = await screen.findByRole('dialog', { name: C.quitarTitulo('Día de la Comunitat Valenciana') });
    await userEvent.click(within(dialogo).getByRole('button', { name: C.quitar }));
    await waitFor(() => expect(llamadas.a('DELETE', '/api/v1/calendario/festivos/8')).toHaveLength(1));
  });

  it('añadir uno no ofrece el ámbito nacional', async () => {
    const llamadas = simularApi({
      'GET /api/v1/calendario': () => ({ ...MES, festivos: [] }),
      'POST /api/v1/calendario/festivos': () => json({ id: 9 }, 201),
    });
    pintar(<CalendarioLaboral />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByRole('button', { name: C.anadir }));
    const dialogo = await screen.findByRole('dialog', { name: C.anadirTitulo });
    expect(within(dialogo).queryByRole('option', { name: C.ambitos.NACIONAL as string })).toBeNull();
    const fecha = within(dialogo).getByLabelText(C.fecha);
    await userEvent.clear(fecha);
    await userEvent.type(fecha, '2026-10-24');
    await userEvent.selectOptions(within(dialogo).getByLabelText(C.ambito), 'LOCAL');
    await userEvent.type(within(dialogo).getByLabelText(C.descripcion), 'Fiesta local');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.guardar }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/calendario/festivos').map((l) => l.cuerpo)).toEqual([
        { fecha: '2026-10-24', descripcion: 'Fiesta local', ambito: 'LOCAL' },
      ]),
    );
  });
});
