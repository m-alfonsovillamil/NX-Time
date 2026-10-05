/**
 * Panel de empresa, informes e integridad de la auditoría.
 */

import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { empresa } from '../../i18n/es/empresa';
import { json, pintar, problema, sesionDe, sinContenido, simularApi } from '../../pruebas/api';
import { Informes, mesAnterior } from './Informes';
import { Integridad } from './Integridad';
import { PanelEmpresa, porcentaje } from './PanelEmpresa';

const E = empresa.panel;
const A = empresa.analitica;
const I = empresa.informes;
const G = empresa.integridad;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('reglas', () => {
  it('un porcentaje con coma, o una raya si todavía no hay cifra', () => {
    expect(porcentaje(4.25)).toBe('4,3 %');
    expect(porcentaje(null)).toBe('—');
  });

  it('el mes del informe por defecto es el anterior, también en enero', () => {
    expect(mesAnterior('2026-09-27')).toBe('2026-08');
    expect(mesAnterior('2027-01-05')).toBe('2026-12');
  });
});

const PANEL = {
  empleadosActivos: 4,
  minutosMesEmpresa: 30000,
  ausenciasPendientes: 2,
  incidenciasAbiertas: 3,
  horasExtraAbiertas: 7,
  denunciasAbiertas: 1,
  horasPorEmpleado: [
    { usuarioId: 2, nombre: 'Javier', minutos: 9000 },
    { usuarioId: 3, nombre: 'Ana', minutos: 6000 },
  ],
};

describe('panel de empresa', () => {
  it('indicadores del mes, incidencias en alerta y la media del equipo', async () => {
    simularApi({
      'GET /api/v1/dashboard/empresa': () => PANEL,
      'GET /api/v1/proyectos/horas': () => ({ proyectos: [] }),
      'GET /api/v1/analitica/resumen': () => ({
        ventana: { periodo: 'MES', evaluadoHasta: '2026-09-26', alcance: 'DEPARTAMENTO', departamento: 'Ingeniería' },
        absentismo: 2.5,
        puntualidad: 91,
      }),
    });
    pintar(<PanelEmpresa />, { sesion: sesionDe('GESTOR') });

    expect(await screen.findByText('500h 00m')).toBeTruthy();
    const ausencias = screen.getByRole('link', { name: `2 ${E.ausencias}` });
    expect(ausencias.getAttribute('href')).toBe('/ausencias-equipo/pendientes');
    expect(screen.getByText(E.incidencias).closest('.nx-cifra--alerta')).not.toBeNull();
    // Las denuncias son de quien instruye (ADMIN): un GESTOR no las ve contadas.
    expect(screen.queryByText(E.denuncias)).toBeNull();
    expect(screen.getByText(E.mediaEquipo('125h 00m'))).toBeTruthy();
    expect(await screen.findByText('2,5 %')).toBeTruthy();
    expect(screen.getByText(/Ingeniería$/)).toBeTruthy();
  });

  it('el día 1 no inventa un 0 %: dice que no ha terminado ningún día', async () => {
    simularApi({
      'GET /api/v1/dashboard/empresa': () => ({ ...PANEL, horasPorEmpleado: [] }),
      'GET /api/v1/proyectos/horas': () => ({ proyectos: [] }),
      'GET /api/v1/analitica/resumen': () => ({ ventana: { periodo: 'MES', evaluadoHasta: null }, absentismo: null, puntualidad: null }),
    });
    pintar(<PanelEmpresa />, { sesion: sesionDe('ADMIN') });

    expect(await screen.findByText(A.sinDias)).toBeTruthy();
    expect(screen.getAllByText('—')).toHaveLength(2);
    expect(screen.getByText(E.denuncias)).toBeTruthy();
    expect(screen.getByText(E.sinHoras)).toBeTruthy();
  });
});

