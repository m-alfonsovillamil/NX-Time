/**
 * Un servidor de mentira para los tests de páginas, **tipado con el contrato**.
 *
 * Las claves son `'GET /api/v1/fichaje/activo'`, con la ruta tal y como está
 * en `schema.d.ts`: una ruta mal escrita no compila, igual que en `cliente`.
 * Lo que no se simula responde **404 y queda anotado** en `sinSimular`, para
 * que una página que pide algo inesperado falle con un mensaje que diga qué.
 *
 * Es el patrón que `cliente.test.ts` y `Login.test.tsx` escribían a mano,
 * juntado aquí para las treinta páginas que vienen (ADR 029).
 */

import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router';
import { vi } from 'vitest';

import type { paths } from '../api/schema';
import { crearClienteDeConsultas } from '../api/consultas';
import { abrirSesion, type Sesion } from '../api/sesion';

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
export type Ruta = `${Metodo} ${keyof paths & string}`;

export interface PeticionSimulada {
  metodo: Metodo;
  ruta: string;
  parametros: Record<string, string>;
  query: URLSearchParams;
  cuerpo: unknown;
}

/** Lo que devuelve un manejador: una `Response` a medida, o un cuerpo que se manda como JSON 200. */
export type Manejador = (peticion: PeticionSimulada) => Response | unknown | Promise<Response | unknown>;

export function json(cuerpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
}

export function sinContenido(): Response {
  return new Response(null, { status: 204 });
}

export function problema(status: number, detail: string): Response {
  return json({ type: 'about:blank', status, detail }, status);
}

/** `/api/v1/avisos/{id}/leido` → una expresión que casa `/api/v1/avisos/7/leido` y saca `id`. */
function aExpresion(plantilla: string): { expresion: RegExp; nombres: string[] } {
  const nombres: string[] = [];
  const fuente = plantilla.replace(/[.*+?^$()|[\]\\]/g, '\\$&').replace(/\\?\{(\w+)\\?\}/g, (_, nombre: string) => {
    nombres.push(nombre);
    return '([^/]+)';
  });
  return { expresion: new RegExp(`^${fuente}$`), nombres };
}

export function simularApi(rutas: Partial<Record<Ruta, Manejador>>) {
  const llamadas: PeticionSimulada[] = [];
  const sinSimular: string[] = [];
  const tabla = Object.entries(rutas).map(([clave, manejador]) => {
    const [metodo, plantilla] = clave.split(' ') as [Metodo, string];
    return { metodo, ...aExpresion(plantilla), manejador: manejador as Manejador };
  });

  vi.stubGlobal(
    'fetch',
    vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
      const peticion = entrada instanceof Request ? entrada : new Request(entrada, init);
      const url = new URL(peticion.url);
      const metodo = peticion.method.toUpperCase() as Metodo;
      const texto = await peticion.clone().text();
      let cuerpo: unknown = undefined;
      if (texto !== '') {
        try {
          cuerpo = JSON.parse(texto);
        } catch {
          cuerpo = texto;
        }
      }

      for (const fila of tabla) {
        if (fila.metodo !== metodo) continue;
        const casa = fila.expresion.exec(url.pathname);
        if (casa === null) continue;
        const parametros = Object.fromEntries(fila.nombres.map((n, i) => [n, casa[i + 1] ?? '']));
        const simulada = { metodo, ruta: url.pathname, parametros, query: url.searchParams, cuerpo };
        llamadas.push(simulada);
        const respuesta = await fila.manejador(simulada);
        return respuesta instanceof Response ? respuesta : json(respuesta);
      }

      sinSimular.push(`${metodo} ${url.pathname}`);
      return problema(404, `Sin simular en el test: ${metodo} ${url.pathname}`);
    }),
  );

  return {
    llamadas,
    sinSimular,
    /** Las llamadas a una ruta concreta (ya resuelta, sin plantillas). */
    a: (metodo: Metodo, ruta: string) => llamadas.filter((l) => l.metodo === metodo && l.ruta === ruta),
  };
}

/* ------------------------------------------------------------------ */
/* Sesiones de prueba                                                   */
/* ------------------------------------------------------------------ */

/*
 * Copias a mano del reparto de `RoleAuthorities.java`, solo para los tests.
 * La web no deduce nada del rol (lo manda el servidor); esto solo sirve para
 * pintar una página como la vería cada cuenta de demo.
 */
const EMPLEADO = [
  'adjunto:subir', 'ausencia:escribir', 'ausencia:leer', 'calendario:leer', 'candidatura:crear',
  'correccion:solicitar', 'cuadrante:leer', 'denuncia:crear', 'fichaje:escribir', 'fichaje:leer',
  'oferta:leer', 'proyecto:leer',
];
const GESTOR = [
  ...EMPLEADO, 'analitica:leer', 'ausencia:aprobar', 'ausencia:leer:equipo', 'calendario:gestionar',
  'candidatura:gestionar', 'correccion:aprobar', 'cuadrante:gestionar', 'cuadrante:incidencias:revisar',
  'empleado:crear', 'empleado:leer', 'fichaje:leer:equipo', 'horasextra:revisar', 'oferta:publicar',
  'proyecto:gestionar',
];
const RRHH = [
  ...GESTOR, 'correccion:disputa:resolver', 'departamento:gestionar', 'empleado:configurar',
  'empleado:gestionar', 'fichaje:auditoria', 'fichaje:corregir', 'firma:visar', 'informe:exportar',
];
const ADMIN = [...RRHH, 'denuncia:instruir', 'gestor:crear'];

export const AUTHORITIES = { EMPLEADO, GESTOR, RRHH, ADMIN } as const;

export function sesionDe(rol: keyof typeof AUTHORITIES, nombre = 'Ana'): Sesion {
  return { accessToken: 'access', refreshToken: 'refresh', nombre, authorities: AUTHORITIES[rol] };
}

/**
 * Pinta algo con lo que tiene la aplicación de verdad alrededor: el router, y
 * una caché de datos **nueva en cada test** y sin reintentos (un 500 simulado
 * tiene que verse ya, no tras el reintento).
 */
export function pintar(ui: ReactElement, { ruta = '/', sesion }: { ruta?: string; sesion?: Sesion } = {}) {
  if (sesion !== undefined) abrirSesion(sesion);
  return render(
    <QueryClientProvider client={crearClienteDeConsultas({ sinReintentos: true })}>
      <MemoryRouter initialEntries={[ruta]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}
