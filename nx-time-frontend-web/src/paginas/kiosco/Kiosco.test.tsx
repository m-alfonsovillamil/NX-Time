/**
 * La tablet del kiosco (ADR 033): emparejarse, fichar con el nombre y el PIN,
 * y volver a emparejarse si el servidor dice que su token ya no vale.
 *
 * En jsdom no hay cámara, así que la espera ofrece directamente buscar el
 * nombre: es lo mismo que ve una tablet que no dio permiso.
 */

import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { kiosco } from '../../i18n/es/kiosco';
import { pintar, problema, simularApi } from '../../pruebas/api';
import { fijarZona } from '../../util/fechas';
import { Kiosco } from './Kiosco';

const T = kiosco.tablet;

const YO = { nombre: 'Entrada almacén', empresa: 'Almacenes Ana', zonaHoraria: 'Atlantic/Canary' };
const LUCIA = { id: 7, nombre: 'Lucía', apellidos: 'Pérez' };

beforeEach(() => {
  reiniciarEstadoDeRed();
  localStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
  fijarZona(null);
  localStorage.clear();
});

describe('emparejar', () => {
  it('enseña el código, pregunta hasta que lo confirman y se queda con el token', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let confirmado = false;
    const llamadas = simularApi({
      'POST /kiosco/emparejar': () => ({ codigo: 'ABCD2345', secreto: 's3cr3t0', caducaEn: '2026-09-30T10:10:00Z' }),
      'POST /kiosco/emparejar/estado': () =>
        confirmado ? { estado: 'LISTO', token: 'token-del-kiosco', kiosco: YO } : { estado: 'PENDIENTE' },
      'GET /kiosco/yo': () => YO,
      'GET /kiosco/plantilla': () => [LUCIA],
    });
    pintar(<Kiosco />);

    expect(await screen.findByText('ABCD 2345')).toBeTruthy();
    await act(() => vi.advanceTimersByTimeAsync(4_000));
    expect(llamadas.a('POST', '/kiosco/emparejar/estado').map((l) => l.cuerpo)).toEqual([{ secreto: 's3cr3t0' }]);

    confirmado = true;
    await act(() => vi.advanceTimersByTimeAsync(4_000));

    expect(await screen.findByText('Entrada almacén')).toBeTruthy();
    expect(localStorage.getItem('nx-kiosco-token')).toBe('token-del-kiosco');
    // A partir de aquí, con su token y su esquema.
    await waitFor(() => expect(llamadas.a('GET', '/kiosco/yo')).toHaveLength(1));
    expect(llamadas.a('GET', '/kiosco/yo')[0]?.cabeceras.get('Authorization')).toBe('Kiosco token-del-kiosco');
  });

  it('si el código caduca, deja pedir otro', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    simularApi({
      'POST /kiosco/emparejar': () => ({ codigo: 'ABCD2345', secreto: 's', caducaEn: '2026-09-30T10:10:00Z' }),
      'POST /kiosco/emparejar/estado': () => ({ estado: 'CADUCADO' }),
    });
    pintar(<Kiosco />);

    await screen.findByText('ABCD 2345');
    await act(() => vi.advanceTimersByTimeAsync(4_000));

    expect(await screen.findByText(T.caducado)).toBeTruthy();
    expect(screen.getByRole('button', { name: T.otroCodigo })).toBeTruthy();
  });
});

