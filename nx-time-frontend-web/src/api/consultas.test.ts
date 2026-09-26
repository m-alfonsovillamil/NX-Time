/**
 * El pegamento entre `openapi-fetch` y TanStack Query: qué es un error y qué no.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { T } from '../i18n/es';
import { json, problema, simularApi, sinContenido } from '../pruebas/api';
import { cliente } from './cliente';
import { ErrorDeApi, pedir, pedirOpcional, sinRepetidos, todasLasPaginas } from './consultas';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('pedir', () => {
  it('devuelve los datos de un 200', async () => {
    simularApi({ 'GET /api/v1/fichaje/hoy': () => ({ laborable: true }) });
    await expect(pedir(cliente.GET('/api/v1/fichaje/hoy', {}))).resolves.toEqual({ laborable: true });
  });

  it('un error lleva el detail del servidor y su código', async () => {
    simularApi({ 'POST /api/v1/fichaje': () => problema(409, 'Ya hay una jornada activa.') });

    const fallo = await pedir(cliente.POST('/api/v1/fichaje', { body: { tipo: 'INICIO' } })).catch((e: unknown) => e);

    expect(fallo).toBeInstanceOf(ErrorDeApi);
    expect((fallo as ErrorDeApi).message).toBe('Ya hay una jornada activa.');
    expect((fallo as ErrorDeApi).status).toBe(409);
  });

  it('sin detail, el mensaje genérico de ese código', async () => {
    simularApi({ 'GET /api/v1/fichaje/hoy': () => json({}, 403) });
    await expect(pedir(cliente.GET('/api/v1/fichaje/hoy', {}))).rejects.toThrow(T.errores.sinPermisos);
  });

  it('sin respuesta, un error sin código: así se sabe que merece reintentar', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))));

    const fallo = await pedir(cliente.GET('/api/v1/fichaje/hoy', {})).catch((e: unknown) => e);

    expect(fallo).toBeInstanceOf(ErrorDeApi);
    expect((fallo as ErrorDeApi).status).toBeNull();
  });
});

describe('pedirOpcional', () => {
  it('un 204 es «no hay», no un error', async () => {
    simularApi({ 'GET /api/v1/fichaje/activo': () => sinContenido() });
    await expect(pedirOpcional(cliente.GET('/api/v1/fichaje/activo', {}))).resolves.toBeNull();
  });
});

describe('listas por páginas', () => {
  it('sinRepetidos se queda con la primera aparición de cada id', () => {
    expect(sinRepetidos([{ id: 1 }, { id: 2 }, { id: 1 }, { id: 3 }])).toEqual([{ id: 1 }, { id: 2 }, { id: 3 }]);
  });

  /*
   * El caso de ADR 027: entra un elemento mientras se lee, la página 1
   * empieza un puesto más tarde y repite el último de la 0.
   */
  it('todasLasPaginas pide hasta que no hay más y quita el repetido del borde', async () => {
    const paginas = [
      { contenido: [{ id: 5 }, { id: 4 }], hayMas: true },
      { contenido: [{ id: 4 }, { id: 3 }], hayMas: true },
      { contenido: [{ id: 2 }], hayMas: false },
    ];
    const pedidas: number[] = [];

    const todos = await todasLasPaginas(async (n) => {
      pedidas.push(n);
      return paginas[n] ?? { contenido: [], hayMas: false };
    });

    expect(pedidas).toEqual([0, 1, 2]);
    expect(todos.map((e) => e.id)).toEqual([5, 4, 3, 2]);
  });

  it('todasLasPaginas no se cuelga si el servidor dice siempre que hay más', async () => {
    let pedidas = 0;
    await todasLasPaginas(async () => {
      pedidas++;
      return { contenido: [], hayMas: true };
    });
    expect(pedidas).toBe(100);
  });
});

/*
 * El backend manda los campos vacíos como `null` y los tipos generados los
 * declaran opcionales (`undefined`). `cliente.ts` los quita para que los tipos
 * digan la verdad: sin esto, «Mi jornada» se quedaba en blanco al fichar.
 */
describe('los null del servidor', () => {
  it('llegan como campos ausentes, también anidados', async () => {
    simularApi({
      'GET /api/v1/fichaje/proyectos': () => ({ disponibles: [{ id: 1, codigo: 'NX', nombre: null }], enCurso: null }),
    });

    const datos = await pedir(cliente.GET('/api/v1/fichaje/proyectos', {}));

    expect(datos).toEqual({ disponibles: [{ id: 1, codigo: 'NX' }] });
    expect('enCurso' in datos).toBe(false);
  });

  it('dentro de un array se quedan, para no mover los demás', async () => {
    simularApi({ 'GET /api/v1/dashboard/horas-por-dia': () => [null, { fecha: '2026-09-21', festivo: null }] });

    const datos = await pedir(cliente.GET('/api/v1/dashboard/horas-por-dia', { params: { query: { desde: 'a', hasta: 'b' } } }));

    expect(datos).toEqual([null, { fecha: '2026-09-21' }]);
  });
});