describe('informes', () => {
  function capturarDescarga() {
    const descargas: Blob[] = [];
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: vi.fn((blob: Blob) => {
          descargas.push(blob);
          return 'blob:nx';
        }),
        revokeObjectURL: vi.fn(),
      }),
    );
    return descargas;
  }

  it('el Excel del mes elegido y el PDF de una persona', async () => {
    const descargas = capturarDescarga();
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => [
        { id: 2, nombre: 'Javier', email: 'j@x', activo: true, horasSemanales: 40, diasVacaciones: 22, departamentoId: null, departamentoNombre: null, rol: 'EMPLEADO' },
      ],
      'GET /api/v1/informes/horas': () => new Response(new Blob(['xlsx']), { status: 200 }),
      'GET /api/v1/informes/mensual/{empleadoId}': () => new Response(new Blob(['pdf']), { status: 200 }),
    });
    pintar(<Informes />, { sesion: sesionDe('RRHH') });

    // jsdom no sabe escribir en un <input type="month">: se cambia el valor.
    fireEvent.change(await screen.findByLabelText(I.mes), { target: { value: '2026-07' } });
    await userEvent.click(screen.getByRole('button', { name: I.excel }));
    await waitFor(() => expect(descargas).toHaveLength(1));
    const [excel] = llamadas.a('GET', '/api/v1/informes/horas');
    expect([excel?.query.get('anio'), excel?.query.get('mes')]).toEqual(['2026', '7']);

    // Sin persona elegida, se dice antes de pedir nada.
    await userEvent.click(screen.getByRole('button', { name: I.pdf }));
    expect(await screen.findByText(I.faltaPersona)).toBeTruthy();
    await waitFor(() => expect(screen.getByRole('option', { name: 'Javier' })).toBeTruthy());
    await userEvent.selectOptions(screen.getByLabelText(I.persona), '2');
    await userEvent.click(screen.getByRole('button', { name: I.pdf }));
    await waitFor(() => expect(descargas).toHaveLength(2));
    expect(llamadas.a('GET', '/api/v1/informes/mensual/2')).toHaveLength(1);
  });

  it('si el servidor no puede, se lee su mensaje', async () => {
    capturarDescarga();
    simularApi({
      'GET /api/v1/gestor/plantilla': () => [],
      'GET /api/v1/informes/horas': () => problema(400, 'El mes todavía no ha empezado.'),
    });
    pintar(<Informes />, { sesion: sesionDe('RRHH') });

    await userEvent.click(await screen.findByRole('button', { name: I.excel }));
    expect((await screen.findByRole('alert')).textContent).toBe('El mes todavía no ha empezado.');
  });
});

describe('integridad', () => {
  it('la última comprobación con lo pendiente, y comprobar ahora', async () => {
    simularApi({
      'GET /api/v1/auditoria/integridad/ultima': () => ({ verificadoEn: '2026-09-27T01:00:00Z', movimientos: 900, pendientes: 4 }),
      'GET /api/v1/auditoria/integridad': () => json({ intacta: true, movimientos: 904, comprobados: 880, soloEnlace: 24 }),
    });
    pintar(<Integridad />, { sesion: sesionDe('RRHH') });

    expect(await screen.findByText(G.pendientes(4))).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: G.comprobar }));
    const resultado = await screen.findByRole('status');
    expect(within(resultado).getByText(G.intacta)).toBeTruthy();
    expect(within(resultado).getByText(G.detalle(904, 880, 24))).toBeTruthy();
  });

  it('una cadena rota dice en qué movimiento; sin comprobaciones previas, lo dice', async () => {
    simularApi({
      'GET /api/v1/auditoria/integridad/ultima': () => sinContenido(),
      'GET /api/v1/auditoria/integridad': () =>
        json({ intacta: false, movimientos: 10, comprobados: 6, soloEnlace: 0, primerFallo: 7, motivo: 'Su huella no cuadra con su contenido.' }),
    });
    pintar(<Integridad />, { sesion: sesionDe('ADMIN') });

    expect(await screen.findByText(G.nunca)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: G.comprobar }));
    const resultado = await screen.findByRole('status');
    expect(within(resultado).getByText(G.rota)).toBeTruthy();
    expect(within(resultado).getByText(G.primerFallo(7, 'Su huella no cuadra con su contenido.'))).toBeTruthy();
  });
});
