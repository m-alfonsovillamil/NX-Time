/**
 * El editor de cuadrantes: plantillas, el cuadrante de una persona y la semana del equipo.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { cuadrantes } from '../../i18n/es/cuadrantes';
import { json, pintar, sesionDe, simularApi } from '../../pruebas/api';
import { hoyEnEspana } from '../../util/fechas';
import { Cuadrantes, textoDelDia } from './Cuadrantes';

const P = cuadrantes.plantilla;
const C = cuadrantes.persona;
const E = cuadrantes.equipo;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const EMPLEADOS = [
  { id: 2, nombre: 'Javier', email: 'j@x', activo: true, horasSemanales: 40, diasVacaciones: 22, departamentoId: null, departamentoNombre: null, rol: 'EMPLEADO' },
];

function tramo(diaSemana: number, inicio: number, fin: number) {
  const hora = (m: number) => `${String(Math.floor((m % 1440) / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  return { diaSemana, inicio, fin, horaInicio: hora(inicio), horaFin: hora(fin), minutos: fin - inicio, cruzaMedianoche: fin > 1440 };
}

const NOCHE = {
  id: 7,
  nombre: 'Noche',
  descripcion: null,
  minutosSemanales: 480,
  tramosEditables: true,
  borrable: true,
  tramos: [tramo(1, 1320, 1800)],
};

describe('plantillas', () => {
  it('crear una con turno de noche manda un solo tramo que pasa de 1440', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/cuadrantes/plantillas': () => [],
      'POST /api/v1/cuadrantes/plantillas': () => json(NOCHE, 201),
    });
    pintar(<Cuadrantes />, { sesion: sesionDe('GESTOR'), ruta: '/cuadrantes?ver=plantillas' });

    await userEvent.click(await screen.findByRole('button', { name: P.nueva }));
    const dialogo = await screen.findByRole('dialog', { name: P.nueva });
    await userEvent.type(within(dialogo).getByLabelText(P.nombre), 'Noche');
    await userEvent.click(within(dialogo).getByRole('button', { name: `${P.anadirTramo} (lunes)` }));
    const desde = within(dialogo).getByLabelText(P.desde);
    const hasta = within(dialogo).getByLabelText(P.hasta);
    await userEvent.clear(desde);
    await userEvent.type(desde, '22:00');
    await userEvent.clear(hasta);
    await userEvent.type(hasta, '06:00');
    // Sin marcar «al día siguiente», acaba antes de empezar: se dice en el acto.
    expect((await within(dialogo).findByRole('alert')).textContent).toMatch(/^Lunes: un tramo tiene que terminar después de empezar/);
    await userEvent.click(within(dialogo).getByLabelText(P.alDiaSiguiente));
    expect(within(dialogo).queryByRole('alert')).toBeNull();
    expect(within(dialogo).getByText(P.total('8h 00m'))).toBeTruthy();
    await userEvent.click(within(dialogo).getByRole('button', { name: P.guardar }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/cuadrantes/plantillas').map((l) => l.cuerpo)).toEqual([
        { nombre: 'Noche', tramos: [{ diaSemana: 1, inicio: 1320, fin: 1800 }] },
      ]),
    );
  });

  it('una ya aplicada a días pasados solo se renombra: tramos bloqueados, y se mandan los mismos', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/cuadrantes/plantillas': () => [{ ...NOCHE, tramosEditables: false, borrable: false }],
      'PUT /api/v1/cuadrantes/plantillas/{plantillaId}': () => NOCHE,
    });
    pintar(<Cuadrantes />, { sesion: sesionDe('GESTOR'), ruta: '/cuadrantes?ver=plantillas' });

    // No borrable: el botón no sale.
    expect(await screen.findByRole('button', { name: P.editar })).toBeTruthy();
    expect(screen.queryByRole('button', { name: P.borrar })).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: P.editar }));
    const dialogo = await screen.findByRole('dialog', { name: P.editarTitulo('Noche') });
    expect(within(dialogo).getByText(P.tramosBloqueados)).toBeTruthy();
    expect((within(dialogo).getByLabelText(P.desde) as HTMLInputElement).disabled).toBe(true);
    const nombre = within(dialogo).getByLabelText(P.nombre);
    await userEvent.clear(nombre);
    await userEvent.type(nombre, 'Noche fija');
    await userEvent.click(within(dialogo).getByRole('button', { name: P.guardar }));

    await waitFor(() =>
      expect(llamadas.a('PUT', '/api/v1/cuadrantes/plantillas/7').map((l) => l.cuerpo)).toEqual([
        { nombre: 'Noche fija', tramos: [{ diaSemana: 1, inicio: 1320, fin: 1800 }] },
      ]),
    );
  });
});

describe('el cuadrante de una persona', () => {
  function rutas(extra: Record<string, unknown> = {}) {
    return {
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/cuadrantes/usuarios/{usuarioId}': () => [],
      'GET /api/v1/cuadrantes/usuarios/{usuarioId}/asignaciones': () => [],
      'GET /api/v1/cuadrantes/usuarios/{usuarioId}/excepciones': () => [],
      'GET /api/v1/cuadrantes/plantillas': () => [NOCHE],
      ...extra,
    } as Parameters<typeof simularApi>[0];
  }

  /* El aviso de jornada lo da el servidor, y se queda a la vista. */
  it('asignar desde hoy y enseñar el aviso de que no suma la jornada', async () => {
    const llamadas = simularApi(
      rutas({
        'POST /api/v1/cuadrantes/asignaciones': () =>
          json({ id: 1, usuarioId: 2, plantillaId: 7, fechaInicio: hoyEnEspana(), aviso: 'La plantilla suma 8h 00m y su contrato 40h 00m a la semana.' }, 201),
      }),
    );
    pintar(<Cuadrantes />, { sesion: sesionDe('GESTOR'), ruta: '/cuadrantes?ver=persona&persona=2' });

    await userEvent.click(await screen.findByRole('button', { name: C.asignar }));
    const dialogo = await screen.findByRole('dialog', { name: C.asignarTitulo('Javier') });
    await waitFor(() => expect(within(dialogo).getByRole('option', { name: 'Noche' })).toBeTruthy());
    await userEvent.selectOptions(within(dialogo).getByLabelText(C.plantilla), '7');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.asignar }));

    expect(await screen.findByText('La plantilla suma 8h 00m y su contrato 40h 00m a la semana.')).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/cuadrantes/asignaciones').map((l) => l.cuerpo)).toEqual([
      { usuarioId: 2, plantillaId: 7, fechaInicio: hoyEnEspana() },
    ]);
  });

  it('una excepción con otro horario manda sus tramos del día', async () => {
    const llamadas = simularApi(rutas({ 'POST /api/v1/cuadrantes/excepciones': () => json({ id: 3 }, 201) }));
    pintar(<Cuadrantes />, { sesion: sesionDe('GESTOR'), ruta: '/cuadrantes?ver=persona&persona=2' });

    await userEvent.click(await screen.findByRole('button', { name: C.anadirExcepcion }));
    const dialogo = await screen.findByRole('dialog', { name: C.excepcionTitulo('Javier') });
    await userEvent.selectOptions(within(dialogo).getByLabelText(C.tipo), 'TRAMO');
    await userEvent.type(within(dialogo).getByLabelText(C.motivo), 'Formación');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.anadirExcepcion }));

    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/cuadrantes/excepciones').map((l) => l.cuerpo)).toEqual([
        { usuarioId: 2, fecha: hoyEnEspana(), tipo: 'TRAMO', tramos: [{ inicio: 540, fin: 840 }], motivo: 'Formación' },
      ]),
    );
  });

  it('una asignación que aún no ha empezado se borra; una en vigor se cierra', async () => {
    simularApi(
      rutas({
        'GET /api/v1/cuadrantes/usuarios/{usuarioId}/asignaciones': () => [
          { id: 1, usuarioId: 2, plantillaNombre: 'Futura', fechaInicio: '2999-01-01', vigente: false },
          { id: 2, usuarioId: 2, plantillaNombre: 'Actual', fechaInicio: '2026-01-01', vigente: true },
        ],
      }),
    );
    pintar(<Cuadrantes />, { sesion: sesionDe('GESTOR'), ruta: '/cuadrantes?ver=persona&persona=2' });

    const lista = await screen.findByRole('list', { name: C.asignaciones });
    const [futura, actual] = within(lista).getAllByRole('listitem');
    expect(within(futura as HTMLElement).getByRole('button', { name: C.deshacer })).toBeTruthy();
    expect(within(futura as HTMLElement).queryByRole('button', { name: C.cerrar })).toBeNull();
    expect(within(actual as HTMLElement).getByRole('button', { name: C.cerrar })).toBeTruthy();
    expect(within(actual as HTMLElement).queryByRole('button', { name: C.deshacer })).toBeNull();
  });
});

