/**
 * El panel de plataforma (ADR 040): la lista de empresas, lo que se ve de la
 * instalación y el detalle de una empresa.
 *
 * Quién lo ve no se prueba aquí: el menú, en `secciones.test.ts`; la guarda de
 * la ruta, en el e2e; y lo que de verdad lo impide, en el backend.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { plataforma } from '../../i18n/es/plataforma';
import { pintar, problema, sesionDeOperadora, simularApi } from '../../pruebas/api';
import { tamano } from './DetalleDeEmpresa';
import { Plataforma, diaDe, empresaElegida } from './Plataforma';

const P = plataforma;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const TAREAS = [
  { tarea: 'CIERRE_JORNADAS', ok: true, ultimaEjecucion: '2026-10-09T01:00:00Z', ultimoResultado: 'OK' },
  { tarea: 'VERIFICACION_INTEGRIDAD', ok: true, ultimaEjecucion: '2026-10-09T01:50:00Z', ultimoResultado: 'OK' },
];

const RESUMEN = {
  empresas: 3,
  empresasConActividad: 2,
  empleadosActivos: 12,
  registrosSinConfirmar: 1,
  fichajesHoy: 5,
  altasPorSemana: [
    { semana: '2026-09-28', altas: 0 },
    { semana: '2026-10-05', altas: 3 },
  ],
  tareas: { ok: true, tareas: TAREAS },
  cadena: { ultimaComprobacion: '2026-10-09T01:50:00Z', movimientosComprobados: 480, movimientosSinRevisar: 2 },
};

const TECHCORP = {
  id: 1,
  nombre: 'TechCorp Solutions',
  zonaHoraria: 'Europe/Madrid',
  creadaEn: '2026-07-13T06:54:00Z',
  empleadosActivos: 7,
  empleadosDeBaja: 0,
  registroSinConfirmar: false,
  ultimoFichaje: '2026-10-09T06:56:00Z',
  fichajesEn7Dias: 18,
  fichajesEn30Dias: 84,
  personasQueFichan: 4,
  ultimaSesion: '2026-10-09T19:41:00Z',
};

const TALLERES = {
  id: 3,
  nombre: 'Talleres Sin Confirmar',
  zonaHoraria: 'Europe/Madrid',
  creadaEn: null,
  empleadosActivos: 1,
  empleadosDeBaja: 0,
  registroSinConfirmar: true,
  ultimoFichaje: null,
  fichajesEn7Dias: 0,
  fichajesEn30Dias: 0,
  personasQueFichan: 0,
  ultimaSesion: null,
};

const pagina = (contenido: unknown[]) => ({
  contenido,
  pagina: 0,
  tamano: 25,
  totalElementos: contenido.length,
  totalPaginas: 1,
  hayMas: false,
});

const DETALLE = {
  id: 1,
  nombre: 'TechCorp Solutions',
  zonaHoraria: 'Europe/Madrid',
  creadaEn: '2026-07-13T06:54:00Z',
  empleadosActivos: 7,
  empleadosDeBaja: 1,
  plantilla: [
    { rol: 'EMPLEADO', activos: 4, deBaja: 1 },
    { rol: 'GESTOR', activos: 1, deBaja: 0 },
    { rol: 'RRHH', activos: 1, deBaja: 0 },
    { rol: 'ADMIN', activos: 1, deBaja: 0 },
  ],
  administradores: [{ nombre: 'Raúl Ortega Lima', email: 'raul.ortega@techcorp.demo', correoSinConfirmar: false }],
  fichajes: { ultimo: '2026-10-09T06:56:00Z', total: 252, en7Dias: 18, en30Dias: 84, personasEn30Dias: 4 },
  sesiones: { ultima: '2026-10-09T19:41:00Z', webEn30Dias: 1, appEn30Dias: 3 },
  cuentasConGoogle: 2,
  cuentasConMicrosoft: 0,
  kioscos: 1,
  dispositivosPush: 3,
  departamentos: 2,
  proyectos: 2,
  bytesDeAdjuntos: 3738,
  borradosPendientes: 0,
  movimientosDeAuditoria: 41,
};

describe('reglas', () => {
  it('la empresa elegida sale de la URL, y solo si es un número de verdad', () => {
    expect(empresaElegida(new URLSearchParams('empresa=7'))).toBe(7);
    expect(empresaElegida(new URLSearchParams(''))).toBeNull();
    expect(empresaElegida(new URLSearchParams('empresa=0'))).toBeNull();
    expect(empresaElegida(new URLSearchParams('empresa=-3'))).toBeNull();
    expect(empresaElegida(new URLSearchParams('empresa=7abc'))).toBeNull();
    expect(empresaElegida(new URLSearchParams('empresa=1.5'))).toBeNull();
  });

  it('lo que ocupan los adjuntos, con coma y con la unidad que toca', () => {
    expect(tamano(0)).toBe('0 B');
    expect(tamano(999)).toBe('999 B');
    expect(tamano(3738)).toBe('3,7 kB');
    expect(tamano(12_500_000)).toBe('12,5 MB');
    expect(tamano(2_000_000_000)).toBe('2 GB');
  });

  it('una empresa sin fecha de alta lo dice, en vez de dejar un hueco', () => {
    expect(diaDe(null, 'Sin fecha')).toBe('Sin fecha');
    expect(diaDe(undefined, 'Sin fecha')).toBe('Sin fecha');
    expect(diaDe('2026-07-13T06:54:00Z', 'Sin fecha')).toBe('13 de julio de 2026');
  });
});

describe('la instalación', () => {
  it('los totales, y la lista con las cifras de cada empresa', async () => {
    const api = simularApi({
      'GET /api/v1/plataforma/resumen': () => RESUMEN,
      'GET /api/v1/plataforma/empresas': () => pagina([TECHCORP, TALLERES]),
    });
    pintar(<Plataforma />, { ruta: '/plataforma', sesion: sesionDeOperadora() });

    const totales = await screen.findByRole('list', { name: P.resumen.titulo });
    expect(within(totales).getByText('12')).toBeTruthy();
    expect(within(totales).getByText(P.resumen.conActividad(2))).toBeTruthy();
    // Un registro que se quedó a medias se explica: es lo que hay que mirar.
    expect(within(totales).getByText(P.resumen.sinConfirmarDetalle)).toBeTruthy();

    const tabla = await screen.findByRole('table', { name: P.lista.tabla });
    const techcorp = within(tabla).getByRole('link', { name: 'TechCorp Solutions' });
    expect(techcorp.getAttribute('href')).toBe('/plataforma?empresa=1');
    expect(within(tabla).getByText('18')).toBeTruthy();
    expect(screen.getByText(P.lista.cuantas(2))).toBeTruthy();

    // La que no ha hecho nada: sin confirmar, sin fecha y «nunca», no huecos.
    const talleres = within(tabla).getByRole('link', { name: 'Talleres Sin Confirmar' }).closest('tr');
    expect(talleres).not.toBeNull();
    const fila = within(talleres as HTMLElement);
    expect(fila.getByText(P.lista.sinConfirmar)).toBeTruthy();
    expect(fila.getByText(P.lista.sinFecha)).toBeTruthy();
    expect(fila.getAllByText(P.lista.nunca)).toHaveLength(2);

    expect(api.sinSimular).toEqual([]);
  });

  it('buscar y ordenar se piden al servidor, y lo tecleado va de una vez', async () => {
    const api = simularApi({
      'GET /api/v1/plataforma/resumen': () => RESUMEN,
      'GET /api/v1/plataforma/empresas': ({ query }) =>
        pagina(query.get('busqueda') === 'zzz' ? [] : query.get('busqueda') === 'tech' ? [TECHCORP] : [TECHCORP, TALLERES]),
    });
    pintar(<Plataforma />, { ruta: '/plataforma', sesion: sesionDeOperadora() });
    await screen.findByRole('link', { name: 'Talleres Sin Confirmar' });

    await userEvent.type(screen.getByLabelText(P.lista.buscar), 'tech');
    await waitFor(() => expect(api.a('GET', '/api/v1/plataforma/empresas')).toHaveLength(2));
    expect(await screen.findByRole('link', { name: 'TechCorp Solutions' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Talleres Sin Confirmar' })).toBeNull();
    // Una petición por lo tecleado, no una por letra.
    const busquedas = api.a('GET', '/api/v1/plataforma/empresas').map((l) => l.query.get('busqueda'));
    expect(busquedas).toEqual([null, 'tech']);

    await userEvent.selectOptions(screen.getByLabelText(P.lista.orden), 'ACTIVIDAD');
    await waitFor(() =>
      expect(api.a('GET', '/api/v1/plataforma/empresas').at(-1)?.query.get('orden')).toBe('ACTIVIDAD'),
    );
    expect(api.a('GET', '/api/v1/plataforma/empresas').at(-1)?.query.get('busqueda')).toBe('tech');

    await userEvent.clear(screen.getByLabelText(P.lista.buscar));
    await userEvent.type(screen.getByLabelText(P.lista.buscar), 'zzz');
    expect(await screen.findByText(P.lista.sinResultados('zzz'))).toBeTruthy();
  });

  it('si una tarea nocturna no ha corrido, se dice arriba y se señala cuál', async () => {
    simularApi({
      'GET /api/v1/plataforma/resumen': () => ({
        ...RESUMEN,
        tareas: { ok: false, tareas: [TAREAS[0], { tarea: 'HORAS_EXTRA', ok: false, ultimoResultado: 'ERROR' }] },
      }),
      'GET /api/v1/plataforma/empresas': () => pagina([TECHCORP]),
    });
    pintar(<Plataforma />, { ruta: '/plataforma', sesion: sesionDeOperadora() });

    expect(await screen.findByText(P.tareas.mal(1))).toBeTruthy();
    const tareas = screen.getByRole('table', { name: P.tareas.tabla });
    const rota = within(tareas).getByText(P.tareas.nombres['HORAS_EXTRA'] ?? '').closest('tr') as HTMLElement;
    expect(within(rota).getByText(P.tareas.fallo)).toBeTruthy();
    // Nunca ha corrido: se dice, no se deja la celda vacía.
    expect(within(rota).getByText(P.tareas.nunca)).toBeTruthy();
  });

  it('la traza: lo de anoche y, al comprobarla, la fila rota y de qué empresa es', async () => {
    simularApi({
      'GET /api/v1/plataforma/resumen': () => RESUMEN,
      'GET /api/v1/plataforma/empresas': () => pagina([TECHCORP]),
      'GET /api/v1/plataforma/integridad': () => ({
        intacta: false,
        movimientos: 37,
        comprobados: 37,
        soloEnlace: 0,
        primerFallo: 38,
        empresaDelFallo: 1,
        motivo: 'el contenido de la fila no coincide con su hash: le han cambiado algo',
        comprobadaEn: '2026-10-09T19:41:04Z',
      }),
    });
    pintar(<Plataforma />, { ruta: '/plataforma', sesion: sesionDeOperadora() });

    expect(await screen.findByText('480')).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: P.cadena.comprobar }));

    expect(await screen.findByText(P.cadena.rota)).toBeTruthy();
    expect(screen.getByText(/Primer movimiento con problemas: el 38\./)).toBeTruthy();
    expect(screen.getByRole('link', { name: P.cadena.verEmpresa(1) }).getAttribute('href')).toBe('/plataforma?empresa=1');
  });

  it('si la traza está intacta, se dice y no se señala a nadie', async () => {
    simularApi({
      'GET /api/v1/plataforma/resumen': () => ({
        ...RESUMEN,
        cadena: { ultimaComprobacion: null, movimientosComprobados: 0, movimientosSinRevisar: 2 },
      }),
      'GET /api/v1/plataforma/empresas': () => pagina([TECHCORP]),
      'GET /api/v1/plataforma/integridad': () => ({
        intacta: true, movimientos: 2, comprobados: 2, soloEnlace: 0, primerFallo: null, empresaDelFallo: null, motivo: null,
      }),
    });
    pintar(<Plataforma />, { ruta: '/plataforma', sesion: sesionDeOperadora() });

    expect(await screen.findByText(P.cadena.nunca)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: P.cadena.comprobar }));
    expect(await screen.findByText(P.cadena.intacta)).toBeTruthy();
    expect(screen.queryByText(P.cadena.deNadie)).toBeNull();
    expect(screen.queryByText(/Primer movimiento/)).toBeNull();
  });

  it('si el resumen falla, la lista sigue ahí', async () => {
    simularApi({
      'GET /api/v1/plataforma/resumen': () => problema(503, 'El servidor está arrancando.'),
      'GET /api/v1/plataforma/empresas': () => pagina([TECHCORP]),
    });
    pintar(<Plataforma />, { ruta: '/plataforma', sesion: sesionDeOperadora() });

    expect(await screen.findByRole('link', { name: 'TechCorp Solutions' })).toBeTruthy();
    expect((await screen.findAllByText('El servidor está arrancando.')).length).toBeGreaterThan(0);
  });
});

describe('el detalle de una empresa', () => {
  it('sus cifras y a quién escribir', async () => {
    const api = simularApi({ 'GET /api/v1/plataforma/empresas/{id}': () => DETALLE });
    pintar(<Plataforma />, { ruta: '/plataforma?empresa=1', sesion: sesionDeOperadora() });

    expect(await screen.findByRole('heading', { level: 1, name: 'TechCorp Solutions' })).toBeTruthy();
    expect(api.a('GET', '/api/v1/plataforma/empresas/1')).toHaveLength(1);

    const correo = screen.getByRole('link', { name: 'raul.ortega@techcorp.demo' });
    expect(correo.getAttribute('href')).toBe('mailto:raul.ortega@techcorp.demo');

    const plantilla = screen.getByRole('table', { name: P.detalle.plantillaTabla });
    const empleados = within(plantilla).getByText(P.detalle.roles['EMPLEADO'] ?? '').closest('tr') as HTMLElement;
    expect(within(empleados).getByText('4')).toBeTruthy();

    expect(screen.getByText('252')).toBeTruthy();
    expect(screen.getByText('3,7 kB')).toBeTruthy();
    expect(screen.getByText(`${P.detalle.web}: 1 · ${P.detalle.app}: 3`)).toBeTruthy();
    expect(screen.getByRole('link', { name: P.detalle.volver }).getAttribute('href')).toBe('/plataforma');
    expect(api.sinSimular).toEqual([]);
  });

  it('desde la lista se llega pulsando el nombre', async () => {
    simularApi({
      'GET /api/v1/plataforma/resumen': () => RESUMEN,
      'GET /api/v1/plataforma/empresas': () => pagina([TECHCORP]),
      'GET /api/v1/plataforma/empresas/{id}': () => DETALLE,
    });
    pintar(<Plataforma />, { ruta: '/plataforma', sesion: sesionDeOperadora() });

    await userEvent.click(await screen.findByRole('link', { name: 'TechCorp Solutions' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'TechCorp Solutions' })).toBeTruthy();
    expect(screen.getByText(P.detalle.contacto)).toBeTruthy();
  });

  it('la que se quedó a medias: su administrador sin confirmar y «nunca» donde no hay nada', async () => {
    simularApi({
      'GET /api/v1/plataforma/empresas/{id}': () => ({
        ...DETALLE,
        id: 3,
        nombre: 'Talleres Sin Confirmar',
        creadaEn: null,
        administradores: [{ nombre: 'Nuria Prueba', email: 'nuria@talleres.example', correoSinConfirmar: true }],
        fichajes: { ultimo: null, total: 0, en7Dias: 0, en30Dias: 0, personasEn30Dias: 0 },
        sesiones: { ultima: null, webEn30Dias: 0, appEn30Dias: 0 },
      }),
    });
    pintar(<Plataforma />, { ruta: '/plataforma?empresa=3', sesion: sesionDeOperadora() });

    expect(await screen.findByText(P.detalle.sinConfirmar)).toBeTruthy();
    expect(screen.getByText(P.detalle.sinFecha)).toBeTruthy();
    expect(screen.getAllByText(P.detalle.nunca)).toHaveLength(2);
  });

  it('una empresa que no existe: lo que dice el servidor y por dónde volver', async () => {
    simularApi({ 'GET /api/v1/plataforma/empresas/{id}': () => problema(404, 'Esa empresa no existe.') });
    pintar(<Plataforma />, { ruta: '/plataforma?empresa=999', sesion: sesionDeOperadora() });

    expect(await screen.findByText('Esa empresa no existe.')).toBeTruthy();
    expect(screen.getByRole('link', { name: P.detalle.volver })).toBeTruthy();
  });
});
