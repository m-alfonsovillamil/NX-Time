/**
 * Mi jornada, ya sobre TanStack Query: el estado lo dice el servidor, también después de fichar.
 */

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../api/cliente';
import { cerrarSesion } from '../api/sesion';
import { T } from '../i18n/es';
import { pintar, problema, sesionDe, simularApi, sinContenido } from '../pruebas/api';
import { Fichar } from './Fichar';

const ABIERTA = { id: 7, horaEntrada: new Date(Date.now() - 3_600_000).toISOString(), enPausa: false };

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Mi jornada', () => {
  it('sin jornada abierta (204), ofrece fichar la entrada', async () => {
    simularApi({
      'GET /api/v1/fichaje/activo': () => sinContenido(),
      'GET /api/v1/fichaje/hoy': () => ({ laborable: true }),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByRole('button', { name: T.fichar.entrar })).toBeTruthy();
    expect(screen.getByText(T.fichar.parado)).toBeTruthy();
  });

  /*
   * Lo que se quiere comprobar: tras fichar, la pantalla enseña lo que dice
   * el servidor al volver a preguntar, no lo que devolvió el POST.
   */
  it('fichar la entrada manda INICIO y vuelve a preguntar por la jornada', async () => {
    let abierta = false;
    const api = simularApi({
      'GET /api/v1/fichaje/activo': () => (abierta ? ABIERTA : sinContenido()),
      'GET /api/v1/fichaje/hoy': () => ({ laborable: true }),
      'POST /api/v1/fichaje': () => {
        abierta = true;
        return ABIERTA;
      },
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: T.fichar.entrar }));

    expect(await screen.findByText(T.fichar.trabajando)).toBeTruthy();
    expect(api.a('POST', '/api/v1/fichaje').map((l) => l.cuerpo)).toEqual([{ tipo: 'INICIO' }]);
    expect(api.a('GET', '/api/v1/fichaje/activo').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole('button', { name: T.fichar.salir })).toBeTruthy();
  });

  it('un rechazo del servidor se lee con su mensaje, al lado de los botones', async () => {
    simularApi({
      'GET /api/v1/fichaje/activo': () => sinContenido(),
      'GET /api/v1/fichaje/hoy': () => ({ laborable: true }),
      'POST /api/v1/fichaje': () => problema(409, 'Ya hay una jornada activa.'),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: T.fichar.entrar }));

    expect((await screen.findByRole('alert')).textContent).toBe('Ya hay una jornada activa.');
  });

  it('un día no laborable se avisa, pero se puede fichar igual', async () => {
    simularApi({
      'GET /api/v1/fichaje/activo': () => sinContenido(),
      'GET /api/v1/fichaje/hoy': () => ({ laborable: false, motivo: 'Festivo nacional' }),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    expect((await screen.findByRole('alert')).textContent).toBe(T.fichar.noLaborable('Festivo nacional'));
    expect(screen.getByRole('button', { name: T.fichar.entrar })).toBeTruthy();
  });

  it('si no se puede cargar la jornada, se ofrece reintentar y reintentar funciona', async () => {
    let falla = true;
    simularApi({
      'GET /api/v1/fichaje/activo': () => (falla ? problema(500, 'Algo ha ido mal.') : sinContenido()),
      'GET /api/v1/fichaje/hoy': () => ({ laborable: true }),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await screen.findByText('Algo ha ido mal.');
    falla = false;
    await userEvent.click(screen.getByRole('button', { name: T.app.reintentar }));

    await waitFor(() => expect(screen.getByRole('button', { name: T.fichar.entrar })).toBeTruthy());
  });
});
