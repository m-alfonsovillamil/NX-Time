/**
 * Mi historial: recientes por páginas, un periodo entero con su total, y las tres acciones sobre una jornada.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { Notificaciones } from '../../componentes/Notificaciones';
import { T } from '../../i18n/es';
import { fichar } from '../../i18n/es/fichar';
import { historial } from '../../i18n/es/historial';
import { json, pintar, sesionDe, simularApi, type Manejador, type Ruta } from '../../pruebas/api';
import { hoyEnEspana, lunesDe, sumarDias } from '../../util/fechas';
import { DialogoCorreccion } from './DialogoCorreccion';
import { DialogoReparto } from './DialogoReparto';
import { Historial, minutosCerrados, rangoDe } from './Historial';

const H = historial;

/** Una jornada cerrada de 8 h con 30 min de pausa, tal y como la manda el backend. */
function cerrada(id: number, entrada: string, salida: string, pausaMin = 30) {
  return {
    id,
    horaEntrada: entrada,
    horaSalida: salida,
    enPausa: false,
    minutosPausaAcumulados: pausaMin,
    segundosPausaAcumulados: pausaMin * 60,
    inicioPausaActual: null,
  };
}

const LUNES = cerrada(1, '2026-09-21T07:00:00Z', '2026-09-21T15:30:00Z');
const MARTES = cerrada(2, '2026-09-22T07:00:00Z', '2026-09-22T15:30:00Z');
const ABIERTA = { id: 3, horaEntrada: '2026-09-23T07:00:00Z', horaSalida: null, enPausa: false, segundosPausaAcumulados: 0 };

function api(extra: Partial<Record<Ruta, Manejador>> = {}) {
  return simularApi({
    'GET /api/v1/fichaje/historial': () => ({ contenido: [ABIERTA, MARTES, LUNES], hayMas: false }),
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

describe('los recientes', () => {
  it('pintan cada jornada, y la abierta sin total ni acciones', async () => {
    api();
    pintar(<Historial />, { sesion: sesionDe('EMPLEADO') });

    const tabla = await screen.findByRole('table', { name: H.tablaTitulo });
    const filas = within(tabla).getAllByRole('row').slice(1);
    expect(filas).toHaveLength(3);

    expect(within(filas[0] as HTMLElement).getByText(H.enCurso)).toBeTruthy();
    expect(within(filas[0] as HTMLElement).queryByRole('button')).toBeNull();
    // 8 h 30 m de jornada menos 30 m de pausa.
    expect(within(filas[1] as HTMLElement).getByText('8h 00m')).toBeTruthy();
    expect(within(filas[1] as HTMLElement).getAllByRole('button').map((b) => b.textContent)).toEqual([
      H.reparto.boton,
      fichar.pausa.boton,
      H.correccion.boton,
    ]);
  });

  /* ADR 027: si entra una jornada mientras se lee, la página siguiente repite la última. */
  it('la página siguiente llega al pedirla, sin repetir la del borde', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/historial': ({ query }) =>
        query.get('pagina') === '0'
          ? { contenido: [ABIERTA, MARTES], hayMas: true }
          : { contenido: [MARTES, LUNES], hayMas: false },
    });
    pintar(<Historial />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: T.listas.cargarMas }));

    await waitFor(() => expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(4));
    expect(llamadas.a('GET', '/api/v1/fichaje/historial').map((l) => l.query.get('pagina'))).toEqual(['0', '1']);
  });

  it('sin fichajes, lo dice', async () => {
    api({ 'GET /api/v1/fichaje/historial': () => ({ contenido: [], hayMas: false }) });
    pintar(<Historial />, { sesion: sesionDe('EMPLEADO') });
    expect(await screen.findByText(H.vacioTitulo)).toBeTruthy();
  });
});

