/**
 * Los apartados plegables del menú lateral (1/10/2026): cuáles salen abiertos,
 * que se abren y cierran con su botón, y que se recuerda. Qué entradas van en
 * cada apartado lo prueba `secciones.test.ts`.
 */

import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../api/cliente';
import { cerrarSesion } from '../api/sesion';
import { T } from '../i18n/es';
import { pintar, sesionDe, simularApi, sinContenido, type Manejador, type Ruta } from '../pruebas/api';
import { Marco } from './Marco';
import { SECCIONES } from './secciones';

const N = T.navegacion;

/** La API de la última llamada a `pintarMarco`, para mirar qué se pidió. */
let api: ReturnType<typeof simularApi>;

function pintarMarco(rol: Parameters<typeof sesionDe>[0], ruta: string, extra: Partial<Record<Ruta, Manejador>> = {}) {
  api = simularApi({
    'GET /api/v1/avisos/no-leidos': () => ({ noLeidos: 0 }),
    'GET /api/v1/avisos': () => ({ content: [], totalElements: 0 }),
    'GET /api/v1/fichaje/activo': () => sinContenido(),
    'GET /api/v1/dashboard/pendientes': () => ({ ausencias: 0, correcciones: 0, horasExtra: 0, borrados: 0 }),
    ...extra,
  });
  pintar(
    <Routes>
      <Route element={<Marco />}>
        <Route path="*" element={<p>La página</p>} />
      </Route>
    </Routes>,
    { ruta, sesion: sesionDe(rol) },
  );
  // El lateral es el primero de los dos «Menú principal» (el otro, la barra del móvil).
  return within(screen.getAllByRole('navigation', { name: N.menuPrincipal })[0] as HTMLElement);
}

const boton = (lateral: ReturnType<typeof pintarMarco>, subgrupo: keyof typeof N.subgrupos) =>
  lateral.getByRole('button', { name: N.subgrupos[subgrupo] });

beforeEach(() => {
  localStorage.clear();
  reiniciarEstadoDeRed();
});

afterEach(() => {
  cerrarSesion();
});

describe('los apartados del menú lateral', () => {
  it('a un ADMIN le sale abierto solo el apartado de la página en que está', () => {
    const lateral = pintarMarco('ADMIN', '/plantilla');

    expect(boton(lateral, 'organizacion').getAttribute('aria-expanded')).toBe('true');
    expect(lateral.getByRole('link', { name: 'Plantilla' })).toBeTruthy();
    expect(boton(lateral, 'control').getAttribute('aria-expanded')).toBe('false');
    expect(lateral.queryByRole('link', { name: 'Informes' })).toBeNull();
  });

  it('el botón abre y cierra su apartado, y se recuerda', async () => {
    const lateral = pintarMarco('ADMIN', '/fichar');

    await userEvent.click(boton(lateral, 'control'));
    expect(boton(lateral, 'control').getAttribute('aria-expanded')).toBe('true');
    expect(lateral.getByRole('link', { name: 'Informes' })).toBeTruthy();
    expect(JSON.parse(localStorage.getItem('nx-menu-abiertos') ?? '{}')).toMatchObject({ control: true });

    await userEvent.click(boton(lateral, 'control'));
    expect(lateral.queryByRole('link', { name: 'Informes' })).toBeNull();
    expect(JSON.parse(localStorage.getItem('nx-menu-abiertos') ?? '{}')).toMatchObject({ control: false });
  });

  it('lo que se dejó abierto sigue abierto al volver', () => {
    localStorage.setItem('nx-menu-abiertos', JSON.stringify({ administracion: true }));

    const lateral = pintarMarco('ADMIN', '/fichar');

    expect(lateral.getByRole('link', { name: 'Ajustes de la empresa' })).toBeTruthy();
  });

  it('con un almacenamiento roto, el menú funciona igual', () => {
    localStorage.setItem('nx-menu-abiertos', '{no es JSON');

    const lateral = pintarMarco('ADMIN', '/fichar');

    expect(boton(lateral, 'jornada').getAttribute('aria-expanded')).toBe('true');
  });

  it('a un EMPLEADO, con un menú corto, le sale todo abierto', () => {
    const lateral = pintarMarco('EMPLEADO', '/fichar');

    for (const subgrupo of ['jornada', 'ausencias', 'en-la-empresa'] as const) {
      expect(boton(lateral, subgrupo).getAttribute('aria-expanded'), subgrupo).toBe('true');
    }
    expect(lateral.getByRole('link', { name: 'Canal de denuncias' })).toBeTruthy();
  });

  it('un apartado con una sola entrada no se pliega: es un enlace', () => {
    // Un GESTOR, de Administración, solo tiene la gestión de ofertas.
    const lateral = pintarMarco('GESTOR', '/fichar');

    expect(lateral.queryByRole('button', { name: N.subgrupos.administracion })).toBeNull();
    expect(lateral.getByRole('link', { name: 'Gestión de ofertas' })).toBeTruthy();
  });
});

