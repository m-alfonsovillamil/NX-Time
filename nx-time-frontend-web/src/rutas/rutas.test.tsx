/**
 * La aplicación entera, sin servidor: rutas, marco, menú por authorities y campana.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../api/cliente';
import { cerrarSesion, sesionActual } from '../api/sesion';
import { T } from '../i18n/es';
import { json, pintar, sesionDe, simularApi, sinContenido, type Manejador, type Ruta } from '../pruebas/api';
import { App, Requiere } from './rutas';

const N = T.navegacion;

/** Lo que el marco y la jornada piden siempre al abrirse. */
function apiBasica(extra: Partial<Record<Ruta, Manejador>> = {}) {
  return simularApi({
    'GET /api/v1/fichaje/activo': () => sinContenido(),
    'GET /api/v1/fichaje/hoy': () => ({ laborable: true }),
    'GET /api/v1/avisos/no-leidos': () => ({ noLeidos: 0 }),
    ...extra,
  });
}

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('sin sesión', () => {
  it('una sección lleva al login y, al entrar, vuelve a ella', async () => {
    apiBasica({
      'POST /auth/login': () => ({ token: 't', refreshToken: 'r', nombre: 'Ana', authorities: ['fichaje:leer'] }),
    });
    pintar(<App />, { ruta: '/fichar' });

    await userEvent.type(await screen.findByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.type(screen.getByLabelText(T.login.contrasena), 'x');
    await userEvent.click(screen.getByRole('button', { name: T.login.entrar }));

    expect(await screen.findByRole('heading', { name: T.fichar.titulo })).toBeTruthy();
  });

  it('una ruta que no existe da 404 sin pedir entrar', () => {
    apiBasica();
    pintar(<App />, { ruta: '/no-existe' });
    expect(screen.getByRole('heading', { name: T.noEncontrado.titulo })).toBeTruthy();
  });
});

describe('con sesión', () => {
  it('pinta el marco con el menú y la página dentro', async () => {
    apiBasica();
    pintar(<App />, { ruta: '/fichar', sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByRole('heading', { name: T.fichar.titulo })).toBeTruthy();
    // Con una sola sección no hay barra inferior: solo la lateral.
    const menu = screen.getByRole('navigation', { name: N.menuPrincipal });
    expect(within(menu).getByRole('link', { name: N.secciones.fichar }).getAttribute('aria-current')).toBe('page');
  });

  it('un EMPLEADO no ve el grupo de gestión', async () => {
    apiBasica();
    pintar(<App />, { ruta: '/fichar', sesion: sesionDe('EMPLEADO') });
    await screen.findByRole('heading', { name: T.fichar.titulo });
    expect(screen.queryByRole('heading', { name: N.grupos.gestion })).toBeNull();
  });

  it('una ruta que no existe da 404 dentro del marco', async () => {
    apiBasica();
    pintar(<App />, { ruta: '/no-existe', sesion: sesionDe('EMPLEADO') });
    expect(await screen.findByRole('heading', { name: T.noEncontrado.titulo })).toBeTruthy();
    expect(screen.getByRole('navigation', { name: N.menuPrincipal })).toBeTruthy();
  });

  it('cerrar sesión desde el menú de usuario avisa al servidor y vuelve al login', async () => {
    const api = apiBasica({ 'POST /auth/logout': () => sinContenido() });
    pintar(<App />, { ruta: '/fichar', sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByLabelText(N.usuario.menu('Ana')));
    await userEvent.click(screen.getByRole('button', { name: N.usuario.salir }));

    expect(await screen.findByRole('button', { name: T.login.entrar })).toBeTruthy();
    expect(sesionActual()).toBeNull();
    await waitFor(() => expect(api.a('POST', '/auth/logout').map((l) => l.cuerpo)).toEqual([{ refreshToken: 'refresh' }]));
  });
});

describe('la guarda por authority', () => {
  it('sin la authority, 403 con explicación y no la página', () => {
    pintar(
      <Requiere authority="informe:exportar">
        <p>Informes</p>
      </Requiere>,
      { sesion: sesionDe('GESTOR') },
    );
    expect(screen.getByRole('heading', { name: T.sinPermiso.titulo })).toBeTruthy();
    expect(screen.queryByText('Informes')).toBeNull();
  });

  it('con ella, la página', () => {
    pintar(
      <Requiere authority="informe:exportar">
        <p>Informes</p>
      </Requiere>,
      { sesion: sesionDe('RRHH') },
    );
    expect(screen.getByText('Informes')).toBeTruthy();
  });
});

describe('la campana', () => {
  it('dice cuántos avisos hay sin leer', async () => {
    apiBasica({ 'GET /api/v1/avisos/no-leidos': () => ({ noLeidos: 3 }) });
    pintar(<App />, { ruta: '/fichar', sesion: sesionDe('EMPLEADO') });
    expect(await screen.findByRole('button', { name: N.campana.etiqueta(3) })).toBeTruthy();
  });

  it('al abrir un aviso lo marca leído y, si su página existe, navega a ella', async () => {
    const api = apiBasica({
      'GET /api/v1/avisos/no-leidos': () => ({ noLeidos: 1 }),
      'GET /api/v1/avisos': () =>
        json({
          contenido: [{ id: 41, titulo: 'Bienvenida', rutaDestino: 'fichar', leido: false, creadoEn: '2026-09-26T08:00:00Z' }],
          hayMas: false,
        }),
      'PATCH /api/v1/avisos/{id}/leido': () => sinContenido(),
    });
    pintar(<App />, { ruta: '/no-existe', sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: N.campana.etiqueta(1) }));
    await userEvent.click(await screen.findByRole('button', { name: /Bienvenida/ }));

    expect(await screen.findByRole('heading', { name: T.fichar.titulo })).toBeTruthy();
    await waitFor(() => expect(api.a('PATCH', '/api/v1/avisos/41/leido')).toHaveLength(1));
  });
});
