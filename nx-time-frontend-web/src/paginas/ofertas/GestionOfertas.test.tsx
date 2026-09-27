/**
 * Gestionar ofertas y el canal de denuncias de quien instruye.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { denuncias } from '../../i18n/es/denuncias';
import { ofertas } from '../../i18n/es/ofertas';
import { json, pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { CanalDenuncias } from '../denuncias/CanalDenuncias';
import { GestionOfertas } from './GestionOfertas';

const G = ofertas.gestion;
const C = denuncias.canal;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function oferta(id: number, titulo: string, estado: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    titulo,
    descripcion: 'Descripción',
    puesto: null,
    departamento: null,
    publicadaPor: 'Marta',
    estado,
    fechaPublicacion: null,
    fechaCierre: null,
    admiteCandidaturas: estado === 'ABIERTA',
    plazoVencido: false,
    yaMePresente: false,
    candidaturas: 0,
    ...extra,
  };
}

const DEPARTAMENTOS = [
  { id: 1, nombre: 'Ingeniería', empleados: 3 },
  { id: 2, nombre: 'Producto', empleados: 2 },
];

describe('gestión de ofertas', () => {
  it('cada estado ofrece lo suyo: publicar un borrador, retirar o cerrar una abierta', async () => {
    const llamadas = simularApi({
      'GET /api/v1/ofertas/gestion': () => [oferta(1, 'Backend', 'BORRADOR'), oferta(2, 'Producto', 'ABIERTA'), oferta(3, 'Soporte', 'CERRADA')],
      'PATCH /api/v1/ofertas/{id}/estado': () => oferta(1, 'Backend', 'ABIERTA'),
    });
    pintar(<GestionOfertas />, { sesion: sesionDe('GESTOR') });

    const borrador = (await screen.findByRole('group', { name: G.accionesDe('Backend') })) as HTMLElement;
    const abierta = screen.getByRole('group', { name: G.accionesDe('Producto') });
    const cerrada = screen.getByRole('group', { name: G.accionesDe('Soporte') });
    expect(within(borrador).queryByRole('button', { name: G.verCandidaturas })).toBeNull();
    expect(within(abierta).getByRole('button', { name: G.retirar })).toBeTruthy();
    // Una cerrada no se reabre ni se edita.
    expect(within(cerrada).queryByRole('button', { name: G.editar })).toBeNull();

    await userEvent.click(within(borrador).getByRole('button', { name: G.publicar }));
    await userEvent.click(within(abierta).getByRole('button', { name: G.cerrar }));
    const dialogo = await screen.findByRole('dialog', { name: G.cerrarTitulo('Producto') });
    await userEvent.click(within(dialogo).getByRole('button', { name: G.cerrar }));

    await waitFor(() =>
      expect(llamadas.llamadas.filter((l) => l.metodo === 'PATCH').map((l) => [l.ruta, l.cuerpo])).toEqual([
      ['/api/v1/ofertas/1/estado', { estado: 'ABIERTA' }],
      ['/api/v1/ofertas/2/estado', { estado: 'CERRADA' }],
    ]),
    );
  });

  /* Es un PUT: si el departamento no se preselecciona, guardar se lo quitaría. */
  it('editar conserva el departamento que ya tenía', async () => {
    const llamadas = simularApi({
      'GET /api/v1/ofertas/gestion': () => [oferta(2, 'Producto', 'ABIERTA', { departamento: 'Producto' })],
      'GET /api/v1/departamentos': () => DEPARTAMENTOS,
      'PUT /api/v1/ofertas/{id}': () => oferta(2, 'Producto', 'ABIERTA'),
    });
    pintar(<GestionOfertas />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByRole('button', { name: G.editar }));
    const dialogo = await screen.findByRole('dialog', { name: G.editarTitulo('Producto') });
    await waitFor(() => expect((within(dialogo).getByLabelText(G.departamento) as HTMLSelectElement).value).toBe('2'));
    await userEvent.click(within(dialogo).getByRole('button', { name: G.guardar }));

    await waitFor(() =>
      expect(llamadas.a('PUT', '/api/v1/ofertas/2').map((l) => l.cuerpo)).toEqual([
        { titulo: 'Producto', descripcion: 'Descripción', departamentoId: 2 },
      ]),
    );
  });

  it('descartar una candidatura pide comentario; la propia no se valora', async () => {
    const candidatura = (id: number, candidato: string, extra: Record<string, unknown> = {}) => ({
      id,
      ofertaId: 2,
      ofertaTitulo: 'Producto',
      usuarioId: id,
      candidato,
      carta: null,
      estado: 'RECIBIDA',
      resueltaPor: null,
      fechaResolucion: null,
      comentario: null,
      creadoEn: '2026-09-21T10:00:00Z',
      cvAdjuntoId: 40 + id,
      cvNombre: `cv-${id}.pdf`,
      puedoValorar: true,
      ...extra,
    });
    const llamadas = simularApi({
      'GET /api/v1/ofertas/gestion': () => [oferta(2, 'Producto', 'ABIERTA', { candidaturas: 2 })],
      'GET /api/v1/ofertas/{id}/candidaturas': () => [candidatura(5, 'Javier'), candidatura(6, 'Marta', { puedoValorar: false })],
      'PATCH /api/v1/candidaturas/{id}/estado': () => candidatura(5, 'Javier', { estado: 'DESCARTADA' }),
    });
    pintar(<GestionOfertas />, { sesion: sesionDe('GESTOR'), ruta: '/gestion-ofertas?oferta=2' });

    const lista = await screen.findByRole('list', { name: G.candidaturas });
    const [javier, marta] = within(lista).getAllByRole('listitem');
    expect(within(marta as HTMLElement).getByText(G.propia)).toBeTruthy();
    expect(within(marta as HTMLElement).queryByRole('button', { name: G.descartar })).toBeNull();

    await userEvent.click(within(javier as HTMLElement).getByRole('button', { name: G.descartar }));
    const dialogo = await screen.findByRole('dialog', { name: G.descartarTitulo('Javier') });
    await userEvent.click(within(dialogo).getByRole('button', { name: G.descartar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(G.comentarioVacio);
    await userEvent.type(within(dialogo).getByLabelText(G.comentario), 'Buscamos más experiencia en producto.');
    await userEvent.click(within(dialogo).getByRole('button', { name: G.descartar }));

    await waitFor(() =>
      expect(llamadas.a('PATCH', '/api/v1/candidaturas/5/estado').map((l) => l.cuerpo)).toEqual([
        { estado: 'DESCARTADA', comentario: 'Buscamos más experiencia en producto.' },
      ]),
    );
  });

  it('una oferta nueva nace en borrador y lo dice', async () => {
    const llamadas = simularApi({
      'GET /api/v1/ofertas/gestion': () => [],
      'GET /api/v1/departamentos': () => DEPARTAMENTOS,
      'POST /api/v1/ofertas': () => json(oferta(9, 'Nueva', 'BORRADOR'), 201),
    });
    pintar(<GestionOfertas />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByRole('button', { name: G.nueva }));
    const dialogo = await screen.findByRole('dialog', { name: G.nueva });
    expect(within(dialogo).getByText(G.naceEnBorrador)).toBeTruthy();
    await userEvent.type(within(dialogo).getByLabelText(G.tituloCampo), 'Diseño');
    await userEvent.type(within(dialogo).getByLabelText(G.descripcion), 'Para el equipo de la app.');
    await userEvent.click(within(dialogo).getByRole('button', { name: G.guardarBorrador }));
    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/ofertas').map((l) => l.cuerpo)).toEqual([{ titulo: 'Diseño', descripcion: 'Para el equipo de la app.' }]),
    );
  });
});

