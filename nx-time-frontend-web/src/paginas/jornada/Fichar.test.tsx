/**
 * Mi jornada: el estado lo dice el servidor, terminar se confirma, y lo accesorio no bloquea fichar.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { T } from '../../i18n/es';
import { fichar } from '../../i18n/es/fichar';
import { pintar, problema, sesionDe, simularApi, sinContenido, type Manejador, type Ruta } from '../../pruebas/api';
import { segundosDeJornada } from './consultas';
import { Fichar } from './Fichar';
import { DialogoPausa } from './DialogoPausa';

const F = fichar;
const HACE_UNA_HORA = () => new Date(Date.now() - 3_600_000).toISOString();

function abierta(extra: Record<string, unknown> = {}) {
  return { id: 7, horaEntrada: HACE_UNA_HORA(), enPausa: false, segundosPausaAcumulados: 0, ...extra };
}

/** Lo que pide la pantalla al abrirse. Cada test cambia lo que le importa. */
function api(extra: Partial<Record<Ruta, Manejador>> = {}) {
  return simularApi({
    'GET /api/v1/fichaje/activo': () => sinContenido(),
    'GET /api/v1/fichaje/hoy': () => ({ laborable: true }),
    'GET /api/v1/fichaje/proyectos': () => ({ disponibles: [] }),
    'GET /api/v1/cuadrantes/mio': () => [],
    'GET /api/v1/dashboard/resumen': () => ({ minutosHoy: 0, minutosSemana: 0, minutosMes: 0 }),
    'GET /api/v1/dashboard/horas-por-dia': () => [],
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

describe('fichar', () => {
  it('sin jornada abierta (204), ofrece fichar la entrada', async () => {
    api();
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByRole('button', { name: F.entrar })).toBeTruthy();
    expect(screen.getByText(F.parado)).toBeTruthy();
  });

  /* Tras fichar, la pantalla enseña lo que dice el servidor al volver a preguntar. */
  it('fichar la entrada manda INICIO y vuelve a preguntar por la jornada', async () => {
    let hayJornada = false;
    const llamadas = api({
      'GET /api/v1/fichaje/activo': () => (hayJornada ? abierta() : sinContenido()),
      'POST /api/v1/fichaje': () => {
        hayJornada = true;
        return abierta();
      },
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: F.entrar }));

    expect(await screen.findByText(F.trabajando)).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/fichaje').map((l) => l.cuerpo)).toEqual([{ tipo: 'INICIO' }]);
    expect(screen.getByRole('button', { name: F.salir })).toBeTruthy();
  });

  it('un rechazo del servidor se lee con su mensaje', async () => {
    api({ 'POST /api/v1/fichaje': () => problema(409, 'Ya hay una jornada activa.') });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: F.entrar }));

    expect((await screen.findByRole('alert')).textContent).toBe('Ya hay una jornada activa.');
  });

  it('si no se puede cargar la jornada, se ofrece reintentar y reintentar funciona', async () => {
    let falla = true;
    api({ 'GET /api/v1/fichaje/activo': () => (falla ? problema(500, 'Algo ha ido mal.') : sinContenido()) });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await screen.findByText('Algo ha ido mal.');
    falla = false;
    await userEvent.click(screen.getByRole('button', { name: T.app.reintentar }));

    await waitFor(() => expect(screen.getByRole('button', { name: F.entrar })).toBeTruthy());
  });
});

/*
 * La forma exacta que manda el backend, con sus `null`. Con datos simulados
 * que omitían esos campos todo pasaba, y contra el backend de verdad la
 * pantalla se quedaba en blanco al fichar (`enCurso: null` → `null.codigo`).
 */
