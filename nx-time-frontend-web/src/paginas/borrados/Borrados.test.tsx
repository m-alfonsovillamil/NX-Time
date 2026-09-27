/**
 * Los borrados de datos.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { borrados } from '../../i18n/es/borrados';
import { json, pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { Borrados } from './Borrados';

const B = borrados;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function solicitud(id: number, nombre: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    usuarioId: id + 100,
    nombre,
    email: `${nombre.toLowerCase()}@techcorp.demo`,
    estado: 'PENDIENTE',
    motivo: null,
    registradaPor: null,
    creadaEn: '2026-09-20T10:00:00Z',
    resueltaPor: null,
    resueltaEn: null,
    comentarioResolucion: null,
    anonimizarDesde: null,
    bloqueos: [],
    ...extra,
  };
}

describe('borrados de datos', () => {
  /* Irreversible: el botón solo funciona tras escribir el nombre. */
  it('ejecutar pide escribir el nombre de la persona', async () => {
    const llamadas = simularApi({
      'GET /api/v1/borrados/pendientes': () => [solicitud(4, 'Lucía')],
      'POST /api/v1/borrados/{id}/ejecutar': () => solicitud(4, 'Lucía', { estado: 'EJECUTADA' }),
    });
    pintar(<Borrados />, { sesion: sesionDe('RRHH') });

    await userEvent.click(await screen.findByRole('button', { name: B.ejecutar }));
    const dialogo = await screen.findByRole('dialog', { name: B.ejecutarTitulo('Lucía') });
    const borrar = within(dialogo).getByRole('button', { name: B.ejecutarConfirmar });
    expect((borrar as HTMLButtonElement).disabled).toBe(true);
    await userEvent.type(within(dialogo).getByLabelText(B.confirmarNombre('Lucía')), 'lucía ');
    expect((borrar as HTMLButtonElement).disabled).toBe(false);
    await userEvent.click(borrar);

    await waitFor(() => expect(llamadas.a('POST', '/api/v1/borrados/4/ejecutar')).toHaveLength(1));
  });

  it('con bloqueos no se ofrece ejecutar y se dice qué falta', async () => {
    simularApi({
      'GET /api/v1/borrados/pendientes': () => [solicitud(5, 'Carlos', { bloqueos: ['Tiene una jornada abierta.', 'Tiene 2 ausencias pendientes.'] })],
    });
    pintar(<Borrados />, { sesion: sesionDe('RRHH') });

    expect(await screen.findByText('Tiene una jornada abierta.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: B.ejecutar })).toBeNull();
    expect(screen.getByRole('button', { name: B.rechazar })).toBeTruthy();
  });

  it('rechazar exige comentario', async () => {
    const llamadas = simularApi({
      'GET /api/v1/borrados/pendientes': () => [solicitud(4, 'Lucía')],
      'POST /api/v1/borrados/{id}/rechazar': () => solicitud(4, 'Lucía', { estado: 'RECHAZADA' }),
    });
    pintar(<Borrados />, { sesion: sesionDe('RRHH') });

    await userEvent.click(await screen.findByRole('button', { name: B.rechazar }));
    const dialogo = await screen.findByRole('dialog', { name: B.rechazarTitulo('Lucía') });
    await userEvent.click(within(dialogo).getByRole('button', { name: B.rechazar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(B.comentarioVacio);
    await userEvent.type(within(dialogo).getByLabelText(B.comentario), 'Tiene una denuncia abierta que hay que cerrar antes.');
    await userEvent.click(within(dialogo).getByRole('button', { name: B.rechazar }));
    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/borrados/4/rechazar').map((l) => l.cuerpo)).toEqual([
        { comentario: 'Tiene una denuncia abierta que hay que cerrar antes.' },
      ]),
    );
  });

  it('registrar una solicitud de fuera: persona y cómo llegó; el 409 del servidor se lee', async () => {
    let veces = 0;
    const llamadas = simularApi({
      'GET /api/v1/borrados/pendientes': () => [],
      'GET /api/v1/borrados/candidatos': () => [
        { id: 7, nombre: 'Pedro', email: 'p@x', activo: false },
        { id: 3, nombre: 'Ana', email: 'a@x', activo: true },
      ],
      'POST /api/v1/borrados': () => (++veces === 1 ? problema(409, 'Pedro ya tiene una solicitud pendiente.') : json(solicitud(9, 'Pedro'), 201)),
    });
    pintar(<Borrados />, { sesion: sesionDe('ADMIN') });

    expect(await screen.findByText(B.vacioTitulo)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: B.registrar }));
    const dialogo = await screen.findByRole('dialog', { name: B.registrarTitulo });
    expect(await within(dialogo).findByRole('option', { name: B.deBaja('Pedro') })).toBeTruthy();
    await userEvent.click(within(dialogo).getByRole('button', { name: B.registrarConfirmar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(B.faltan);

    await userEvent.selectOptions(within(dialogo).getByLabelText(B.persona), '7');
    await userEvent.type(within(dialogo).getByLabelText(B.comoLlego), 'Carta del 12/09');
    await userEvent.click(within(dialogo).getByRole('button', { name: B.registrarConfirmar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe('Pedro ya tiene una solicitud pendiente.');
    await userEvent.click(within(dialogo).getByRole('button', { name: B.registrarConfirmar }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(llamadas.a('POST', '/api/v1/borrados').map((l) => l.cuerpo)).toEqual([
      { usuarioId: 7, motivo: 'Carta del 12/09' },
      { usuarioId: 7, motivo: 'Carta del 12/09' },
    ]);
  });
});
