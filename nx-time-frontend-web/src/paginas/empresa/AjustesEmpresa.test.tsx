/**
 * Los ajustes de la empresa (fase Z2): el nombre, y la zona horaria con su
 * confirmación, porque cambiarla mueve los días de todo el histórico.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion, sesionActual } from '../../api/sesion';
import { empresa } from '../../i18n/es/empresa';
import { kiosco } from '../../i18n/es/kiosco';
import { pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { zonaActual } from '../../util/fechas';
import { AjustesEmpresa } from './AjustesEmpresa';

const A = empresa.ajustes;

const AJUSTES = { nombre: 'Talleres Ana', zonaHoraria: 'Europe/Madrid', firmasInvalidadas: 0 };

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
  cerrarSesion();
});

describe('ajustes de la empresa', () => {
  it('cambiar solo el nombre se guarda sin preguntar', async () => {
    const llamadas = simularApi({
      'GET /api/v1/empresa/ajustes': () => AJUSTES,
      'PUT /api/v1/empresa/ajustes': ({ cuerpo }) => ({ ...(cuerpo as object), firmasInvalidadas: 0 }),
    });
    pintar(<AjustesEmpresa />, { sesion: sesionDe('ADMIN') });

    const nombre = await screen.findByLabelText(A.nombre);
    await userEvent.clear(nombre);
    await userEvent.type(nombre, 'Talleres Ana S.L.');
    await userEvent.click(screen.getByRole('button', { name: A.guardar }));

    await waitFor(() =>
      expect(llamadas.a('PUT', '/api/v1/empresa/ajustes').map((l) => l.cuerpo)).toEqual([
        { nombre: 'Talleres Ana S.L.', zonaHoraria: 'Europe/Madrid' },
      ]),
    );
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('cambiar la zona pide confirmación, y al guardarla la sesión pasa a esa zona', async () => {
    const llamadas = simularApi({
      'GET /api/v1/empresa/ajustes': () => AJUSTES,
      'PUT /api/v1/empresa/ajustes': ({ cuerpo }) => ({ ...(cuerpo as object), firmasInvalidadas: 2 }),
    });
    pintar(<AjustesEmpresa />, { sesion: { ...sesionDe('ADMIN'), zonaHoraria: 'Europe/Madrid' } });

    await userEvent.selectOptions(await screen.findByLabelText(A.zona), 'Atlantic/Canary');
    await userEvent.click(screen.getByRole('button', { name: A.guardar }));

    // Nada se guarda hasta confirmar.
    expect(await screen.findByText(A.confirmarTexto('Atlantic/Canary'))).toBeTruthy();
    expect(llamadas.a('PUT', '/api/v1/empresa/ajustes')).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: A.confirmar }));

    await waitFor(() => expect(zonaActual()).toBe('Atlantic/Canary'));
    expect(sesionActual()?.zonaHoraria).toBe('Atlantic/Canary');
    expect(llamadas.a('PUT', '/api/v1/empresa/ajustes').map((l) => l.cuerpo)).toEqual([
      { nombre: 'Talleres Ana', zonaHoraria: 'Atlantic/Canary' },
    ]);
  });

  it('un error del servidor se enseña en el formulario', async () => {
    simularApi({
      'GET /api/v1/empresa/ajustes': () => AJUSTES,
      'PUT /api/v1/empresa/ajustes': () => problema(409, 'Ya hay otra empresa con ese nombre.'),
    });
    pintar(<AjustesEmpresa />, { sesion: sesionDe('ADMIN') });

    await userEvent.click(await screen.findByRole('button', { name: A.guardar }));

    expect(await screen.findByText('Ya hay otra empresa con ese nombre.')).toBeTruthy();
  });
});

describe('kioscos de la empresa', () => {
  it('se da de alta uno con el código de la tablet y se revoca', async () => {
    let lista: { id: number; nombre: string; creadoEn: string; activo: boolean }[] = [];
    const llamadas = simularApi({
      'GET /api/v1/empresa/ajustes': () => AJUSTES,
      'GET /api/v1/empresa/kioscos': () => lista,
      'POST /api/v1/empresa/kioscos': ({ cuerpo }) => {
        lista = [{ id: 3, nombre: (cuerpo as { nombre: string }).nombre, creadoEn: '2026-09-30T08:00:00Z', activo: true }];
        return lista[0];
      },
      'DELETE /api/v1/empresa/kioscos/{id}': () => {
        lista = lista.map((k) => ({ ...k, activo: false }));
        return new Response(null, { status: 204 });
      },
    });
    pintar(<AjustesEmpresa />, { sesion: sesionDe('ADMIN') });

    await userEvent.type(await screen.findByLabelText(kiosco.gestion.codigo), 'abcd-2345');
    await userEvent.type(screen.getByLabelText(kiosco.gestion.nombre), 'Entrada almacén');
    await userEvent.click(screen.getByRole('button', { name: kiosco.gestion.anadir }));

    expect(await screen.findByText('Entrada almacén')).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/empresa/kioscos').map((l) => l.cuerpo)).toEqual([
      { codigo: 'abcd-2345', nombre: 'Entrada almacén' },
    ]);

    await userEvent.click(screen.getByRole('button', { name: kiosco.gestion.revocar }));
    const dialogo = await screen.findByRole('dialog');
    await userEvent.click(within(dialogo).getByRole('button', { name: kiosco.gestion.revocar }));

    expect(await screen.findByText(kiosco.gestion.revocado)).toBeTruthy();
    expect(llamadas.a('DELETE', '/api/v1/empresa/kioscos/3')).toHaveLength(1);
  });
});
