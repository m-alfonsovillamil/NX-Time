/**
 * El panel de gestión y las ausencias del equipo.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { gestion } from '../../i18n/es/gestion';
import { AUTHORITIES, pintar, sesionDe, simularApi } from '../../pruebas/api';
import { AusenciasEquipo, PaginaAusenciasResueltas } from './AusenciasEquipo';
import { PanelGestion, bandejasPara } from './PanelGestion';

const P = gestion.panel;
const G = gestion.ausencias;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('panel de gestión', () => {
  it('solo las bandejas que la persona puede abrir y que la web ya tiene', () => {
    const rutas = (rol: keyof typeof AUTHORITIES) => bandejasPara(AUTHORITIES[rol]).map((b) => b.ruta);
    expect(rutas('GESTOR')).toEqual(expect.arrayContaining(['ausencias-equipo/pendientes', 'correcciones/pendientes', 'horas-extra']));
    // Los borrados son de quien gestiona la plantilla (RRHH y ADMIN).
    expect(rutas('ADMIN')).toContain('borrados');
    expect(rutas('GESTOR')).not.toContain('borrados');
    expect(rutas('EMPLEADO')).not.toContain('horas-extra');
  });

  it('cada contador lleva a su bandeja, y los accesos salen del catálogo', async () => {
    simularApi({ 'GET /api/v1/dashboard/pendientes': () => ({ ausencias: 2, correcciones: 0, horasExtra: 5, borrados: 1 }) });
    pintar(<PanelGestion />, { sesion: sesionDe('GESTOR') });

    const ausencias = await screen.findByRole('link', { name: `2 ${P.ausencias}` });
    expect(ausencias.getAttribute('href')).toBe('/ausencias-equipo/pendientes');
    expect(screen.getByRole('link', { name: `5 ${P.horasExtra}` }).getAttribute('href')).toBe('/horas-extra');
    expect(screen.getByRole('link', { name: 'Historial del equipo' }).getAttribute('href')).toBe('/equipo');
    // El panel no se enlaza a sí mismo.
    expect(screen.queryByRole('link', { name: P.titulo })).toBeNull();
  });

  it('sin nada pendiente, lo dice en vez de enseñar ceros', async () => {
    simularApi({ 'GET /api/v1/dashboard/pendientes': () => ({ ausencias: 0, correcciones: 0, horasExtra: 0, borrados: 0 }) });
    pintar(<PanelGestion />, { sesion: sesionDe('GESTOR') });
    expect(await screen.findByText(P.nadaPendiente)).toBeTruthy();
  });
});

const SOLICITUD = {
  id: 4,
  fechaInicio: '2026-10-05',
  fechaFin: '2026-10-09',
  tipo: 'VACACIONES',
  motivo: 'Viaje familiar',
  estado: 'PENDIENTE',
  usuario: { nombre: 'Javier' },
  aprobadoPor: null,
  fechaResolucion: null,
  comentarioResolucion: null,
  diasHabiles: 5,
};

describe('ausencias del equipo', () => {
  it('aprobar va directo; rechazar exige motivo', async () => {
    const llamadas = simularApi({
      'GET /api/v1/ausencias/gestor/pendientes': () => [SOLICITUD, { ...SOLICITUD, id: 5, usuario: { nombre: 'Ana' } }],
      'PATCH /api/v1/ausencias/{id}/estado': () => ({ ...SOLICITUD, estado: 'APROBADA' }),
    });
    pintar(<AusenciasEquipo />, { sesion: sesionDe('GESTOR') });

    const lista = await screen.findByRole('list', { name: G.pendientes });
    expect(within(lista).getAllByText(/5 días hábiles/)).toHaveLength(2);
    const [javier, ana] = within(lista).getAllByRole('listitem');
    await userEvent.click(within(javier as HTMLElement).getByRole('button', { name: G.aprobar }));

    await userEvent.click(within(ana as HTMLElement).getByRole('button', { name: G.rechazar }));
    const dialogo = await screen.findByRole('dialog', { name: G.rechazarTitulo('Ana') });
    await userEvent.click(within(dialogo).getByRole('button', { name: G.rechazar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(G.comentarioVacio);
    await userEvent.type(within(dialogo).getByLabelText(G.comentario), 'Coincide con el cierre trimestral');
    await userEvent.click(within(dialogo).getByRole('button', { name: G.rechazar }));

    await waitFor(() =>
      expect(llamadas.llamadas.filter((l) => l.metodo === 'PATCH').map((l) => [l.ruta, l.cuerpo])).toEqual([
        ['/api/v1/ausencias/4/estado', { estado: 'APROBADA' }],
        ['/api/v1/ausencias/5/estado', { estado: 'RECHAZADA', comentario: 'Coincide con el cierre trimestral' }],
      ]),
    );
  });

  it('/ausencias-equipo/resueltas abre esa pestaña, con quién resolvió y su respuesta', async () => {
    simularApi({
      'GET /api/v1/gestor/ausencias-historial': () => ({
        contenido: [
          {
            ...SOLICITUD,
            estado: 'RECHAZADA',
            aprobadoPor: { nombre: 'Marta' },
            fechaResolucion: '2026-09-20T10:00:00Z',
            comentarioResolucion: 'Coincide con el cierre trimestral',
          },
        ],
        hayMas: false,
      }),
    });
    pintar(<PaginaAusenciasResueltas />, { sesion: sesionDe('GESTOR') });

    expect(await screen.findByText(G.respuesta('Coincide con el cierre trimestral'))).toBeTruthy();
    expect(screen.getByText(/^Resuelta por Marta el/)).toBeTruthy();
    expect(screen.getByRole('tab', { name: G.resueltas }).getAttribute('aria-selected')).toBe('true');
    expect(screen.queryByRole('button', { name: G.aprobar })).toBeNull();
  });
});
