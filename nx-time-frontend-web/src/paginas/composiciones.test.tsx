/**
 * Lo que las páginas ganaron al pasar a lo ancho (plan del 5/10/2026, E2) y
 * que no es solo maquetación: la rejilla del calendario laboral, el expediente
 * al lado de la bandeja de denuncias, el resumen de avisos y el índice de
 * ajustes.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../api/cliente';
import { cerrarSesion } from '../api/sesion';
import { cuenta } from '../i18n/es/cuenta';
import { denuncias } from '../i18n/es/denuncias';
import { proyectos } from '../i18n/es/proyectos';
import { pintar, sesionDe, simularApi } from '../pruebas/api';
import { diaLargo, hoyEnEmpresa } from '../util/fechas';
import { Ajustes } from './cuenta/Ajustes';
import { Avisos } from './cuenta/Avisos';
import { CanalDenuncias } from './denuncias/CanalDenuncias';
import { CalendarioLaboral } from './proyectos/CalendarioLaboral';

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('calendario laboral: el mes en rejilla', () => {
  const C = proyectos.calendario;
  // Días del mes en curso, que es el que enseña la página al abrirse.
  const delMes = (dia: number) => `${hoyEnEmpresa().slice(0, 8)}${String(dia).padStart(2, '0')}`;
  const MES = {
    festivos: [
      { id: null, fecha: delMes(12), descripcion: 'Fiesta Nacional de España', ambito: 'NACIONAL', editable: false },
      { id: 8, fecha: delMes(9), descripcion: 'Día de la Comunitat Valenciana', ambito: 'AUTONOMICO', editable: true },
    ],
    ausencias: [],
  };

  it('pulsar un día libre abre «nuevo festivo» con esa fecha ya puesta', async () => {
    simularApi({ 'GET /api/v1/calendario': () => MES });
    pintar(<CalendarioLaboral />, { sesion: sesionDe('GESTOR') });

    await userEvent.click(await screen.findByRole('button', { name: C.diaLibre(diaLargo(delMes(20))) }));

    const dialogo = await screen.findByRole('dialog', { name: C.anadirTitulo });
    expect((within(dialogo).getByLabelText(C.fecha) as HTMLInputElement).value).toBe(delMes(20));
  });

  it('un festivo que se puede cambiar se abre al pulsarlo; uno nacional no es un botón', async () => {
    simularApi({ 'GET /api/v1/calendario': () => MES });
    pintar(<CalendarioLaboral />, { sesion: sesionDe('GESTOR') });

    const nacional = C.diaFestivo(diaLargo(delMes(12)), C.ambitos.NACIONAL ?? '', 'Fiesta Nacional de España');
    expect(await screen.findByRole('img', { name: nacional })).toBeTruthy();
    expect(screen.queryByRole('button', { name: nacional })).toBeNull();

    await userEvent.click(
      screen.getByRole('button', { name: C.diaFestivo(diaLargo(delMes(9)), C.ambitos.AUTONOMICO ?? '', 'Día de la Comunitat Valenciana') }),
    );
    const dialogo = await screen.findByRole('dialog', { name: C.editarTitulo });
    expect((within(dialogo).getByLabelText(C.descripcion) as HTMLInputElement).value).toBe('Día de la Comunitat Valenciana');
  });
});

describe('denuncias recibidas: el expediente al lado en una pantalla ancha', () => {
  const C = denuncias.canal;
  const EXPEDIENTE = {
    id: 12,
    categoria: 'ACOSO',
    categoriaEtiqueta: 'Acoso laboral o sexual',
    descripcion: 'Hechos.',
    estado: 'RECIBIDA',
    anonima: false,
    denunciante: 'Ana',
    creadoEn: '2026-09-20T08:00:00Z',
    diasHastaAcuse: 3,
    diasHastaRespuesta: 80,
    mensajes: [],
  };

  /** Una ventana que responde «sí» a cualquier `min-width`: un escritorio. */
  function ventanaAncha() {
    vi.stubGlobal('matchMedia', (consulta: string) => ({
      matches: true,
      media: consulta,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
  }

  it('abre el expediente en la propia página, sin diálogo, y marca cuál es', async () => {
    ventanaAncha();
    simularApi({
      'GET /api/v1/denuncias': () => [{ ...EXPEDIENTE, mensajes: 0 }],
      'GET /api/v1/denuncias/{id}': () => EXPEDIENTE,
    });
    pintar(<CanalDenuncias />, { sesion: sesionDe('ADMIN') });

    // Antes de elegir, el hueco dice qué va a salir ahí.
    expect(await screen.findByText(C.elige)).toBeTruthy();
    const abrir = await screen.findByRole('button', { name: /^Abrir/ });
    expect(abrir.getAttribute('aria-pressed')).toBe('false');

    await userEvent.click(abrir);

    expect(await screen.findByText(/^La presentó Ana/)).toBeTruthy();
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(abrir.getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('region', { name: 'Acoso laboral o sexual' })).toBeTruthy();
  });

  it('en una pantalla estrecha sigue saliendo en su diálogo', async () => {
    simularApi({
      'GET /api/v1/denuncias': () => [{ ...EXPEDIENTE, mensajes: 0 }],
      'GET /api/v1/denuncias/{id}': () => EXPEDIENTE,
    });
    pintar(<CanalDenuncias />, { sesion: sesionDe('ADMIN') });

    await userEvent.click(await screen.findByRole('button', { name: /^Abrir/ }));

    const dialogo = await screen.findByRole('dialog', { name: 'Acoso laboral o sexual' });
    expect(await within(dialogo).findByText(/^La presentó Ana/)).toBeTruthy();
    expect(screen.queryByText(C.elige)).toBeNull();
  });
});

describe('avisos: cuántos quedan y el camino a las notificaciones', () => {
  const V = cuenta.avisos;
  const AVISOS = {
    contenido: [
      { id: 1, tipo: 'AUSENCIA_RESUELTA', titulo: 'Ausencia aprobada', cuerpo: 'Que la disfrutes', rutaDestino: 'ausencias', leido: false, creadoEn: '2026-09-20T08:00:00Z' },
    ],
    hayMas: false,
  };

  it('dice cuántos hay sin leer y enlaza a los ajustes de notificaciones', async () => {
    simularApi({ 'GET /api/v1/avisos': () => AVISOS, 'GET /api/v1/avisos/no-leidos': () => ({ noLeidos: 3 }) });
    pintar(<Avisos />, { sesion: sesionDe('EMPLEADO') });

    const cifra = (await screen.findByText(V.resumen)).closest('.nx-cifra');
    expect(cifra?.textContent).toContain('3');
    expect(screen.getByRole('link', { name: V.ajustesEnlace }).getAttribute('href')).toBe('/ajustes#ajustes-notificaciones');
  });

  it('sin ninguno sin leer, dice que estás al día', async () => {
    simularApi({ 'GET /api/v1/avisos': () => ({ contenido: [], hayMas: false }), 'GET /api/v1/avisos/no-leidos': () => ({ noLeidos: 0 }) });
    pintar(<Avisos />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(V.alDia)).toBeTruthy();
  });
});

describe('ajustes: el índice', () => {
  const J = cuenta.ajustes;

  it('cada sección del índice lleva a su tarjeta, y llegar con una en la URL baja hasta ella', async () => {
    const bajar = vi.fn();
    Element.prototype.scrollIntoView = bajar;
    simularApi({});
    pintar(<Ajustes />, { ruta: '/ajustes#ajustes-notificaciones', sesion: sesionDe('EMPLEADO') });

    const indice = screen.getByRole('navigation', { name: J.indice });
    const enlaces = within(indice).getAllByRole('link');
    expect(enlaces.length).toBeGreaterThanOrEqual(5);
    for (const enlace of enlaces) {
      const id = (enlace.getAttribute('href') ?? '').slice(1);
      expect(document.getElementById(id), `no hay tarjeta #${id}`).not.toBeNull();
    }
    await waitFor(() => expect(bajar).toHaveBeenCalled());
  });
});
