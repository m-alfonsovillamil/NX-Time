/**
 * Los ajustes de la empresa (fase Z2): el nombre, y la zona horaria con su
 * confirmación, porque cambiarla mueve los días de todo el histórico. Y el
 * contacto de privacidad (ADR 047).
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
        { nombre: 'Talleres Ana S.L.', zonaHoraria: 'Europe/Madrid', contactoPrivacidad: '' },
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
      { nombre: 'Talleres Ana', zonaHoraria: 'Atlantic/Canary', contactoPrivacidad: '' },
    ]);
  });

  it('el contacto de privacidad se guarda recortado, y desde aquí se llega a los accesos (ADR 047)', async () => {
    const llamadas = simularApi({
      'GET /api/v1/empresa/ajustes': () => ({ ...AJUSTES, contactoPrivacidad: 'viejo@talleres.es' }),
      'PUT /api/v1/empresa/ajustes': ({ cuerpo }) => ({ ...(cuerpo as object), firmasInvalidadas: 0 }),
    });
    pintar(<AjustesEmpresa />, { sesion: sesionDe('ADMIN') });

    const contacto = await screen.findByLabelText(A.contacto);
    expect((contacto as HTMLInputElement).value).toBe('viejo@talleres.es');
    await userEvent.clear(contacto);
    await userEvent.type(contacto, ' https://talleres.es/privacidad ');
    await userEvent.click(screen.getByRole('button', { name: A.guardar }));

    await waitFor(() =>
      expect(llamadas.a('PUT', '/api/v1/empresa/ajustes').map((l) => l.cuerpo)).toEqual([
        { nombre: 'Talleres Ana', zonaHoraria: 'Europe/Madrid', contactoPrivacidad: 'https://talleres.es/privacidad' },
      ]),
    );
    expect(screen.getByRole('link', { name: A.verAccesos }).getAttribute('href')).toBe('/accesos-a-datos');
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

describe('canal de denuncias de la empresa (ADR 048)', () => {
  const CANAL = empresa.canal;

  it('designar al Responsable lo guarda y pone al día los permisos de la sesión', async () => {
    const llamadas = simularApi({
      'GET /api/v1/empresa/ajustes': () => AJUSTES,
      'GET /api/v1/empresa/canal-denuncias': () => ({}),
      'GET /api/v1/gestor/plantilla': () => [
        { id: 7, nombre: 'Laura Gil', activo: true },
        { id: 8, nombre: 'Baja Antigua', activo: false },
      ],
      'PUT /api/v1/empresa/canal-denuncias': ({ cuerpo }) => ({ ...(cuerpo as object), responsable: 'Laura Gil' }),
      'GET /api/v1/perfil': () => ({ id: 1, nombre: 'Ana', authorities: ['empresa:configurar'] }),
    });
    pintar(<AjustesEmpresa />, { sesion: sesionDe('ADMIN') });

    const selector = await screen.findByLabelText(CANAL.responsable);
    // Quien está de baja no se ofrece.
    expect(within(selector).queryByText('Baja Antigua')).toBeNull();
    await userEvent.selectOptions(selector, '7');
    await userEvent.type(screen.getByLabelText(CANAL.viaVerbal), 'Teléfono 600 000 000');
    await userEvent.click(within(screen.getByRole('region', { name: CANAL.titulo })).getByRole('button', { name: CANAL.guardar }));

    await waitFor(() =>
      expect(llamadas.a('PUT', '/api/v1/empresa/canal-denuncias').map((l) => l.cuerpo)).toEqual([
        { responsableId: 7, viaVerbal: 'Teléfono 600 000 000' },
      ]),
    );
    // El ADMIN ya no instruye: el menú lo sabe sin esperar a renovar la sesión.
    await waitFor(() => expect(sesionActual()?.authorities).toEqual(['empresa:configurar']));
    expect(screen.getByRole('link', { name: CANAL.autoridadEnlace }).getAttribute('href')).toBe(CANAL.autoridadUrl);
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