describe('la semana del equipo', () => {
  it('cada celda dice lo que toca: horario, libre, no laborable o excepción', () => {
    expect(textoDelDia({ origen: 'CUADRANTE', tramos: [tramo(1, 540, 840)] })).toEqual({ texto: '09:00–14:00', marca: null });
    expect(textoDelDia({ origen: 'NO_LABORABLE', motivo: 'Fiesta Nacional' })).toEqual({ texto: 'Fiesta Nacional', marca: 'no-laborable' });
    expect(textoDelDia({ origen: 'CUADRANTE', tramos: [] })).toEqual({ texto: E.libre, marca: 'libre' });
    expect(textoDelDia({ origen: 'EXCEPCION', tramos: [] }).marca).toBe('excepcion');
  });

  it('una fila por persona con cuadrante, y su nombre lleva a su cuadrante', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/mis-empleados': () => EMPLEADOS,
      'GET /api/v1/cuadrantes/equipo': ({ query }) => [
        { usuarioId: 2, nombre: 'Javier', dia: { fecha: query.get('fecha'), origen: 'CUADRANTE', minutos: 300, tramos: [tramo(1, 540, 840)] } },
      ],
      'GET /api/v1/cuadrantes/usuarios/{usuarioId}': () => [],
      'GET /api/v1/cuadrantes/usuarios/{usuarioId}/asignaciones': () => [],
      'GET /api/v1/cuadrantes/usuarios/{usuarioId}/excepciones': () => [],
    });
    pintar(<Cuadrantes />, { sesion: sesionDe('GESTOR') });

    expect((await screen.findAllByText('09:00–14:00')).length).toBe(7);
    expect(llamadas.a('GET', '/api/v1/cuadrantes/equipo')).toHaveLength(7);
    await userEvent.click(screen.getByRole('button', { name: E.ver('Javier') }));
    expect(await screen.findByRole('heading', { name: C.asignaciones })).toBeTruthy();
  });
});
