/**
 * Importar la plantilla, las invitaciones que esperan y el cambio de rol (ADR 052).
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { plantilla } from '../../i18n/es/plantilla';
import { pintar, sesionDe, simularApi } from '../../pruebas/api';
import { Plantilla } from './Plantilla';

const P = plantilla;
const I = plantilla.importar;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const PLANTILLA = [
  { id: 2, nombre: 'Javier', email: 'javier@techcorp.demo', activo: true, horasSemanales: 40, diasVacaciones: 22, rol: 'EMPLEADO' },
  { id: 9, nombre: 'Nuevo', email: 'nuevo@techcorp.demo', activo: true, horasSemanales: 40, diasVacaciones: 22, rol: 'EMPLEADO', invitacionPendiente: true },
];

function fila(numero: number, email: string, errores: string[] = []) {
  return { fila: numero, nombre: 'Ana', apellidos: 'López', email, rol: 'EMPLEADO', errores };
}

const FICHERO = new File(['nombre;apellidos;email\nAna;López;ana@x.es\n'], 'plantilla.csv', { type: 'text/csv' });

async function abrirYSubir() {
  await userEvent.click(await screen.findByRole('button', { name: I.boton }));
  const dialogo = await screen.findByRole('dialog', { name: I.titulo });
  await userEvent.upload(within(dialogo).getByLabelText(I.fichero), FICHERO);
  return dialogo;
}

describe('importar la plantilla', () => {
  it('primero comprueba sin crear nada, y con errores no deja crear', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'POST /api/v1/gestor/plantilla/importar': () => ({
        simulada: true,
        filas: 2,
        conErrores: 1,
        creadas: 0,
        columnasIgnoradas: ['DNI'],
        detalle: [fila(2, 'ana@x.es'), fila(3, 'ana@x.es', ['El correo está repetido: ya sale en la fila 2.'])],
      }),
    });
    pintar(<Plantilla />, { sesion: sesionDe('ADMIN') });

    const dialogo = await abrirYSubir();

    expect(await within(dialogo).findByText(I.resumen(2, 1))).toBeTruthy();
    expect(within(dialogo).getByText('El correo está repetido: ya sale en la fila 2.')).toBeTruthy();
    expect(within(dialogo).getByText(I.ignoradas('DNI'))).toBeTruthy();
    expect((within(dialogo).getByRole('button', { name: I.crear(2) }) as HTMLButtonElement).disabled).toBe(true);
    const subidas = llamadas.a('POST', '/api/v1/gestor/plantilla/importar');
    expect(subidas).toHaveLength(1);
    expect(subidas[0]?.query.get('simular')).toBe('true');
  });

  it('sin errores crea a todas, y dice cuántas invitaciones salen y cuántas esperan', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'POST /api/v1/gestor/plantilla/importar': ({ query }) =>
        query.get('simular') === 'true'
          ? { simulada: true, filas: 55, conErrores: 0, creadas: 0, columnasIgnoradas: [], detalle: [fila(2, 'ana@x.es')] }
          : {
              simulada: false,
              filas: 55,
              conErrores: 0,
              creadas: 55,
              columnasIgnoradas: [],
              detalle: [fila(2, 'ana@x.es')],
              invitaciones: { pendientes: 55, cupoHoy: 50, enCamino: 50 },
            },
    });
    pintar(<Plantilla />, { sesion: sesionDe('ADMIN') });

    const dialogo = await abrirYSubir();
    await userEvent.click(await within(dialogo).findByRole('button', { name: I.crear(55) }));

    expect(await within(dialogo).findByText(I.hecho(55))).toBeTruthy();
    expect(within(dialogo).getByText(I.invitaciones(50, 55))).toBeTruthy();
    const subidas = llamadas.a('POST', '/api/v1/gestor/plantilla/importar');
    expect(subidas.map((s) => s.query.get('simular'))).toEqual(['true', 'false']);
  });

  it('un GESTOR puede importar, pero no cambiar roles', async () => {
    simularApi({ 'GET /api/v1/gestor/plantilla': () => PLANTILLA });
    pintar(<Plantilla />, { sesion: sesionDe('GESTOR') });

    expect(await screen.findByRole('button', { name: I.boton })).toBeTruthy();
    expect(screen.queryByRole('button', { name: P.cambioDeRol.boton })).toBeNull();
  });
});

describe('las invitaciones que esperan', () => {
  it('se ve quién no ha recibido la suya, y el aviso las manda', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'GET /api/v1/gestor/plantilla/invitaciones': () => ({ pendientes: 5, cupoHoy: 3, enCamino: 0 }),
      'POST /api/v1/gestor/plantilla/invitaciones/enviar': () => ({ pendientes: 5, cupoHoy: 3, enCamino: 3 }),
    });
    pintar(<Plantilla />, { sesion: sesionDe('RRHH') });

    expect(await screen.findByText(P.invitaciones.pendiente)).toBeTruthy();
    expect(await screen.findByText(P.invitaciones.aviso(5, 3))).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: P.invitaciones.enviar }));

    expect(await screen.findByText(P.invitaciones.enviando(3))).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/gestor/plantilla/invitaciones/enviar')).toHaveLength(1);
  });

  it('sin cupo hoy, el aviso lo dice y no ofrece mandarlas', async () => {
    simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'GET /api/v1/gestor/plantilla/invitaciones': () => ({ pendientes: 5, cupoHoy: 0, enCamino: 0 }),
    });
    pintar(<Plantilla />, { sesion: sesionDe('RRHH') });

    expect(await screen.findByText(P.invitaciones.aviso(5, 0))).toBeTruthy();
    expect(screen.queryByRole('button', { name: P.invitaciones.enviar })).toBeNull();
  });
});

describe('cambiar el rol', () => {
  it('un ADMIN elige el rol nuevo y se guarda', async () => {
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => PLANTILLA,
      'PATCH /api/v1/gestor/empleados/{id}/rol': () => new Response(null, { status: 204 }),
    });
    pintar(<Plantilla />, { sesion: sesionDe('ADMIN') });

    const javier = (await screen.findByRole('cell', { name: 'Javier' })).closest('tr') as HTMLElement;
    await userEvent.click(within(javier).getByRole('button', { name: P.cambioDeRol.boton }));
    const dialogo = await screen.findByRole('dialog', { name: P.cambioDeRol.titulo('Javier') });
    await userEvent.selectOptions(within(dialogo).getByLabelText(P.cambioDeRol.etiqueta), 'RRHH');
    await userEvent.click(within(dialogo).getByRole('button', { name: P.cambioDeRol.guardar }));

    await waitFor(() => expect(llamadas.a('PATCH', '/api/v1/gestor/empleados/2/rol')).toHaveLength(1));
    expect(llamadas.a('PATCH', '/api/v1/gestor/empleados/2/rol')[0]?.cuerpo).toEqual({ rol: 'RRHH' });
  });
});