describe('un periodo', () => {
  /* La cabecera suma: con solo la primera página sería un total falso. */
  it('se pide entero, de lunes a domingo, y el total solo suma las cerradas', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/historial': ({ query }) =>
        query.get('pagina') === '0' ? { contenido: [ABIERTA, MARTES], hayMas: true } : { contenido: [LUNES], hayMas: false },
    });
    pintar(<Historial />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('tab', { name: H.semana }));

    const lunes = lunesDe(hoyEnEspana());
    await screen.findByText(/16h 00m trabajadas$/);
    const delPeriodo = llamadas.a('GET', '/api/v1/fichaje/historial').filter((l) => l.query.has('desde'));
    expect(delPeriodo.map((l) => [l.query.get('desde'), l.query.get('hasta'), l.query.get('pagina')])).toEqual([
      [lunes, sumarDias(lunes, 6), '0'],
      [lunes, sumarDias(lunes, 6), '1'],
    ]);
  });

  it('elegir más de un año se para antes de preguntar', async () => {
    const llamadas = api();
    pintar(<Historial />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('tab', { name: H.elegir }));
    await userEvent.clear(screen.getByLabelText(H.desde));
    await userEvent.type(screen.getByLabelText(H.desde), '2024-01-01');
    await userEvent.clear(screen.getByLabelText(H.hasta));
    await userEvent.type(screen.getByLabelText(H.hasta), '2026-01-01');
    await userEvent.click(screen.getByRole('button', { name: H.ver }));

    expect((await screen.findByRole('alert')).textContent).toBe(H.maxUnAnio);
    expect(llamadas.a('GET', '/api/v1/fichaje/historial').filter((l) => l.query.has('desde'))).toHaveLength(0);
  });

  it('el mes anterior de enero es diciembre del año anterior', () => {
    expect(rangoDe('mes-anterior', '2027-01-15')).toEqual(['2026-12-01', '2026-12-31']);
    expect(rangoDe('semana', '2026-09-27')).toEqual(['2026-09-21', '2026-09-27']);
  });

  it('una jornada abierta no suma', () => {
    // Ya sin los null: así llegan a la pantalla después de pasar por `cliente`.
    const lunes = { horaEntrada: LUNES.horaEntrada, horaSalida: LUNES.horaSalida, segundosPausaAcumulados: 1800 };
    expect(minutosCerrados([lunes, { horaEntrada: ABIERTA.horaEntrada }])).toBe(480);
  });
});