/** Lo que un lector de pantalla dice después del nombre: el texto de `aria-describedby`. */
const descripcion = (elemento: HTMLElement) =>
  document.getElementById(elemento.getAttribute('aria-describedby') ?? '')?.textContent ?? null;

describe('los pendientes en el menú', () => {
  const CON_PENDIENTES = {
    'GET /api/v1/dashboard/pendientes': () => ({ ausencias: 3, correcciones: 1, horasExtra: 0, borrados: 0 }),
  } as const;

  it('la entrada dice cuántas esperan, sin cambiar de nombre', async () => {
    const lateral = pintarMarco('GESTOR', '/gestion', CON_PENDIENTES);

    // El nombre sigue siendo el de la sección: el número es su descripción.
    const ausencias = lateral.getByRole('link', { name: 'Ausencias del equipo' });
    await waitFor(() => expect(descripcion(ausencias)).toBe(N.pendientes(3)));
    // Lo que no tiene nada pendiente no lleva número.
    expect(lateral.getByRole('link', { name: 'Historial del equipo' }).getAttribute('aria-describedby')).toBeNull();
  });

  it('un apartado plegado suma lo que tiene dentro, y abierto lo deja a sus entradas', async () => {
    const lateral = pintarMarco('GESTOR', '/gestion', CON_PENDIENTES);

    // «Correcciones» está en «Jornada y fichajes», que a un GESTOR le sale plegado.
    await waitFor(() => expect(descripcion(boton(lateral, 'jornada'))).toBe(N.pendientes(1)));

    await userEvent.click(boton(lateral, 'jornada'));
    expect(boton(lateral, 'jornada').getAttribute('aria-describedby')).toBeNull();
    expect(descripcion(lateral.getByRole('link', { name: 'Correcciones' }))).toBe(N.pendientes(1));
  });

  it('quien no gestiona a nadie ni pregunta', async () => {
    const lateral = pintarMarco('EMPLEADO', '/fichar');

    await waitFor(() => expect(api.a('GET', '/api/v1/avisos/no-leidos').length).toBeGreaterThan(0));
    expect(api.a('GET', '/api/v1/dashboard/pendientes')).toHaveLength(0);
    expect(lateral.getByRole('link', { name: 'Correcciones' }).getAttribute('aria-describedby')).toBeNull();
  });

  it('el apartado de la página abierta queda marcado', () => {
    const lateral = pintarMarco('ADMIN', '/integridad');

    expect(boton(lateral, 'control').hasAttribute('data-actual')).toBe(true);
    expect(boton(lateral, 'organizacion').hasAttribute('data-actual')).toBe(false);
  });
});

