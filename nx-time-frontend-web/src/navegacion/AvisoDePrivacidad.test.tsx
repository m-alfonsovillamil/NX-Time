/**
 * El aviso de privacidad de la primera entrada (ADR 047): sale una vez, con el
 * contacto de la empresa si lo hay, y con «Entendido» no vuelve.
 */

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../api/cliente';
import { cerrarSesion } from '../api/sesion';
import { T } from '../i18n/es';
import { pintar, sesionDe, simularApi, sinContenido } from '../pruebas/api';
import { AvisoDePrivacidad } from './AvisoDePrivacidad';

const P = T.navegacion.privacidad;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
  cerrarSesion();
});

describe('aviso de privacidad', () => {
  it('dice quién es la responsable y a qué correo escribir, y con «Entendido» se va', async () => {
    const llamadas = simularApi({
      'GET /api/v1/perfil/aviso-de-privacidad': () => ({
        empresa: 'Talleres Ana',
        contacto: 'privacidad@talleres.es',
        visto: false,
      }),
      'POST /api/v1/perfil/aviso-de-privacidad/visto': () => sinContenido(),
    });
    pintar(<AvisoDePrivacidad />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(P.texto('Talleres Ana'), { exact: false })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'privacidad@talleres.es' }).getAttribute('href')).toBe(
      'mailto:privacidad@talleres.es',
    );

    await userEvent.click(screen.getByRole('button', { name: P.entendido }));

    await waitFor(() => expect(screen.queryByRole('region', { name: P.titulo })).toBeNull());
    expect(llamadas.a('POST', '/api/v1/perfil/aviso-de-privacidad/visto')).toHaveLength(1);
  });

  it('con un enlace, lo abre aparte; sin contacto, manda a la empresa', async () => {
    simularApi({
      'GET /api/v1/perfil/aviso-de-privacidad': () => ({
        empresa: 'Talleres Ana',
        contacto: 'https://talleres.es/privacidad',
        visto: false,
      }),
    });
    pintar(<AvisoDePrivacidad />, { sesion: sesionDe('EMPLEADO') });

    const enlace = await screen.findByRole('link', { name: 'https://talleres.es/privacidad' });
    expect(enlace.getAttribute('target')).toBe('_blank');
    expect(enlace.getAttribute('rel')).toContain('noopener');
  });

  it('sin contacto, manda a la empresa', async () => {
    simularApi({
      'GET /api/v1/perfil/aviso-de-privacidad': () => ({ empresa: 'Talleres Ana', contacto: null, visto: false }),
    });
    pintar(<AvisoDePrivacidad />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(P.sinContacto)).toBeTruthy();
  });

  it('si ya lo vio, no sale', async () => {
    const llamadas = simularApi({
      'GET /api/v1/perfil/aviso-de-privacidad': () => ({ empresa: 'Talleres Ana', contacto: null, visto: true }),
    });
    pintar(<AvisoDePrivacidad />, { sesion: sesionDe('EMPLEADO') });

    await waitFor(() => expect(llamadas.a('GET', '/api/v1/perfil/aviso-de-privacidad')).toHaveLength(1));
    expect(screen.queryByRole('region', { name: P.titulo })).toBeNull();
  });
});
