/**
 * El PIN y la tarjeta del kiosco en el perfil (ADR 033).
 */

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { kiosco } from '../../i18n/es/kiosco';
import { pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { FicharEnKiosco } from './FicharEnKiosco';

const P = kiosco.perfil;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fichar en un kiosco, desde el perfil', () => {
  it('guarda el PIN tecleado, y un PIN corto no llega a salir', async () => {
    let tienePin = false;
    const llamadas = simularApi({
      'GET /api/v1/perfil/kiosco': () => ({ tienePin, tieneTarjeta: false }),
      'PUT /api/v1/perfil/kiosco/pin': () => {
        tienePin = true;
        return { tienePin, tieneTarjeta: false };
      },
    });
    pintar(<FicharEnKiosco />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(P.sinPin)).toBeTruthy();
    await userEvent.type(screen.getByLabelText(P.pin), '12');
    await userEvent.click(screen.getByRole('button', { name: P.guardarPin }));
    expect(await screen.findByText(P.pinInvalido)).toBeTruthy();
    expect(llamadas.a('PUT', '/api/v1/perfil/kiosco/pin')).toEqual([]);

    await userEvent.type(screen.getByLabelText(P.pin), '85');
    await userEvent.click(screen.getByRole('button', { name: P.guardarPin }));

    await waitFor(() =>
      expect(llamadas.a('PUT', '/api/v1/perfil/kiosco/pin').map((l) => l.cuerpo)).toEqual([{ pin: '1285' }]),
    );
    expect(await screen.findByText(P.tienePin)).toBeTruthy();
  });

  it('el PIN demasiado fácil lo rechaza el servidor, y se dice', async () => {
    simularApi({
      'GET /api/v1/perfil/kiosco': () => ({ tienePin: false, tieneTarjeta: false }),
      'PUT /api/v1/perfil/kiosco/pin': () => problema(400, 'Ese PIN es demasiado fácil de adivinar.'),
    });
    pintar(<FicharEnKiosco />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.type(await screen.findByLabelText(P.pin), '1234');
    await userEvent.click(screen.getByRole('button', { name: P.guardarPin }));

    expect(await screen.findByText('Ese PIN es demasiado fácil de adivinar.')).toBeTruthy();
  });

  it('la tarjeta se pide solo al querer verla, y se pinta como imagen', async () => {
    const llamadas = simularApi({
      'GET /api/v1/perfil/kiosco': () => ({ tienePin: false, tieneTarjeta: true }),
      'GET /api/v1/perfil/kiosco/tarjeta': () => ({
        usuarioId: 7,
        nombre: 'Lucía',
        codigo: 'NXK1.7.1.abc',
        svg: '<svg xmlns="http://www.w3.org/2000/svg"></svg>',
      }),
    });
    pintar(<FicharEnKiosco />, { sesion: sesionDe('EMPLEADO') });

    await screen.findByText(P.sinPin);
    expect(llamadas.a('GET', '/api/v1/perfil/kiosco/tarjeta')).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: P.verTarjeta }));
    const imagen = (await screen.findByRole('img', { name: P.tarjeta })) as HTMLImageElement;
    expect(imagen.src.startsWith('data:image/svg+xml')).toBe(true);
  });
});
