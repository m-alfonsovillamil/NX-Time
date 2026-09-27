/**
 * Horas extra y correcciones.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { revisiones } from '../../i18n/es/revisiones';
import { pintar, problema, sesionDe, simularApi } from '../../pruebas/api';
import { fechaCorta } from '../../util/fechas';
import { Correcciones, cambiosDe } from './Correcciones';
import { HorasExtra, periodoDe } from './HorasExtra';

const H = revisiones.horasExtra;
const C = revisiones.correcciones;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const BOLSA = { anio: 2026, minutosTope: 4800, minutosConsumidos: 2880, minutosDisponibles: 1920, avisosAbiertos: 2, alLimite: false };

const AVISO = {
  id: 5,
  usuarioId: 2,
  usuario: 'Javier',
  tipo: 'DIARIA',
  fecha: '2026-09-21',
  fechaFin: null,
  minutosExtra: 95,
  minutosEsperados: 480,
  registroId: 40,
  estado: 'ABIERTO',
  justificacion: null,
  revisadoPor: null,
  fechaRevision: null,
};

describe('horas extra', () => {
  it('un aviso semanal dice la semana entera, no solo el lunes', () => {
    expect(periodoDe({ tipo: 'SEMANAL', fecha: '2026-09-14', fechaFin: '2026-09-20' })).toBe(
      H.periodoSemana(fechaCorta('2026-09-14'), fechaCorta('2026-09-20')),
    );
    expect(periodoDe({ tipo: 'DIARIA', fecha: '2026-09-21' })).toBe(H.periodoDia(fechaCorta('2026-09-21')));
  });

  /* El exceso y el listón siempre juntos: «1h 35m» a secas no se puede juzgar. */
  it('enseña la bolsa y cada exceso con su listón; en los míos no hay botones', async () => {
    simularApi({
      'GET /api/v1/horas-extra/bolsa': () => BOLSA,
      'GET /api/v1/horas-extra': () => [AVISO],
    });
    pintar(<HorasExtra />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(H.exceso('1h 35m', '8h 00m'))).toBeTruthy();
    expect(await screen.findByText(H.bolsaDetalle('32h 00m', 2))).toBeTruthy();
    expect(screen.getByRole('meter').getAttribute('aria-valuenow')).toBe('2880');
    expect(screen.queryByRole('button', { name: H.aceptar })).toBeNull();
    expect(screen.queryByRole('tab', { name: H.delEquipo })).toBeNull();
  });

  it('con la bolsa al límite, lo avisa', async () => {
    simularApi({
      'GET /api/v1/horas-extra/bolsa': () => ({ ...BOLSA, minutosConsumidos: 4700, minutosDisponibles: 100, alLimite: true }),
      'GET /api/v1/horas-extra': () => [],
    });
    pintar(<HorasExtra />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(H.alLimite)).toBeTruthy();
    expect(screen.getByText(H.vacio)).toBeTruthy();
  });

  it('en el equipo, «no computan» exige motivo y «son horas extra» no', async () => {
    const llamadas = simularApi({
      'GET /api/v1/horas-extra/bolsa': () => BOLSA,
      'GET /api/v1/horas-extra': () => [],
      'GET /api/v1/horas-extra/equipo': () => [AVISO, { ...AVISO, id: 6 }, { ...AVISO, id: 7, estado: 'ACEPTADO', revisadoPor: 'Marta' }],
      'PATCH /api/v1/horas-extra/{id}': () => ({ ...AVISO, estado: 'ACEPTADO' }),
    });
    pintar(<HorasExtra />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByRole('tab', { name: H.delEquipo }));
    const [primero, segundo, revisado] = await screen.findAllByRole('listitem');
    // El ya revisado no ofrece nada que decidir.
    expect(within(revisado as HTMLElement).queryByRole('button')).toBeNull();
    expect(within(revisado as HTMLElement).getByText(H.revisadoPor('Marta'))).toBeTruthy();

    await userEvent.click(within(primero as HTMLElement).getByRole('button', { name: H.aceptar }));
    await userEvent.click(within(segundo as HTMLElement).getByRole('button', { name: H.justificar }));
    const dialogo = await screen.findByRole('dialog', { name: H.justificarTitulo });
    await userEvent.click(within(dialogo).getByRole('button', { name: H.justificar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(H.motivoVacio);
    await userEvent.type(within(dialogo).getByLabelText(H.motivo), 'Jornada intensiva pactada');
    await userEvent.click(within(dialogo).getByRole('button', { name: H.justificar }));

    await waitFor(() =>
      expect(llamadas.llamadas.filter((l) => l.metodo === 'PATCH').map((l) => [l.ruta, l.cuerpo])).toEqual([
        ['/api/v1/horas-extra/5', { aceptar: true }],
        ['/api/v1/horas-extra/6', { aceptar: false, justificacion: 'Jornada intensiva pactada' }],
      ]),
    );
  });
});

const CORRECCION = {
  id: 9,
  fichajeId: 40,
  empleado: { nombre: 'Javier' },
  solicitante: { nombre: 'Javier' },
  horaEntradaActual: '2026-09-21T07:00:00Z',
  horaSalidaActual: '2026-09-21T15:00:00Z',
  horaEntradaPropuesta: '2026-09-21T06:30:00Z',
  horaSalidaPropuesta: '2026-09-21T15:00:00Z',
  pausaInicioPropuesta: null,
  pausaFinPropuesta: null,
  motivo: 'Se me olvidó fichar al llegar',
  estado: 'PENDIENTE',
  comentarioResolucion: null,
  motivoDisputa: null,
  puedoResolver: true,
  puedoDisputar: false,
  repartoPropuesto: [],
};

describe('correcciones', () => {
  /* Una que solo añade pausa no dice «De 09:00 a 09:00»: parecería que no cambia nada. */
  it('solo enseña las horas si cambian, y la pausa si la hay', () => {
    // Lo que llega a la página ya sin los null (los quita `sinNulos`).
    const horas = {
      horaEntradaActual: CORRECCION.horaEntradaActual,
      horaSalidaActual: CORRECCION.horaSalidaActual,
      horaEntradaPropuesta: CORRECCION.horaEntradaPropuesta,
      horaSalidaPropuesta: CORRECCION.horaSalidaPropuesta,
    };
    expect(cambiosDe(horas)).toEqual([C.deA('09:00–17:00 h', '08:30–17:00 h')]);
    expect(
      cambiosDe({
        ...horas,
        horaEntradaPropuesta: CORRECCION.horaEntradaActual,
        pausaInicioPropuesta: '2026-09-21T10:00:00Z',
        pausaFinPropuesta: '2026-09-21T10:30:00Z',
      }),
    ).toEqual([C.soloPausa('12:00 h', '12:30 h')]);
    expect(cambiosDe({ ...horas, repartoPropuesto: [{ codigo: 'WEB', minutos: 90 }] })).toContain(C.reparto('WEB 1h 30m'));
  });

  it('dice quién pide qué', async () => {
    simularApi({
      'GET /api/v1/correcciones/pendientes': () => [
        CORRECCION,
        { ...CORRECCION, id: 10, solicitante: { nombre: 'Marta' }, puedoResolver: false, puedoDisputar: true },
      ],
    });
    pintar(<Correcciones />, { sesion: sesionDe('GESTOR') });

    expect(await screen.findByText(C.pideElSuyo('Javier'))).toBeTruthy();
    expect(screen.getByText(C.quienPide('Marta', 'Javier'))).toBeTruthy();
  });

  it('aprobar va directo; rechazar y «no estoy de acuerdo» piden motivo', async () => {
    const llamadas = simularApi({
      'GET /api/v1/correcciones/pendientes': () => [
        CORRECCION,
        { ...CORRECCION, id: 10 },
        { ...CORRECCION, id: 11, solicitante: { nombre: 'Marta' }, puedoResolver: false, puedoDisputar: true },
      ],
      'PATCH /api/v1/correcciones/{id}/estado': () => ({ ...CORRECCION, estado: 'APROBADA' }),
      'POST /api/v1/correcciones/{id}/disputa': () => ({ ...CORRECCION, estado: 'EN_DISPUTA' }),
    });
    pintar(<Correcciones />, { sesion: sesionDe('GESTOR') });

    const [primera, segunda, tercera] = await screen.findAllByRole('listitem');
    // Quien solo puede disputar no ve aprobar ni rechazar.
    expect(within(tercera as HTMLElement).queryByRole('button', { name: C.aprobar })).toBeNull();

    await userEvent.click(within(primera as HTMLElement).getByRole('button', { name: C.aprobar }));

    await userEvent.click(within(segunda as HTMLElement).getByRole('button', { name: C.rechazar }));
    let dialogo = await screen.findByRole('dialog', { name: C.rechazarTitulo });
    await userEvent.click(within(dialogo).getByRole('button', { name: C.rechazar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(C.textoVacio);
    await userEvent.type(within(dialogo).getByLabelText(C.texto), 'No consta en el control de acceso');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.rechazar }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());

    await userEvent.click(within(tercera as HTMLElement).getByRole('button', { name: C.disputar }));
    dialogo = await screen.findByRole('dialog', { name: C.disputarTitulo });
    await userEvent.type(within(dialogo).getByLabelText(C.texto), 'Entré a las 9, no a las 8:30');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.enviar }));

    await waitFor(() =>
      expect(
        llamadas.llamadas.filter((l) => l.metodo !== 'GET').map((l) => [l.metodo, l.ruta, l.cuerpo]),
      ).toEqual([
        ['PATCH', '/api/v1/correcciones/9/estado', { aprobada: true }],
        ['PATCH', '/api/v1/correcciones/10/estado', { aprobada: false, comentario: 'No consta en el control de acceso' }],
        ['POST', '/api/v1/correcciones/11/disputa', { motivo: 'Entré a las 9, no a las 8:30' }],
      ]),
    );
  });

  it('si el servidor no deja aprobar, se lee su motivo', async () => {
    simularApi({
      'GET /api/v1/correcciones/pendientes': () => [CORRECCION],
      'PATCH /api/v1/correcciones/{id}/estado': () => problema(409, 'La corrección ya estaba resuelta.'),
    });
    pintar(<Correcciones />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByRole('button', { name: C.aprobar }));
    expect((await screen.findByRole('alert')).textContent).toBe('La corrección ya estaba resuelta.');
  });

  it('las que he pedido enseñan la respuesta', async () => {
    simularApi({
      'GET /api/v1/correcciones/pendientes': () => [],
      'GET /api/v1/correcciones/mias': () => ({
        contenido: [{ ...CORRECCION, estado: 'RECHAZADA', comentarioResolucion: 'Falta el justificante', puedoResolver: false }],
        hayMas: false,
      }),
    });
    pintar(<Correcciones />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(C.vacioEsperan)).toBeTruthy();
    await userEvent.click(screen.getByRole('tab', { name: C.pedidasPorMi }));
    expect(await screen.findByText(C.comentario('Falta el justificante'))).toBeTruthy();
    expect(screen.getByText(C.estados.RECHAZADA as string)).toBeTruthy();
  });
});