describe('con las respuestas tal y como llegan del backend', () => {
  it('una jornada abierta sin proyecto en curso se pinta', async () => {
    api({
      'GET /api/v1/fichaje/activo': () => ({
        id: 448,
        horaEntrada: HACE_UNA_HORA(),
        horaSalida: null,
        enPausa: false,
        minutosPausaAcumulados: 0,
        segundosPausaAcumulados: 0,
        inicioPausaActual: null,
      }),
      'GET /api/v1/fichaje/proyectos': () => ({
        disponibles: [{ id: 1, codigo: 'NX-CORE', nombre: 'Plataforma' }],
        enCurso: null,
      }),
      'GET /api/v1/fichaje/hoy': () => ({ laborable: true, motivo: null }),
      'GET /api/v1/cuadrantes/mio': () => [
        { fecha: '2026-09-27', origen: 'SIN_CUADRANTE', minutos: 0, entrada: null, tramos: [], motivo: null, plantilla: null },
      ],
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(F.trabajando)).toBeTruthy();
    expect(screen.getByText(F.proyecto.sin)).toBeTruthy();
  });
});

describe('terminar la jornada', () => {
  /* Cerrar por error cuesta una corrección aprobada; empezar por error, no. */
  it('pide confirmación, y solo manda FIN al confirmar', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/activo': () => abierta(),
      'POST /api/v1/fichaje': () => ({ ...abierta(), horaSalida: new Date().toISOString() }),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: F.salir }));
    const dialogo = await screen.findByRole('dialog', { name: F.confirmarFin.titulo });
    expect(llamadas.a('POST', '/api/v1/fichaje')).toHaveLength(0);

    await userEvent.click(within(dialogo).getByRole('button', { name: F.confirmarFin.si }));

    await waitFor(() => expect(llamadas.a('POST', '/api/v1/fichaje').map((l) => l.cuerpo)).toEqual([{ tipo: 'FIN' }]));
  });

  it('en pausa, dice que se reanude antes y no pregunta al servidor', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/activo': () => abierta({ enPausa: true, inicioPausaActual: new Date().toISOString() }),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: F.salir }));

    expect((await screen.findByRole('alert')).textContent).toBe(F.reanudaAntes);
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(llamadas.a('POST', '/api/v1/fichaje')).toHaveLength(0);
  });
});

describe('antes de iniciar', () => {
  it('un día no laborable se avisa y se pregunta, y se puede iniciar igual', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/hoy': () => ({ laborable: false, motivo: 'Festivo nacional' }),
      'POST /api/v1/fichaje': () => abierta(),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    expect((await screen.findByRole('alert')).textContent).toBe(F.noLaborable('Festivo nacional'));
    await userEvent.click(screen.getByRole('button', { name: F.entrar }));
    const dialogo = await screen.findByRole('dialog', { name: F.noLaborableDialogo.titulo });
    await userEvent.click(within(dialogo).getByRole('button', { name: F.noLaborableDialogo.si }));

    await waitFor(() => expect(llamadas.a('POST', '/api/v1/fichaje')).toHaveLength(1));
  });

  /* Fichar es la función de la app: una comprobación de cortesía no la bloquea. */
  it('si falla la pregunta de si hoy es laborable, se inicia igual', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/hoy': () => problema(500, 'Caído.'),
      'POST /api/v1/fichaje': () => abierta(),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: F.entrar }));

    await waitFor(() => expect(llamadas.a('POST', '/api/v1/fichaje')).toHaveLength(1));
  });

  it('con dos proyectos pregunta en cuál, y manda el elegido', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/proyectos': () => ({
        disponibles: [
          { id: 1, codigo: 'NX-1', nombre: 'Portal' },
          { id: 2, codigo: 'NX-2', nombre: 'App' },
        ],
      }),
      'POST /api/v1/fichaje': () => abierta(),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    await screen.findByRole('button', { name: F.entrar });
    // Espera a que los proyectos hayan llegado: con uno solo, no se preguntaría.
    await waitFor(() => expect(llamadas.a('GET', '/api/v1/fichaje/proyectos')).toHaveLength(1));
    await userEvent.click(screen.getByRole('button', { name: F.entrar }));
    const dialogo = await screen.findByRole('dialog', { name: F.proyecto.elegirTitulo });
    await userEvent.click(within(dialogo).getByLabelText('NX-2 · App'));
    await userEvent.click(within(dialogo).getByRole('button', { name: F.proyecto.empezar }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/fichaje').map((l) => l.cuerpo)).toEqual([{ tipo: 'INICIO', proyectoId: 2 }]),
    );
  });
});

