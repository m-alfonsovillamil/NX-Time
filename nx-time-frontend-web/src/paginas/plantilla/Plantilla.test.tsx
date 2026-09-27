/**
 * La plantilla y los departamentos.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { plantilla } from '../../i18n/es/plantilla';
import { json, pintar, problema, sesionDe, sinContenido, simularApi } from '../../pruebas/api';
import { Departamentos } from './Departamentos';
import { Plantilla, filtrar, horasValidas } from './Plantilla';

const P = plantilla;
const D = plantilla.departamentos;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function persona(id: number, nombre: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    nombre,
    email: `${nombre.toLowerCase()}@techcorp.demo`,
    activo: true,
    horasSemanales: 40,
    diasVacaciones: 22,
    departamentoId: null,
    departamentoNombre: null,
    rol: 'EMPLEADO',
    ...extra,
  };
}

const PLANTILLA = [
  persona(2, 'Javier', { departamentoId: 1, departamentoNombre: 'Desarrollo' }),
  persona(5, 'Marta', { rol: 'GESTOR' }),
  persona(7, 'Pedro', { rol: 'GESTOR', activo: false }),
];

describe('reglas de la plantilla', () => {
  it('las horas: con coma o punto, más de 0, hasta 60 y un decimal', () => {
    expect(horasValidas('37,5')).toBe(37.5);
    expect(horasValidas('40')).toBe(40);
    expect(horasValidas('0')).toBeNull();
    expect(horasValidas('61')).toBeNull();
    expect(horasValidas('37,25')).toBeNull();
  });

  it('buscar por nombre o correo; quien está de baja solo si se pide', () => {
    const lista = PLANTILLA as never[];
    expect(filtrar(lista, '', false).map((p: { nombre?: string }) => p.nombre)).toEqual(['Javier', 'Marta']);
    expect(filtrar(lista, 'pedro', true)).toHaveLength(1);
    expect(filtrar(lista, 'TECHCORP', false)).toHaveLength(2);
  });
});

describe('plantilla', () => {
  /* RRHH ve a todos los roles: la app solo listaba empleados. */
  it('RRHH ve la plantilla entera, con su rol, y quien está de baja no sale por defecto', async () => {
    const llamadas = simularApi({ 'GET /api/v1/gestor/plantilla': () => PLANTILLA });
    pintar(<Plantilla />, { sesion: sesionDe('RRHH') });

    expect(await screen.findByRole('cell', { name: 'Marta' })).toBeTruthy();
    expect(screen.getAllByRole('cell', { name: P.roles.GESTOR as string })).toHaveLength(1);
    expect(screen.queryByRole('cell', { name: 'Pedro' })).toBeNull();
    await userEvent.click(screen.getByLabelText(P.verDeBaja));
    expect(screen.getByRole('cell', { name: 'Pedro' })).toBeTruthy();
    expect(llamadas.a('GET', '/api/v1/gestor/mis-empleados')).toEqual([]);
  });

  it('un GESTOR ve a sus empleados, sin rol, ficha ni bajas', async () => {
    const llamadas = simularApi({ 'GET /api/v1/gestor/mis-empleados': () => [PLANTILLA[0]] });
    pintar(<Plantilla />, { sesion: sesionDe('GESTOR') });

    expect(await screen.findByRole('cell', { name: 'Javier' })).toBeTruthy();
    expect(screen.queryByRole('columnheader', { name: P.rol })).toBeNull();
    expect(screen.queryByRole('button', { name: P.editarFicha })).toBeNull();
    expect(screen.getByRole('button', { name: P.nuevoEmpleado })).toBeTruthy();
    expect(screen.queryByRole('button', { name: P.nuevoGestor })).toBeNull();
    expect(llamadas.a('GET', '/api/v1/gestor/plantilla')).toEqual([]);
  });

  it('dar de alta un empleado manda sus datos; el correo repetido se lee del servidor', async () => {
    let veces = 0;
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'POST /api/v1/gestor/empleados': () => (++veces === 1 ? problema(409, 'El email ya está registrado.') : json({}, 200)),
    });
    pintar(<Plantilla />, { sesion: sesionDe('ADMIN') });

    await userEvent.click(await screen.findByRole('button', { name: P.nuevoEmpleado }));
    const dialogo = await screen.findByRole('dialog', { name: P.nuevoEmpleado });
    await userEvent.click(within(dialogo).getByRole('button', { name: P.crear }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(P.altaFaltan);

    await userEvent.type(within(dialogo).getByLabelText(P.altaNombre), 'Lucía');
    await userEvent.type(within(dialogo).getByLabelText(P.altaApellidos), 'Moreno Gil');
    await userEvent.type(within(dialogo).getByLabelText(P.altaEmail), 'lucia@techcorp.demo');
    await userEvent.click(within(dialogo).getByRole('button', { name: P.crear }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe('El email ya está registrado.');
    await userEvent.click(within(dialogo).getByRole('button', { name: P.crear }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(llamadas.a('POST', '/api/v1/gestor/empleados').map((l) => l.cuerpo)).toEqual([
      { nombre: 'Lucía', apellidos: 'Moreno Gil', email: 'lucia@techcorp.demo' },
      { nombre: 'Lucía', apellidos: 'Moreno Gil', email: 'lucia@techcorp.demo' },
    ]);
  });

  it('la ficha guarda jornada y vacaciones, y el departamento solo si cambia', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'GET /api/v1/departamentos': () => [
        { id: 1, nombre: 'Desarrollo', empleados: 1 },
        { id: 2, nombre: 'Operaciones', empleados: 0 },
      ],
      'PATCH /api/v1/gestor/empleados/{id}/ficha': () => PLANTILLA[1],
      'PATCH /api/v1/departamentos/empleados/{usuarioId}': () => ({}),
    });
    pintar(<Plantilla />, { sesion: sesionDe('RRHH') });

    const fila = (await screen.findByRole('group', { name: P.accionesDe('Marta') })) as HTMLElement;
    await userEvent.click(within(fila).getByRole('button', { name: P.editarFicha }));
    const dialogo = await screen.findByRole('dialog', { name: P.fichaTitulo('Marta') });
    const horas = within(dialogo).getByLabelText(P.fichaHoras);
    await userEvent.clear(horas);
    await userEvent.type(horas, '37,5');
    await waitFor(() => expect(within(dialogo).getByRole('option', { name: 'Operaciones' })).toBeTruthy());
    await userEvent.selectOptions(within(dialogo).getByLabelText(P.departamento), '2');
    await userEvent.click(within(dialogo).getByRole('button', { name: P.guardar }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(llamadas.a('PATCH', '/api/v1/gestor/empleados/5/ficha').map((l) => l.cuerpo)).toEqual([{ horasSemanales: 37.5, diasVacaciones: 22 }]);
    expect(llamadas.a('PATCH', '/api/v1/departamentos/empleados/5').map((l) => l.cuerpo)).toEqual([{ departamentoId: 2 }]);
  });

  it('dar de baja se confirma; reactivar va directo', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'PATCH /api/v1/gestor/empleados/{id}/estado': () => sinContenido(),
    });
    pintar(<Plantilla />, { sesion: sesionDe('RRHH') });

    const javier = (await screen.findByRole('group', { name: P.accionesDe('Javier') })) as HTMLElement;
    await userEvent.click(within(javier).getByRole('button', { name: P.darDeBaja }));
    const dialogo = await screen.findByRole('dialog', { name: P.bajaTitulo('Javier') });
    expect(within(dialogo).getByText(P.bajaTexto)).toBeTruthy();
    await userEvent.click(within(dialogo).getByRole('button', { name: P.darDeBaja }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());

    await userEvent.click(screen.getByLabelText(P.verDeBaja));
    const pedro = screen.getByRole('group', { name: P.accionesDe('Pedro') });
    await userEvent.click(within(pedro).getByRole('button', { name: P.reactivar }));

    await waitFor(() =>
      expect(llamadas.llamadas.filter((l) => l.metodo === 'PATCH').map((l) => [l.ruta, l.cuerpo])).toEqual([
      ['/api/v1/gestor/empleados/2/estado', { activo: false }],
      ['/api/v1/gestor/empleados/7/estado', { activo: true }],
    ]),
    );
  });
});