describe('pedir corrección', () => {
  it('parte de las horas de la jornada en hora de España y manda instantes', async () => {
    const llamadas = api({ 'POST /api/v1/fichaje/{id}/correcciones': () => json({ id: 5, estado: 'PENDIENTE' }, 202) });
    const cerrar = vi.fn();
    pintar(
      <>
        <DialogoCorreccion jornada={LUNES} alCerrar={cerrar} />
        <Notificaciones />
      </>,
      { sesion: sesionDe('EMPLEADO') },
    );

    // 07:00 UTC en septiembre son las 09:00 en España.
    expect((screen.getByLabelText(H.correccion.entrada) as HTMLInputElement).value).toBe('09:00');
    expect((screen.getByLabelText(H.correccion.salida) as HTMLInputElement).value).toBe('17:30');

    await userEvent.clear(screen.getByLabelText(H.correccion.salida));
    await userEvent.type(screen.getByLabelText(H.correccion.salida), '18:00');
    await userEvent.type(screen.getByLabelText(H.correccion.motivo), 'Me quedé a cerrar');
    await userEvent.click(screen.getByRole('button', { name: H.correccion.enviar }));

    await waitFor(() => expect(cerrar).toHaveBeenCalled());
    expect(llamadas.a('POST', '/api/v1/fichaje/1/correcciones').map((l) => l.cuerpo)).toEqual([
      { horaEntrada: '2026-09-21T07:00:00.000Z', horaSalida: '2026-09-21T16:00:00.000Z', motivo: 'Me quedé a cerrar' },
    ]);
    // 202 con estado PENDIENTE: pedida, no corregida.
    expect(await screen.findByText(H.correccion.pedida)).toBeTruthy();
  });

  /* 22:52 a 00:29: sin la casilla marcada, la salida quedaría antes de la entrada. */
  it('un turno de noche viene con «la salida es del día siguiente» marcada', () => {
    const noche = cerrada(9, '2026-09-21T20:52:00Z', '2026-09-21T22:29:00Z', 0);
    pintar(<DialogoCorreccion jornada={noche} alCerrar={vi.fn()} />, { sesion: sesionDe('EMPLEADO') });

    expect((screen.getByLabelText(H.correccion.salidaOtroDia) as HTMLInputElement).checked).toBe(true);
  });

  it('sin motivo no se manda nada', async () => {
    const llamadas = api();
    pintar(<DialogoCorreccion jornada={LUNES} alCerrar={vi.fn()} />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(screen.getByRole('button', { name: H.correccion.enviar }));

    expect((await screen.findByRole('alert')).textContent).toBe(H.correccion.motivoVacio);
    expect(llamadas.a('POST', '/api/v1/fichaje/1/correcciones')).toHaveLength(0);
  });
});

describe('repartir por proyecto', () => {
  const IMPUTACIONES = {
    fichajeId: 1,
    netoMinutos: 480,
    repartoLibre: true,
    lineas: [],
    disponibles: [
      { id: 10, codigo: 'NX-1', nombre: 'Portal' },
      { id: 11, codigo: 'NX-2', nombre: 'App' },
    ],
    solicitudPendienteId: null,
  };

  it('«Todo aquí» pone lo trabajado en un proyecto y se aplica al momento', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/{id}/imputaciones': () => IMPUTACIONES,
      'PUT /api/v1/fichaje/{id}/imputaciones': () => IMPUTACIONES,
    });
    const cerrar = vi.fn();
    pintar(<DialogoReparto fichajeId={1} alCerrar={cerrar} />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: H.reparto.todoEn('NX-2 · App') }));
    expect(screen.getByText(H.reparto.seAplicaYa)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: H.reparto.guardar }));

    await waitFor(() => expect(cerrar).toHaveBeenCalled());
    expect(llamadas.a('PUT', '/api/v1/fichaje/1/imputaciones').map((l) => l.cuerpo)).toEqual([
      { lineas: [{ proyectoId: 11, minutos: 480 }] },
    ]);
  });

  /* Sumar más de lo fichado lo tiene que aprobar un gestor, y el botón lo dice antes. */
  it('pasarse de lo trabajado pide motivo y cambia el botón a «Pedir el cambio»', async () => {
    api({ 'GET /api/v1/fichaje/{id}/imputaciones': () => IMPUTACIONES });
    pintar(<DialogoReparto fichajeId={1} alCerrar={vi.fn()} />, { sesion: sesionDe('EMPLEADO') });

    const minutosPortal = await screen.findByLabelText(H.reparto.minutos('NX-1 · Portal'));
    await userEvent.clear(minutosPortal);
    await userEvent.type(minutosPortal, '500');

    expect(screen.getByText(H.reparto.deMas('20m'))).toBeTruthy();
    const pedirCambio = screen.getByRole('button', { name: H.reparto.pedir }) as HTMLButtonElement;
    expect(pedirCambio.disabled).toBe(true);

    await userEvent.type(screen.getByLabelText(H.reparto.motivo), 'Horas de la tarde');
    expect(pedirCambio.disabled).toBe(false);
  });

  it('sin proyectos ese día, lo dice y no ofrece nada que guardar', async () => {
    api({ 'GET /api/v1/fichaje/{id}/imputaciones': () => ({ ...IMPUTACIONES, disponibles: [] }) });
    pintar(<DialogoReparto fichajeId={1} alCerrar={vi.fn()} />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(H.reparto.sinProyectos)).toBeTruthy();
    expect(screen.queryByRole('button', { name: H.reparto.guardar })).toBeNull();
  });
});