describe('mi tiempo', () => {
  /* El servidor solo cuenta las cerradas: sin sumar la abierta, «Hoy» diría 0. */
  it('suma la jornada abierta a lo de hoy', async () => {
    api({
      'GET /api/v1/fichaje/activo': () => abierta(),
      'GET /api/v1/dashboard/resumen': () => ({ minutosHoy: 30, minutosSemana: 600, minutosMes: 1200 }),
    });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    const hoy = (await screen.findByText(F.resumen.hoy)).parentElement;
    await waitFor(() => expect(hoy?.textContent).toContain('1h 30m'));
  });

  it('si el resumen falla, se puede fichar igual y no hay error junto al botón', async () => {
    api({ 'GET /api/v1/dashboard/resumen': () => problema(500, 'Resumen caído.') });
    pintar(<Fichar />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByRole('button', { name: F.entrar })).toBeTruthy();
    await waitFor(() => expect(screen.queryByText('Resumen caído.')).toBeNull());
    expect(screen.queryByRole('alert')).toBeNull();
  });
});

describe('el cronómetro', () => {
  /*
   * La pausa en curso no está aún en `segundosPausaAcumulados`: contar hasta
   * «ahora» la contaría como trabajo. Es lo que arregla `inicioPausaActual`.
   */
  it('en pausa se para en el inicio de la pausa', () => {
    const jornada = {
      horaEntrada: '2026-09-21T07:00:00Z',
      enPausa: true,
      inicioPausaActual: '2026-09-21T09:00:00Z',
      segundosPausaAcumulados: 600,
    };
    const muchoDespues = new Date('2026-09-21T12:00:00Z');
    expect(segundosDeJornada(jornada, muchoDespues)).toBe(2 * 3600 - 600);
  });

  it('trabajando cuenta hasta ahora, y cerrada hasta la salida', () => {
    const ahora = new Date('2026-09-21T08:00:00Z');
    expect(segundosDeJornada({ horaEntrada: '2026-09-21T07:00:00Z', segundosPausaAcumulados: 60 }, ahora)).toBe(3540);
    expect(
      segundosDeJornada({ horaEntrada: '2026-09-21T07:00:00Z', horaSalida: '2026-09-21T07:30:00Z' }, ahora),
    ).toBe(1800);
  });
});

describe('añadir una pausa', () => {
  /* De un día pasado se pide, no se aplica: y el botón lo dice antes de pulsar. */
  it('de un día pasado se pide como corrección, en hora de España y con motivo', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/{id}/pausas': () => [],
      'POST /api/v1/fichaje/{id}/pausas': () => ({ aplicada: false }),
    });
    const cerrar = vi.fn();
    pintar(
      <DialogoPausa
        jornada={{ id: 9, horaEntrada: '2026-09-21T07:00:00Z', horaSalida: '2026-09-21T16:00:00Z' }}
        alCerrar={cerrar}
      />,
      { sesion: sesionDe('EMPLEADO') },
    );

    expect(screen.getByText(F.pausa.explicacionAprobacion)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: F.pausa.guardarAprobacion }));
    expect((await screen.findByRole('alert')).textContent).toBe(F.pausa.motivoVacio);

    await userEvent.type(screen.getByLabelText(F.pausa.motivo), 'Olvidé fichar la comida');
    await userEvent.click(screen.getByRole('button', { name: F.pausa.guardarAprobacion }));

    await waitFor(() => expect(cerrar).toHaveBeenCalled());
    // 14:00-15:00 en España, en septiembre, son las 12:00-13:00 UTC.
    expect(llamadas.a('POST', '/api/v1/fichaje/9/pausas').map((l) => l.cuerpo)).toEqual([
      { inicio: '2026-09-21T12:00:00.000Z', fin: '2026-09-21T13:00:00.000Z', motivo: 'Olvidé fichar la comida' },
    ]);
  });

  it('con la jornada abierta se aplica ya, y las pausas añadidas se pueden deshacer', async () => {
    const llamadas = api({
      'GET /api/v1/fichaje/{id}/pausas': () => [
        { id: 3, inicio: '2026-09-21T12:00:00Z', fin: '2026-09-21T12:30:00Z', minutos: 30 },
      ],
      'DELETE /api/v1/fichaje/{id}/pausas/{pausaId}': () => abierta(),
    });
    pintar(<DialogoPausa jornada={{ id: 9, horaEntrada: HACE_UNA_HORA() }} alCerrar={vi.fn()} />, {
      sesion: sesionDe('EMPLEADO'),
    });

    expect(screen.getByText(F.pausa.explicacionDirecta)).toBeTruthy();
    await userEvent.click(await screen.findByRole('button', { name: F.pausa.deshacer }));

    await waitFor(() => expect(llamadas.a('DELETE', '/api/v1/fichaje/9/pausas/3')).toHaveLength(1));
  });
});