describe('departamentos', () => {
  it('se ve quién hay en cada uno, y a quien no tiene se le asigna desde aquí', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'GET /api/v1/departamentos': () => [
        { id: 1, nombre: 'Desarrollo', empleados: 1 },
        { id: 2, nombre: 'Operaciones', empleados: 0 },
      ],
      'PATCH /api/v1/departamentos/empleados/{usuarioId}': () => ({}),
    });
    pintar(<Departamentos />, { sesion: sesionDe('RRHH') });

    const desarrollo = (await screen.findByRole('region', { name: 'Desarrollo' })) as HTMLElement;
    expect(within(desarrollo).getByText('Javier')).toBeTruthy();
    // Con gente dentro no se ofrece borrar; vacío, sí.
    expect(within(desarrollo).queryByRole('button', { name: D.borrar })).toBeNull();
    expect(within(screen.getByRole('region', { name: 'Operaciones' })).getByRole('button', { name: D.borrar })).toBeTruthy();

    // Marta es GESTOR y no tiene departamento: en la app no había forma de ponérselo.
    await userEvent.selectOptions(await screen.findByLabelText(D.asignarA('Marta')), '2');
    await waitFor(() =>
      expect(llamadas.a('PATCH', '/api/v1/departamentos/empleados/5').map((l) => l.cuerpo)).toEqual([{ departamentoId: 2 }]),
    );
  });

  it('crear uno pide nombre', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => [],
      'GET /api/v1/departamentos': () => [],
      'POST /api/v1/departamentos': () => json({ id: 3, nombre: 'Ventas', empleados: 0 }, 201),
    });
    pintar(<Departamentos />, { sesion: sesionDe('RRHH') });

    await userEvent.click(await screen.findByRole('button', { name: D.anadir }));
    expect(await screen.findByText(D.nombreVacio)).toBeTruthy();
    await userEvent.type(screen.getByLabelText(D.nuevo), 'Ventas');
    await userEvent.click(screen.getByRole('button', { name: D.anadir }));
    await waitFor(() => expect(llamadas.a('POST', '/api/v1/departamentos').map((l) => l.cuerpo)).toEqual([{ nombre: 'Ventas' }]));
  });
});
