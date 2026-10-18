/**
 * El canal visto por quien instruye, en lo que añade la fase J6 (ADR 048):
 * el aviso de supresión, la prórroga, la supresión de un texto, el
 * libro-registro y quién ha abierto cada expediente.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { denuncias } from '../../i18n/es/denuncias';
import { pintar, sesionDe, simularApi } from '../../pruebas/api';
import { CanalDenuncias } from './CanalDenuncias';

const D = denuncias;
const C = denuncias.canal;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const EXPEDIENTE = {
  id: 12,
  categoria: 'ACOSO',
  categoriaEtiqueta: 'Acoso laboral o sexual',
  descripcion: 'Hechos.',
  estado: 'RECIBIDA',
  anonima: true,
  creadoEn: '2026-09-20T08:00:00Z',
  acuseReciboEn: '2026-09-20T08:00:00Z',
  diasHastaRespuesta: 80,
  diasHastaSupresion: 12,
  contactoNotificaciones: 'Calle Real 1',
  mensajes: [{ id: 5, autorRol: 'DENUNCIANTE', texto: 'Tiene una enfermedad que…', creadoEn: '2026-09-21T08:00:00Z' }],
};

function bandeja() {
  // Las rutas literales antes que /{id}: el servidor de mentira se queda con la
  // primera que case, y «accesos» también casa con {id}.
  return {
    'GET /api/v1/denuncias/accesos': () => [],
    'GET /api/v1/denuncias': () => [{ ...EXPEDIENTE, mensajes: 1 }],
    'GET /api/v1/denuncias/{id}': () => EXPEDIENTE,
  } as const;
}

async function abrirElExpediente() {
  await userEvent.click(await screen.findByRole('button', { name: /^Abrir/ }));
  return screen.findByRole('dialog', { name: EXPEDIENTE.categoriaEtiqueta });
}

describe('canal de denuncias: quien instruye', () => {
  it('avisa de cuándo se suprime el contenido y enseña el acuse y el contacto', async () => {
    simularApi(bandeja());
    pintar(<CanalDenuncias />, { sesion: sesionDe('ADMIN') });

    const dialogo = await abrirElExpediente();
    expect(await within(dialogo).findByText(C.supresion(12))).toBeTruthy();
    expect(within(dialogo).getByText(D.contactoDado('Calle Real 1'))).toBeTruthy();
    expect(within(dialogo).getByText(/^Acuse de recibo:/)).toBeTruthy();
  });

  it('ampliar el plazo pide el motivo y lo manda', async () => {
    const llamadas = simularApi({
      ...bandeja(),
      'POST /api/v1/denuncias/{id}/prorroga': () => ({ ...EXPEDIENTE, respuestaProrrogadaEn: '2026-10-18T08:00:00Z' }),
    });
    pintar(<CanalDenuncias />, { sesion: sesionDe('ADMIN') });

    const expediente = await abrirElExpediente();
    await userEvent.click(await within(expediente).findByRole('button', { name: C.prorrogar }));
    const dialogo = await screen.findByRole('dialog', { name: C.prorrogaTitulo });
    await userEvent.type(within(dialogo).getByLabelText(C.prorrogaEtiqueta), 'Hay que pedir informes a tres sedes.');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.prorrogar }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/denuncias/12/prorroga').map((l) => l.cuerpo)).toEqual([
        { motivo: 'Hay que pedir informes a tres sedes.' },
      ]),
    );
  });

  it('suprimir un mensaje pide el motivo y manda cuál', async () => {
    const llamadas = simularApi({
      ...bandeja(),
      'POST /api/v1/denuncias/{id}/supresiones': () => EXPEDIENTE,
    });
    pintar(<CanalDenuncias />, { sesion: sesionDe('ADMIN') });

    const expediente = await abrirElExpediente();
    await userEvent.click(await within(expediente).findByRole('button', { name: C.suprimir }));
    const dialogo = await screen.findByRole('dialog', { name: C.suprimirTitulo });
    await userEvent.click(within(dialogo).getByRole('button', { name: C.suprimir }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/denuncias/12/supresiones').map((l) => l.cuerpo)).toEqual([
        { motivo: 'CATEGORIAS_ESPECIALES', mensajeId: 5 },
      ]),
    );
  });

  it('enseña quién ha abierto cada expediente', async () => {
    simularApi({
      ...bandeja(),
      'GET /api/v1/denuncias/accesos': () => [
        { en: '2026-10-18T09:00:00Z', quienId: 3, quien: 'Laura Gil', que: 'DENUNCIA', detalle: 'Expediente 12' },
      ],
    });
    pintar(<CanalDenuncias />, { sesion: sesionDe('ADMIN') });

    expect(await screen.findByText('Expediente 12')).toBeTruthy();
    expect(screen.getByText('Laura Gil')).toBeTruthy();
    expect(screen.getByRole('button', { name: C.libro })).toBeTruthy();
  });
});
