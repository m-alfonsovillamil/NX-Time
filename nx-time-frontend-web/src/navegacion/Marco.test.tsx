/**
 * Los apartados plegables del menú lateral (1/10/2026): cuáles salen abiertos,
 * que se abren y cierran con su botón, y que se recuerda. Qué entradas van en
 * cada apartado lo prueba `secciones.test.ts`.
 */

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { reiniciarEstadoDeRed } from '../api/cliente';
import { cerrarSesion } from '../api/sesion';
import { T } from '../i18n/es';
import { pintar, sesionDe, simularApi } from '../pruebas/api';
import { Marco } from './Marco';

const N = T.navegacion;

function pintarMarco(rol: Parameters<typeof sesionDe>[0], ruta: string) {
  simularApi({
    'GET /api/v1/avisos/no-leidos': () => ({ noLeidos: 0 }),
    'GET /api/v1/avisos': () => ({ content: [], totalElements: 0 }),
  });
  pintar(
    <Routes>
      <Route element={<Marco />}>
        <Route path="*" element={<p>La página</p>} />
      </Route>
    </Routes>,
    { ruta, sesion: sesionDe(rol) },
  );
  // El lateral es el primero de los dos «Menú principal» (el otro, la barra del móvil).
  return within(screen.getAllByRole('navigation', { name: N.menuPrincipal })[0] as HTMLElement);
}

const boton = (lateral: ReturnType<typeof pintarMarco>, subgrupo: keyof typeof N.subgrupos) =>
  lateral.getByRole('button', { name: N.subgrupos[subgrupo] });

beforeEach(() => {
  localStorage.clear();
  reiniciarEstadoDeRed();
});

afterEach(() => {
  cerrarSesion();
});

describe('los apartados del menú lateral', () => {
  it('a un ADMIN le sale abierto solo el apartado de la página en que está', () => {
    const lateral = pintarMarco('ADMIN', '/plantilla');

    expect(boton(lateral, 'organizacion').getAttribute('aria-expanded')).toBe('true');
    expect(lateral.getByRole('link', { name: 'Plantilla' })).toBeTruthy();
    expect(boton(lateral, 'control').getAttribute('aria-expanded')).toBe('false');
    expect(lateral.queryByRole('link', { name: 'Informes' })).toBeNull();
  });

  it('el botón abre y cierra su apartado, y se recuerda', async () => {
    const lateral = pintarMarco('ADMIN', '/fichar');

    await userEvent.click(boton(lateral, 'control'));
    expect(boton(lateral, 'control').getAttribute('aria-expanded')).toBe('true');
    expect(lateral.getByRole('link', { name: 'Informes' })).toBeTruthy();
    expect(JSON.parse(localStorage.getItem('nx-menu-abiertos') ?? '{}')).toMatchObject({ control: true });

    await userEvent.click(boton(lateral, 'control'));
    expect(lateral.queryByRole('link', { name: 'Informes' })).toBeNull();
    expect(JSON.parse(localStorage.getItem('nx-menu-abiertos') ?? '{}')).toMatchObject({ control: false });
  });

  it('lo que se dejó abierto sigue abierto al volver', () => {
    localStorage.setItem('nx-menu-abiertos', JSON.stringify({ administracion: true }));

    const lateral = pintarMarco('ADMIN', '/fichar');

    expect(lateral.getByRole('link', { name: 'Ajustes de la empresa' })).toBeTruthy();
  });

  it('con un almacenamiento roto, el menú funciona igual', () => {
    localStorage.setItem('nx-menu-abiertos', '{no es JSON');

    const lateral = pintarMarco('ADMIN', '/fichar');

    expect(boton(lateral, 'jornada').getAttribute('aria-expanded')).toBe('true');
  });

  it('a un EMPLEADO, con un menú corto, le sale todo abierto', () => {
    const lateral = pintarMarco('EMPLEADO', '/fichar');

    for (const subgrupo of ['jornada', 'ausencias', 'en-la-empresa'] as const) {
      expect(boton(lateral, subgrupo).getAttribute('aria-expanded'), subgrupo).toBe('true');
    }
    expect(lateral.getByRole('link', { name: 'Canal de denuncias' })).toBeTruthy();
  });

  it('un apartado con una sola entrada no se pliega: es un enlace', () => {
    // Un GESTOR, de Administración, solo tiene la gestión de ofertas.
    const lateral = pintarMarco('GESTOR', '/fichar');

    expect(lateral.queryByRole('button', { name: N.subgrupos.administracion })).toBeNull();
    expect(lateral.getByRole('link', { name: 'Gestión de ofertas' })).toBeTruthy();
  });
});
