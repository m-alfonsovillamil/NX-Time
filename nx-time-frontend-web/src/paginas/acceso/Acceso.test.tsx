/**
 * Recuperar el acceso con un código y registrar una empresa.
 */

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion, sesionActual } from '../../api/sesion';
import { T } from '../../i18n/es';
import { json, pintar, problema, simularApi, sinContenido } from '../../pruebas/api';
import { RecuperarAcceso } from './RecuperarAcceso';
import { RegistroEmpresa } from './RegistroEmpresa';

const R = T.recuperar;
const G = T.registro;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('recuperar el acceso', () => {
  /* Responde igual tenga cuenta o no: la pantalla tampoco lo delata. */
  it('pedir el código y elegir la contraseña, de punta a punta', async () => {
    const llamadas = simularApi({
      'POST /auth/recuperar': () => new Response(null, { status: 202 }),
      'POST /auth/recuperar/confirmar': () => sinContenido(),
    });
    pintar(<RecuperarAcceso />);

    await userEvent.type(screen.getByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.click(screen.getByRole('button', { name: R.enviarCodigo }));
    expect(await screen.findByText(R.codigoEnviado('ana@nxtime.test'))).toBeTruthy();

    await userEvent.type(screen.getByLabelText(R.codigo), '12a3456');
    expect((screen.getByLabelText(R.codigo) as HTMLInputElement).value).toBe('123456');
    await userEvent.type(screen.getByLabelText(R.nueva), 'unaBuena123');
    await userEvent.type(screen.getByLabelText(R.repetir), 'unaBuena123');
    await userEvent.click(screen.getByRole('button', { name: R.guardar }));

    expect(await screen.findByRole('heading', { name: R.hechoTitulo })).toBeTruthy();
    expect(llamadas.a('POST', '/auth/recuperar').map((l) => l.cuerpo)).toEqual([{ email: 'ana@nxtime.test' }]);
    expect(llamadas.a('POST', '/auth/recuperar/confirmar').map((l) => l.cuerpo)).toEqual([
      { email: 'ana@nxtime.test', codigo: '123456', contrasenaNueva: 'unaBuena123' },
    ]);
  });

  it('«Ya tengo un código» va directo al código, sin pedir otro', async () => {
    const llamadas = simularApi({});
    pintar(<RecuperarAcceso />);

    await userEvent.type(screen.getByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.click(screen.getByRole('button', { name: R.yaTengoCodigo }));

    expect(await screen.findByText(R.codigoExplicacion)).toBeTruthy();
    expect(llamadas.llamadas).toHaveLength(0);
  });

  it('un código que no son 6 dígitos no se manda', async () => {
    const llamadas = simularApi({});
    pintar(<RecuperarAcceso />);

    await userEvent.type(screen.getByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.click(screen.getByRole('button', { name: R.yaTengoCodigo }));
    await userEvent.type(await screen.findByLabelText(R.codigo), '123');
    await userEvent.click(screen.getByRole('button', { name: R.guardar }));

    expect((await screen.findByRole('alert')).textContent).toBe(R.codigoIncompleto);
    expect(llamadas.llamadas).toHaveLength(0);
  });

  it('un código malo se lee con el mensaje del servidor', async () => {
    simularApi({
      'POST /auth/recuperar/confirmar': () => problema(400, 'El código no es válido o ha caducado.'),
    });
    pintar(<RecuperarAcceso />);

    await userEvent.type(screen.getByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.click(screen.getByRole('button', { name: R.yaTengoCodigo }));
    await userEvent.type(await screen.findByLabelText(R.codigo), '000000');
    await userEvent.type(screen.getByLabelText(R.nueva), 'unaBuena123');
    await userEvent.type(screen.getByLabelText(R.repetir), 'unaBuena123');
    await userEvent.click(screen.getByRole('button', { name: R.guardar }));

    expect((await screen.findByRole('alert')).textContent).toBe('El código no es válido o ha caducado.');
  });
});

describe('registrar una empresa', () => {
  /*
   * `origen: 'WEB'` es lo que hace que el refresh vaya a la cookie y dure 12
   * horas; sin él, el registro daba un refresh de 30 días en el cuerpo.
   */
  it('manda origen WEB, abre la sesión y lleva a la jornada', async () => {
    const llamadas = simularApi({
      'POST /auth/register-manager': () =>
        json({ token: 'access', refreshToken: null, nombre: 'Eva', rol: 'ADMIN', authorities: ['gestor:crear'] }),
    });
    pintar(
      <Routes>
        <Route path="/" element={<RegistroEmpresa />} />
        <Route path="/fichar" element={<h1>Mi jornada</h1>} />
      </Routes>,
    );

    await userEvent.type(screen.getByLabelText(G.empresa), 'Talleres Eva SL');
    await userEvent.type(screen.getByLabelText(G.nombre), 'Eva');
    await userEvent.type(screen.getByLabelText(G.apellidos), 'Martín');
    await userEvent.type(screen.getByLabelText(G.email), ' eva@talleres.test ');
    await userEvent.type(screen.getByLabelText(G.contrasena), 'unaBuena123');
    await userEvent.click(screen.getByRole('button', { name: G.crear }));

    expect(await screen.findByRole('heading', { name: 'Mi jornada' })).toBeTruthy();
    expect(llamadas.a('POST', '/auth/register-manager').map((l) => l.cuerpo)).toEqual([
      {
        nombreEmpresa: 'Talleres Eva SL',
        nombre: 'Eva',
        apellidos: 'Martín',
        email: 'eva@talleres.test',
        contrasena: 'unaBuena123',
        origen: 'WEB',
      },
    ]);
    await waitFor(() => expect(sesionActual()?.nombre).toBe('Eva'));
  });

  it('con algún campo vacío no se manda nada', async () => {
    const llamadas = simularApi({});
    pintar(<RegistroEmpresa />);

    await userEvent.type(screen.getByLabelText(G.empresa), 'Talleres Eva SL');
    await userEvent.click(screen.getByRole('button', { name: G.crear }));

    expect((await screen.findByRole('alert')).textContent).toBe(G.faltan);
    expect(llamadas.llamadas).toHaveLength(0);
  });
});