describe('el estado de la jornada en la barra superior', () => {
  const HACE_DOS_HORAS = () => new Date(Date.now() - 2 * 3_600_000 - 5 * 60_000).toISOString();

  it('sin jornada abierta dice «Sin fichar» y lleva a Mi jornada', async () => {
    pintarMarco('EMPLEADO', '/historial');

    const chip = await screen.findByRole('link', { name: N.jornada.ir(N.jornada.sinFichar, null) });
    expect(chip.getAttribute('href')).toBe('/fichar');
  });

  it('trabajando, dice cuánto lleva', async () => {
    pintarMarco('EMPLEADO', '/historial', {
      'GET /api/v1/fichaje/activo': () => ({ id: 7, horaEntrada: HACE_DOS_HORAS(), enPausa: false, segundosPausaAcumulados: 0 }),
    });

    const chip = await screen.findByRole('link', { name: N.jornada.ir(N.jornada.trabajando, '2h 05m') });
    expect(chip.className).toContain('nx-chip-jornada--trabajando');
  });

  it('en pausa, el tiempo se para en el inicio de la pausa', async () => {
    // Los dos instantes, del mismo «ahora»: entró hace 2h 05m y paró hace 1h.
    const ahora = Date.now();
    const jornada = {
      id: 7,
      horaEntrada: new Date(ahora - 2 * 3_600_000 - 5 * 60_000).toISOString(),
      enPausa: true,
      inicioPausaActual: new Date(ahora - 3_600_000).toISOString(),
      segundosPausaAcumulados: 0,
    };
    pintarMarco('EMPLEADO', '/historial', { 'GET /api/v1/fichaje/activo': () => jornada });

    const chip = await screen.findByRole('link', { name: /Ir a Mi jornada/ });
    expect(chip.getAttribute('aria-label')).toBe(N.jornada.ir(N.jornada.enPausa, '1h 05m'));
  });

  it('en Mi jornada no sale: el cronómetro ya está en la página', async () => {
    pintarMarco('EMPLEADO', '/fichar');

    await waitFor(() => expect(api.a('GET', '/api/v1/fichaje/activo').length).toBeGreaterThan(0));
    expect(screen.queryByRole('link', { name: /Ir a Mi jornada/ })).toBeNull();
  });

  it('si no se sabe (la consulta falla), no dice nada', async () => {
    pintarMarco('EMPLEADO', '/historial', { 'GET /api/v1/fichaje/activo': () => new Response(null, { status: 500 }) });

    await waitFor(() => expect(api.a('GET', '/api/v1/fichaje/activo').length).toBeGreaterThan(0));
    expect(screen.queryByRole('link', { name: /Ir a Mi jornada/ })).toBeNull();
  });
});

describe('la precarga de una página', () => {
  it('pasar el ratón por su entrada (o llegar con el tabulador) pide ya su JS', async () => {
    const historial = SECCIONES.find((s) => s.ruta === 'historial')?.pagina;
    if (historial === undefined) throw new Error('El historial no tiene página.');
    const precargar = vi.spyOn(historial, 'precargar').mockImplementation(() => undefined);
    const lateral = pintarMarco('EMPLEADO', '/fichar');

    expect(precargar).not.toHaveBeenCalled();
    await userEvent.hover(lateral.getByRole('link', { name: 'Historial' }));
    expect(precargar).toHaveBeenCalled();

    precargar.mockRestore();
  });
});

describe('la precarga en reposo de las secciones principales', () => {
  const principales = () =>
    SECCIONES.filter((s) => s.principal === true).map((s) => {
      if (s.pagina === undefined) throw new Error(`${s.ruta} no tiene página.`);
      return vi.spyOn(s.pagina, 'precargar').mockImplementation(() => undefined);
    });

  /** Un navegador que tiene un rato libre en cuanto se le pide. */
  function conRatoLibre() {
    vi.stubGlobal('requestIdleCallback', (tarea: () => void) => {
      tarea();
      return 1;
    });
    vi.stubGlobal('cancelIdleCallback', () => undefined);
  }

  afterEach(() => {
    // Primero se desmonta, con el rato libre simulado todavía puesto: al
    // revés, el marco se desmontaba en un navegador al que ya le habían quitado
    // `cancelIdleCallback` (falló así en el CI y no en local).
    cleanup();
    vi.restoreAllMocks();
    // El rato libre y el ahorro de datos simulados no pasan al test siguiente.
    vi.unstubAllGlobals();
  });

  it('en el móvil no hay ratón que pase por encima: se piden solas cuando hay un rato libre', () => {
    conRatoLibre();
    const espias = principales();

    pintarMarco('EMPLEADO', '/fichar');

    expect(espias.length).toBe(4);
    for (const espia of espias) expect(espia).toHaveBeenCalledTimes(1);
  });

  it('con el ahorro de datos activado no se pide nada por adelantado', () => {
    conRatoLibre();
    vi.stubGlobal('navigator', Object.assign(Object.create(navigator), { connection: { saveData: true } }));
    const espias = principales();

    pintarMarco('EMPLEADO', '/fichar');

    for (const espia of espias) expect(espia).not.toHaveBeenCalled();
  });
});
