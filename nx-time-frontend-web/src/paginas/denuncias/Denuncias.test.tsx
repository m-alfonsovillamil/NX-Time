/**
 * El canal de denuncias visto por quien denuncia.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { denuncias } from '../../i18n/es/denuncias';
import { json, pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { Denuncias, plazoDe } from './Denuncias';

const D = denuncias;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const EXPEDIENTE = {
  id: 12,
  categoria: 'SEGURIDAD',
  categoriaEtiqueta: 'Seguridad y salud en el trabajo',
  descripcion: 'La salida de emergencia del almacén está bloqueada.',
  estado: 'EN_INVESTIGACION',
  anonima: true,
  denunciante: null,
  creadoEn: '2026-09-20T08:00:00Z',
  acuseReciboEn: '2026-09-21T08:00:00Z',
  resueltaEn: null,
  conclusion: null,
  diasHastaAcuse: null,
  diasHastaRespuesta: 80,
  mensajes: [
    { id: 1, autorRol: 'INSTRUCTOR', autor: null, texto: 'Recibida. ¿Desde cuándo?', creadoEn: '2026-09-21T08:00:00Z' },
  ],
};

describe('el plazo que corre', () => {
  it('primero el acuse, luego la respuesta; vencido si es negativo', () => {
    expect(plazoDe({ diasHastaAcuse: 3 })).toEqual({ texto: D.plazoAcuse(3), vencido: false });
    expect(plazoDe({ diasHastaAcuse: -2, diasHastaRespuesta: 80 })).toEqual({ texto: D.plazoAcuseVencido(2), vencido: true });
    expect(plazoDe({ diasHastaRespuesta: -5 })).toEqual({ texto: D.plazoRespuestaVencido(5), vencido: true });
    expect(plazoDe({})).toBeNull();
  });
});

describe('canal de denuncias', () => {
  it('marcar «anónima» cambia lo que se explica antes de enviar', async () => {
    simularApi({ 'GET /api/v1/denuncias/mias': () => [] });
    pintar(<Denuncias />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(D.anonimaNo)).toBeTruthy();
    await userEvent.click(screen.getByLabelText(D.anonima));
    expect(screen.getByText(D.anonimaSi)).toBeTruthy();
  });

  it('sin descripción no se envía', async () => {
    const llamadas = simularApi({ 'GET /api/v1/denuncias/mias': () => [] });
    pintar(<Denuncias />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: D.enviar }));
    expect(await screen.findByText(D.sinDescripcion)).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/denuncias')).toEqual([]);
  });

  /* El código se ve una vez y solo se sale confirmando que se ha guardado. */
  it('una anónima enseña el código con el aviso del servidor, y se va al confirmar', async () => {
    const llamadas = simularApi({
      'GET /api/v1/denuncias/mias': () => [],
      'POST /api/v1/denuncias': () =>
        json(
          {
            codigoSeguimiento: 'K7Q2-9XMA-3PLD',
            anonima: true,
            creadoEn: '2026-09-27T10:00:00Z',
            avisoImportante: 'Este código no se puede recuperar.',
          },
          201,
        ),
    });
    pintar(<Denuncias />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.selectOptions(await screen.findByLabelText(D.categoria), 'SEGURIDAD');
    await userEvent.type(screen.getByLabelText(D.descripcion), 'La salida de emergencia está bloqueada');
    await userEvent.click(screen.getByLabelText(D.anonima));
    await userEvent.click(screen.getByRole('button', { name: D.enviar }));

    const dialogo = await screen.findByRole('dialog', { name: D.codigoTitulo });
    expect(within(dialogo).getByText('K7Q2-9XMA-3PLD')).toBeTruthy();
    expect(within(dialogo).getByText('Este código no se puede recuperar.')).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/denuncias').map((l) => l.cuerpo)).toEqual([
      { categoria: 'SEGURIDAD', descripcion: 'La salida de emergencia está bloqueada', anonima: true },
    ]);

    await userEvent.click(within(dialogo).getByRole('button', { name: D.codigoGuardado }));
    await waitFor(() => expect(screen.queryByText('K7Q2-9XMA-3PLD')).toBeNull());
  });

  it('seguir por código abre el expediente y responde por el mismo código', async () => {
    const llamadas = simularApi({
      'GET /api/v1/denuncias/mias': () => [],
      'GET /api/v1/denuncias/seguimiento/{codigo}': () => EXPEDIENTE,
      'POST /api/v1/denuncias/seguimiento/{codigo}/mensajes': ({ cuerpo }) => ({
        ...EXPEDIENTE,
        mensajes: [
          ...EXPEDIENTE.mensajes,
          { id: 2, autorRol: 'DENUNCIANTE', autor: null, texto: (cuerpo as { texto: string }).texto, creadoEn: '2026-09-27T10:00:00Z' },
        ],
      }),
    });
    pintar(<Denuncias />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.type(await screen.findByLabelText(D.codigo), 'K7Q2-9XMA-3PLD');
    await userEvent.click(screen.getByRole('button', { name: D.buscar }));

    const dialogo = await screen.findByRole('dialog', { name: EXPEDIENTE.categoriaEtiqueta });
    expect(within(dialogo).getByText('Recibida. ¿Desde cuándo?')).toBeTruthy();
    expect(within(dialogo).getByText(D.plazoRespuesta(80))).toBeTruthy();
    // El campo se vacía: el código no se queda a la vista.
    expect((screen.getByLabelText(D.codigo) as HTMLInputElement).value).toBe('');

    await userEvent.type(within(dialogo).getByLabelText(D.responder), 'Desde el lunes');
    await userEvent.click(within(dialogo).getByRole('button', { name: D.enviarMensaje }));

    expect(await within(dialogo).findByText('Desde el lunes')).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/denuncias/seguimiento/K7Q2-9XMA-3PLD/mensajes').map((l) => l.cuerpo)).toEqual([
      { texto: 'Desde el lunes' },
    ]);
  });

  it('un código que no existe dice lo que diga el servidor', async () => {
    simularApi({
      'GET /api/v1/denuncias/mias': () => [],
      'GET /api/v1/denuncias/seguimiento/{codigo}': () => problema(404, 'No hay ninguna denuncia con ese código.'),
    });
    pintar(<Denuncias />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.type(await screen.findByLabelText(D.codigo), 'NO-EXISTE');
    await userEvent.click(screen.getByRole('button', { name: D.buscar }));
    expect((await screen.findByRole('alert')).textContent).toBe('No hay ninguna denuncia con ese código.');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('las mías se abren por su id, y una cerrada ya no admite mensajes', async () => {
    const llamadas = simularApi({
      'GET /api/v1/denuncias/mias': () => [
        {
          id: 12,
          categoria: 'SEGURIDAD',
          categoriaEtiqueta: 'Seguridad y salud en el trabajo',
          estado: 'RESUELTA',
          anonima: false,
          creadoEn: '2026-09-20T08:00:00Z',
          acuseReciboEn: '2026-09-21T08:00:00Z',
          diasHastaAcuse: null,
          diasHastaRespuesta: null,
          mensajes: 1,
        },
      ],
      'GET /api/v1/denuncias/mias/{id}': () => ({
        ...EXPEDIENTE,
        anonima: false,
        estado: 'RESUELTA',
        diasHastaRespuesta: null,
        conclusion: 'Se ha despejado la salida y se revisa cada semana.',
      }),
    });
    pintar(<Denuncias />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(D.mensajesCuenta(1))).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: D.ver }));

    const dialogo = await screen.findByRole('dialog', { name: EXPEDIENTE.categoriaEtiqueta });
    expect(within(dialogo).getByText('Se ha despejado la salida y se revisa cada semana.')).toBeTruthy();
    expect(within(dialogo).getByText(D.cerrado)).toBeTruthy();
    expect(within(dialogo).queryByLabelText(D.responder)).toBeNull();
    expect(llamadas.a('GET', '/api/v1/denuncias/mias/12')).toHaveLength(1);
  });
});