describe('canal de denuncias (quien instruye)', () => {
  const EXPEDIENTE = {
    id: 12,
    categoria: 'ACOSO',
    categoriaEtiqueta: 'Acoso laboral o sexual',
    descripcion: 'Hechos.',
    estado: 'RECIBIDA',
    anonima: false,
    denunciante: 'Ana',
    creadoEn: '2026-09-20T08:00:00Z',
    acuseReciboEn: null,
    resueltaEn: null,
    conclusion: null,
    diasHastaAcuse: -2,
    diasHastaRespuesta: 80,
    mensajes: [],
  };

  it('la bandeja con el plazo vencido en rojo; cerrar exige conclusión', async () => {
    const llamadas = simularApi({
      'GET /api/v1/denuncias': () => [{ ...EXPEDIENTE, mensajes: 0 }],
      'GET /api/v1/denuncias/{id}': () => EXPEDIENTE,
      'PATCH /api/v1/denuncias/{id}/estado': () => ({ ...EXPEDIENTE, estado: 'RESUELTA' }),
    });
    pintar(<CanalDenuncias />, { sesion: sesionDe('ADMIN') });

    const plazo = await screen.findByText(denuncias.plazoAcuseVencido(2));
    expect(plazo.className).toContain('nx-texto-error');
    await userEvent.click(screen.getByRole('button', { name: /^Abrir/ }));
    expect(await screen.findByText(/^La presentó Ana/)).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: C.resolver }));
    const dialogo = await screen.findByRole('dialog', { name: C.cerrarTitulo(C.resolver) });
    await userEvent.click(within(dialogo).getByRole('button', { name: C.resolver }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(C.conclusionVacia);
    await userEvent.type(within(dialogo).getByLabelText(denuncias.conclusion), 'Se ha hablado con las partes y se ha corregido.');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.resolver }));

    await waitFor(() =>
      expect(llamadas.a('PATCH', '/api/v1/denuncias/12/estado').map((l) => l.cuerpo)).toEqual([
        { estado: 'RESUELTA', conclusion: 'Se ha hablado con las partes y se ha corregido.' },
      ]),
    );
  });

  it('responder a una propia: el 403 del servidor se lee', async () => {
    simularApi({
      'GET /api/v1/denuncias': () => [{ ...EXPEDIENTE, mensajes: 0 }],
      'GET /api/v1/denuncias/{id}': () => EXPEDIENTE,
      'POST /api/v1/denuncias/{id}/mensajes': () => problema(403, 'Nadie instruye una denuncia que presentó él mismo.'),
    });
    pintar(<CanalDenuncias />, { sesion: sesionDe('ADMIN') });

    await userEvent.click(await screen.findByRole('button', { name: /^Abrir/ }));
    await userEvent.type(await screen.findByLabelText(C.responder), 'Acuso recibo.');
    await userEvent.click(screen.getByRole('button', { name: denuncias.enviarMensaje }));
    expect((await screen.findByRole('alert')).textContent).toBe('Nadie instruye una denuncia que presentó él mismo.');
  });
});
