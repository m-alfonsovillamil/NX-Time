/**
 * Ofertas internas y mis candidaturas.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { ofertas } from '../../i18n/es/ofertas';
import { json, pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { fechaCorta } from '../../util/fechas';
import { Ofertas, PaginaMisCandidaturas, plazoDe } from './Ofertas';

const O = ofertas;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const OFERTA = {
  id: 3,
  titulo: 'Desarrollador/a Android',
  descripcion: 'Equipo de la app.\nJornada completa.',
  puesto: 'Desarrollo',
  departamento: 'Tecnología',
  publicadaPor: 'Marta',
  estado: 'ABIERTA',
  fechaPublicacion: '2026-09-20T08:00:00Z',
  fechaCierre: '2026-10-15',
  admiteCandidaturas: true,
  plazoVencido: false,
  yaMePresente: false,
  candidaturas: null,
};

describe('ofertas internas', () => {
  it('el plazo: hasta cuándo, terminado o sin fecha', () => {
    expect(plazoDe({ fechaCierre: '2026-10-15' })).toBe(O.plazoHasta(fechaCorta('2026-10-15')));
    expect(plazoDe({ fechaCierre: '2026-09-01', plazoVencido: true })).toBe(O.plazoTerminado);
    expect(plazoDe({})).toBe(O.sinPlazo);
  });

  it('presentarse manda la carta y explica que el CV lo pone el servidor', async () => {
    const llamadas = simularApi({
      'GET /api/v1/ofertas': () => [OFERTA],
      'POST /api/v1/ofertas/{id}/candidaturas': () => json({ id: 1, estado: 'RECIBIDA' }, 201),
    });
    pintar(<Ofertas />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: O.presentar }));
    const dialogo = await screen.findByRole('dialog', { name: O.presentarTitulo(OFERTA.titulo) });
    expect(within(dialogo).getByText(O.cvAutomatico)).toBeTruthy();
    await userEvent.type(within(dialogo).getByLabelText(O.carta), 'Llevo dos años con Compose');
    await userEvent.click(within(dialogo).getByRole('button', { name: O.presentar }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/ofertas/3/candidaturas').map((l) => l.cuerpo)).toEqual([
        { carta: 'Llevo dos años con Compose' },
      ]),
    );
  });

  /* 400 sin CV: el servidor lo redacta, y el diálogo lleva al perfil para arreglarlo. */
  it('sin CV, se lee el motivo del servidor y hay un camino al perfil', async () => {
    simularApi({
      'GET /api/v1/ofertas': () => [OFERTA],
      'POST /api/v1/ofertas/{id}/candidaturas': () => problema(400, 'Para presentarte necesitas tener un CV subido en tu perfil.'),
    });
    pintar(<Ofertas />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: O.presentar }));
    const dialogo = await screen.findByRole('dialog');
    await userEvent.click(within(dialogo).getByRole('button', { name: O.presentar }));

    expect((await within(dialogo).findByRole('alert')).textContent).toBe('Para presentarte necesitas tener un CV subido en tu perfil.');
    expect(within(dialogo).getByRole('link', { name: O.irAlPerfil }).getAttribute('href')).toBe('/perfil');
  });

  it('una a la que ya me presenté, o con el plazo vencido, no ofrece presentarse', async () => {
    simularApi({
      'GET /api/v1/ofertas': () => [
        { ...OFERTA, yaMePresente: true, admiteCandidaturas: false },
        { ...OFERTA, id: 4, titulo: 'Otra', plazoVencido: true, admiteCandidaturas: false },
      ],
    });
    pintar(<Ofertas />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(O.yaPresentadoDetalle)).toBeTruthy();
    expect(screen.getAllByText(O.plazoTerminado).length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: O.presentar })).toBeNull();
  });

  it('/mis-candidaturas abre directamente las mías, con su valoración', async () => {
    simularApi({
      'GET /api/v1/candidaturas/mias': () => [
        {
          id: 1,
          ofertaId: 3,
          ofertaTitulo: 'Desarrollador/a Android',
          estado: 'DESCARTADA',
          comentario: 'Buscamos a alguien con más experiencia en iOS',
          resueltaPor: 'Marta',
          creadoEn: '2026-09-21T10:00:00Z',
          cvNombre: 'cv-javier.pdf',
          carta: null,
          fechaResolucion: null,
          cvAdjuntoId: 7,
          puedoValorar: false,
        },
      ],
    });
    pintar(<PaginaMisCandidaturas />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(O.comentario('Buscamos a alguien con más experiencia en iOS'))).toBeTruthy();
    expect(screen.getByText(O.cvAdjunto('cv-javier.pdf'))).toBeTruthy();
    expect(screen.getByRole('tab', { name: O.misCandidaturas }).getAttribute('aria-selected')).toBe('true');
  });
});