describe('fichar', () => {
  beforeEach(() => localStorage.setItem('nx-kiosco-token', 'token-del-kiosco'));

  it('con el nombre y el PIN: identifica, ficha la entrada y lo dice en la hora de la empresa', async () => {
    const llamadas = simularApi({
      'GET /kiosco/yo': () => YO,
      'GET /kiosco/plantilla': () => [LUCIA],
      'POST /kiosco/identificar': () => ({ usuarioId: 7, nombre: 'Lucía', estado: 'SIN_JORNADA', proyectos: [] }),
      'POST /kiosco/fichar': () => ({ nombre: 'Lucía', tipo: 'INICIO', instante: '2026-09-30T07:02:00Z' }),
    });
    pintar(<Kiosco />);

    // Sin cámara (jsdom), directamente a buscar el nombre.
    await userEvent.click(await screen.findByRole('button', { name: T.oBuscaTuNombre }));
    await userEvent.type(screen.getByLabelText(T.buscar), 'lucia');
    await userEvent.click(screen.getByRole('button', { name: 'Lucía Pérez' }));

    for (const cifra of ['4', '8', '2', '7']) {
      await userEvent.click(screen.getByRole('button', { name: cifra }));
    }
    await userEvent.click(screen.getByRole('button', { name: T.entrar }));

    await userEvent.click(await screen.findByRole('button', { name: T.ficharEntrada }));

    // 07:02 UTC son las 08:02 en Canarias en verano.
    expect(await screen.findByText(T.hecho.INICIO('Lucía', '08:02 h'))).toBeTruthy();
    expect(llamadas.a('POST', '/kiosco/identificar').map((l) => l.cuerpo)).toEqual([{ usuarioId: 7, pin: '4827' }]);
    expect(llamadas.a('POST', '/kiosco/fichar').map((l) => l.cuerpo)).toEqual([
      { usuarioId: 7, pin: '4827', tipo: 'INICIO' },
    ]);
  });

  it('con varios proyectos hoy, pregunta en cuál antes de fichar la entrada', async () => {
    const llamadas = simularApi({
      'GET /kiosco/yo': () => YO,
      'GET /kiosco/plantilla': () => [LUCIA],
      'POST /kiosco/identificar': () => ({
        usuarioId: 7,
        nombre: 'Lucía',
        estado: 'SIN_JORNADA',
        proyectos: [
          { id: 1, codigo: 'P-1', nombre: 'Almacén' },
          { id: 2, codigo: 'P-2', nombre: 'Tienda' },
        ],
      }),
      'POST /kiosco/fichar': () => ({ nombre: 'Lucía', tipo: 'INICIO', instante: '2026-09-30T07:02:00Z' }),
    });
    pintar(<Kiosco />);

    await userEvent.click(await screen.findByRole('button', { name: T.oBuscaTuNombre }));
    await userEvent.click(screen.getByRole('button', { name: 'Lucía Pérez' }));
    for (const cifra of ['4', '8', '2', '7']) await userEvent.click(screen.getByRole('button', { name: cifra }));
    await userEvent.click(screen.getByRole('button', { name: T.entrar }));
    await userEvent.click(await screen.findByRole('button', { name: T.ficharEntrada }));
    await userEvent.click(await screen.findByRole('button', { name: 'P-2 · Tienda' }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/kiosco/fichar').map((l) => l.cuerpo)).toEqual([
        { usuarioId: 7, pin: '4827', tipo: 'INICIO', proyectoId: 2 },
      ]),
    );
  });

  it('un PIN equivocado se dice, y se puede volver a probar', async () => {
    simularApi({
      'GET /kiosco/yo': () => YO,
      'GET /kiosco/plantilla': () => [LUCIA],
      'POST /kiosco/identificar': () => problema(403, 'PIN incorrecto.'),
    });
    pintar(<Kiosco />);

    await userEvent.click(await screen.findByRole('button', { name: T.oBuscaTuNombre }));
    await userEvent.click(screen.getByRole('button', { name: 'Lucía Pérez' }));
    for (const cifra of ['1', '9', '8', '3']) await userEvent.click(screen.getByRole('button', { name: cifra }));
    await userEvent.click(screen.getByRole('button', { name: T.entrar }));

    expect(await screen.findByText('PIN incorrecto.')).toBeTruthy();
    expect(screen.getByRole('button', { name: T.entrar })).toBeTruthy();
  });

  it('si el servidor dice que el token ya no vale, se olvida y vuelve a emparejar', async () => {
    simularApi({
      'GET /kiosco/yo': () => problema(401, 'No autenticado'),
      'GET /kiosco/plantilla': () => problema(401, 'No autenticado'),
      'POST /kiosco/emparejar': () => ({ codigo: 'WXYZ6789', secreto: 's', caducaEn: '2026-09-30T10:10:00Z' }),
    });
    pintar(<Kiosco />);

    expect(await screen.findByText('WXYZ 6789')).toBeTruthy();
    expect(localStorage.getItem('nx-kiosco-token')).toBeNull();
  });
});
