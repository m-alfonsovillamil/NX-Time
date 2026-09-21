/**
 * El login, y sobre todo **qué manda por el cable**.
 *
 * Dos campos de ese cuerpo son fáciles de escribir mal y el fallo llega tarde:
 * `contrasena` (con `password` el servidor responde 400, y eso ya costó un
 * rato una vez) y `origen: 'WEB'`, que es lo que hace que este refresh dure 12
 * horas y no 30 días. Sin `origen` el servidor aplica el valor por defecto,
 * `ANDROID`, y un navegador compartido se quedaría con un token de un mes sin
 * que nada lo delate.
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../api/cliente';
import { cerrarSesion, sesionActual } from '../api/sesion';
import { T } from '../i18n/es';
import { Login } from './Login';

function pintar() {
  return render(
    <MemoryRouter>
      <Login />
    </MemoryRouter>,
  );
}

let cuerposEnviados: unknown[] = [];

function servidor(respuesta: Response) {
  cuerposEnviados = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
      const peticion = entrada instanceof Request ? entrada : new Request(entrada, init);
      cuerposEnviados.push(await peticion.clone().json());
      return respuesta.clone();
    }),
  );
}

function json(cuerpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('entrar', () => {
  it('manda contrasena y origen WEB, y guarda las authorities del servidor', async () => {
    servidor(
      json({
        token: 'access',
        refreshToken: 'refresh',
        nombre: 'Ana',
        rol: 'EMPLEADO',
        authorities: ['fichaje:escribir', 'fichaje:leer'],
      }),
    );
    pintar();

    await userEvent.type(screen.getByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.type(screen.getByLabelText(T.login.contrasena), 'unaContrasena123');
    await userEvent.click(screen.getByRole('button', { name: T.login.entrar }));

    await waitFor(() => expect(cuerposEnviados).toHaveLength(1));
    expect(cuerposEnviados[0]).toEqual({
      email: 'ana@nxtime.test',
      contrasena: 'unaContrasena123',
      origen: 'WEB',
    });

    // Las authorities se guardan tal cual llegan: no se deducen del rol.
    await waitFor(() => expect(sesionActual()?.nombre).toBe('Ana'));
    expect(sesionActual()?.authorities).toEqual(['fichaje:escribir', 'fichaje:leer']);
  });

  it('el correo se recorta antes de mandarlo', async () => {
    // Se copia y se pega, y un espacio al final no es otro correo.
    servidor(json({ token: 't', refreshToken: 'r', nombre: 'Ana', authorities: [] }));
    pintar();

    await userEvent.type(screen.getByLabelText(T.login.email), '  ana@nxtime.test  ');
    await userEvent.type(screen.getByLabelText(T.login.contrasena), 'x');
    await userEvent.click(screen.getByRole('button', { name: T.login.entrar }));

    await waitFor(() => expect(cuerposEnviados).toHaveLength(1));
    expect((cuerposEnviados[0] as { email: string }).email).toBe('ana@nxtime.test');
  });

  /*
   * Un 401 aquí son credenciales malas, no una sesión caducada. Decirle
   * "vuelve a entrar" a quien está entrando no ayuda a nadie, así que este
   * caso no usa el mensaje genérico del 401.
   */
  it('un 401 habla de credenciales, no de sesión caducada', async () => {
    servidor(json({ detail: 'Credenciales incorrectas.' }, 401));
    pintar();

    await userEvent.type(screen.getByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.type(screen.getByLabelText(T.login.contrasena), 'mal');
    await userEvent.click(screen.getByRole('button', { name: T.login.entrar }));

    expect((await screen.findByRole('alert')).textContent).toBe(T.errores.credenciales);
    expect(sesionActual()).toBeNull();
  });

  it('no manda nada si falta la contraseña', async () => {
    servidor(json({}));
    pintar();

    await userEvent.type(screen.getByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.click(screen.getByRole('button', { name: T.login.entrar }));

    expect((await screen.findByRole('alert')).textContent).toBe(T.login.faltaContrasena);
    expect(cuerposEnviados).toHaveLength(0);
  });
});
