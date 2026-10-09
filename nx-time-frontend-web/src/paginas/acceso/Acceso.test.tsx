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
import { ConfirmarCorreo } from './ConfirmarCorreo';
import { Login } from './Login';
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

const C = T.confirmarCorreo;

describe('entrar sin haber confirmado el correo', () => {
  it('con la contraseña buena y un 403, pide el código que acaba de salir', async () => {
    simularApi({
      'POST /auth/login': () => problema(403, 'Falta confirmar tu correo.'),
    });
    pintar(<Login />);

    await userEvent.type(screen.getByLabelText(T.login.email), 'eva@talleres.test');
    await userEvent.type(screen.getByLabelText(T.login.contrasena), 'unaBuena123');
    await userEvent.click(screen.getByRole('button', { name: T.login.entrar }));

    expect(await screen.findByRole('heading', { name: C.titulo })).toBeTruthy();
    expect(screen.getByText(C.explicacion('eva@talleres.test'))).toBeTruthy();
  });

  it('un código que no son 6 cifras no se manda', async () => {
    const llamadas = simularApi({});
    pintar(<ConfirmarCorreo email="eva@talleres.test" />);

    await userEvent.type(screen.getByLabelText(C.codigo), '12ab');
    await userEvent.click(screen.getByRole('button', { name: C.entrar }));

    expect((await screen.findByRole('alert')).textContent).toBe(C.faltaCodigo);
    expect(llamadas.llamadas).toEqual([]);
  });
});

describe('registrar una empresa', () => {
  /*
   * `origen: 'WEB'` es lo que hace que el refresh vaya a la cookie y dure 12
   * horas; sin él, el registro daba un refresh de 30 días en el cuerpo.
   */
  it('registra, pide el código del correo, y al confirmarlo abre la sesión (origen WEB) y lleva a la jornada', async () => {
    const llamadas = simularApi({
      // Desde la V37 (ADR 034) el registro no abre sesión: 202 y un código al correo.
      'POST /auth/register-manager': () =>
        json({ email: 'eva@talleres.test', mensaje: 'Te hemos mandado un código' }, 202),
      'POST /auth/registro/confirmar': () =>
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

    expect(await screen.findByRole('heading', { name: C.titulo })).toBeTruthy();
    expect(sesionActual()).toBeNull();
    await userEvent.type(screen.getByLabelText(C.codigo), '123 456');
    await userEvent.click(screen.getByRole('button', { name: C.entrar }));

    expect(await screen.findByRole('heading', { name: 'Mi jornada' })).toBeTruthy();
    expect(llamadas.a('POST', '/auth/registro/confirmar').map((l) => l.cuerpo)).toEqual([
      { email: 'eva@talleres.test', codigo: '123456', origen: 'WEB' },
    ]);
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
    // Lo único que sale es la pregunta por los proveedores, al cargar.
    expect(llamadas.llamadas.filter((l) => l.metodo !== 'GET')).toHaveLength(0);
  });
});

/*
 * Registrar con una cuenta de Google o de Microsoft (ADR 038). El viaje al
 * proveedor es del navegador y del servidor: aquí se prueba lo que hace la web
 * antes (a dónde manda) y después (qué pide y qué manda al volver).
 */
describe('registrar una empresa con Google o Microsoft', () => {
  const proveedores = [
    { id: 'google', nombre: 'Google', inicio: 'https://api.nxtime.test/auth/sso/google/iniciar' },
    { id: 'microsoft', nombre: 'Microsoft', inicio: 'https://api.nxtime.test/auth/sso/microsoft/iniciar' },
  ];

  afterEach(() => {
    window.history.replaceState({}, '', '/');
  });

  it('con proveedores, ofrece registrarla con cada uno, y el enlace pide un registro', async () => {
    simularApi({ 'GET /auth/sso/proveedores': () => json(proveedores) });
    pintar(<RegistroEmpresa />);

    const google = await screen.findByRole('link', { name: G.sso.registrarCon('Google') });
    expect(google.getAttribute('href')).toBe('https://api.nxtime.test/auth/sso/google/iniciar?registro=1');
    expect(screen.getByRole('link', { name: G.sso.registrarCon('Microsoft') })).toBeTruthy();
  });

  it('sin proveedores no hay botones: el formulario es el de siempre', async () => {
    simularApi({ 'GET /auth/sso/proveedores': () => json([]) });
    pintar(<RegistroEmpresa />);

    expect(await screen.findByLabelText(G.contrasena)).toBeTruthy();
    expect(screen.queryByRole('group', { name: G.sso.etiqueta })).toBeNull();
  });

  it('al volver del proveedor solo pide la empresa y el nombre, y al mandarlos abre la sesión y lleva a la jornada', async () => {
    window.history.replaceState({}, '', '/registro?sso=continuar');
    const llamadas = simularApi({
      'GET /auth/sso/proveedores': () => json(proveedores),
      'GET /auth/sso/registro': () => json({ proveedor: 'google', nombre: 'Google', correo: 'eva@gmail.test' }),
      'POST /auth/sso/registro': () =>
        json({ token: 'access', refreshToken: null, nombre: 'Eva', rol: 'ADMIN', authorities: ['gestor:crear'] }),
    });
    pintar(
      <Routes>
        <Route path="/" element={<RegistroEmpresa />} />
        <Route path="/fichar" element={<h1>Mi jornada</h1>} />
      </Routes>,
    );

    // Dice con qué cuenta se registra, y no pide ni correo ni contraseña.
    expect(await screen.findByText(G.sso.explicacion('Google', 'eva@gmail.test'))).toBeTruthy();
    expect(screen.queryByLabelText(G.email)).toBeNull();
    expect(screen.queryByLabelText(G.contrasena)).toBeNull();
    // El `?sso=` se quita de la URL: recargar no repite nada.
    expect(window.location.search).toBe('');

    await userEvent.type(screen.getByLabelText(G.empresa), ' Talleres Eva SL ');
    await userEvent.type(screen.getByLabelText(G.nombre), 'Eva');
    await userEvent.type(screen.getByLabelText(G.apellidos), 'Martín');
    await userEvent.click(screen.getByRole('button', { name: G.crear }));

    expect(await screen.findByRole('heading', { name: 'Mi jornada' })).toBeTruthy();
    expect(llamadas.a('POST', '/auth/sso/registro').map((l) => l.cuerpo)).toEqual([
      { nombreEmpresa: 'Talleres Eva SL', nombre: 'Eva', apellidos: 'Martín' },
    ]);
    expect(llamadas.a('POST', '/auth/register-manager')).toHaveLength(0);
    await waitFor(() => expect(sesionActual()?.nombre).toBe('Eva'));
  });

  it('si el nombre de la empresa está cogido se lee lo que dice el servidor y se puede corregir', async () => {
    window.history.replaceState({}, '', '/registro?sso=continuar');
    simularApi({
      'GET /auth/sso/proveedores': () => json(proveedores),
      'GET /auth/sso/registro': () => json({ proveedor: 'google', nombre: 'Google', correo: 'eva@gmail.test' }),
      'POST /auth/sso/registro': () => problema(409, 'La empresa ya existe. Solicita acceso al administrador.'),
    });
    pintar(<RegistroEmpresa />);

    await userEvent.type(await screen.findByLabelText(G.empresa), 'TechCorp');
    await userEvent.type(screen.getByLabelText(G.nombre), 'Eva');
    await userEvent.type(screen.getByLabelText(G.apellidos), 'Martín');
    await userEvent.click(screen.getByRole('button', { name: G.crear }));

    expect((await screen.findByRole('alert')).textContent).toBe(
      'La empresa ya existe. Solicita acceso al administrador.',
    );
    expect(sesionActual()).toBeNull();
    // Sigue en el formulario corto: no hay que volver al proveedor.
    expect(screen.queryByLabelText(G.contrasena)).toBeNull();
  });

  it('si el registro a medias ha caducado lo dice y deja el formulario de siempre', async () => {
    window.history.replaceState({}, '', '/registro?sso=continuar');
    simularApi({
      'GET /auth/sso/proveedores': () => json(proveedores),
      'GET /auth/sso/registro': () => problema(404, 'El registro ha caducado.'),
    });
    pintar(<RegistroEmpresa />);

    expect((await screen.findByRole('alert')).textContent).toBe(G.sso.motivos.caducado);
    expect(screen.getByLabelText(G.contrasena)).toBeTruthy();
  });

  it('si vuelve sin poder seguir, dice por qué con palabras de registrar y no de entrar', async () => {
    window.history.replaceState({}, '', '/registro?sso=correo-sin-verificar');
    simularApi({ 'GET /auth/sso/proveedores': () => json(proveedores) });
    pintar(<RegistroEmpresa />);

    expect((await screen.findByRole('alert')).textContent).toBe(G.sso.motivos['correo-sin-verificar']);
    // Un motivo que esta versión no conoce cae en el genérico (ver `mensajeDeVuelta`).
    expect(G.sso.motivos.fallo).toContain('registra la empresa');
  });

  it('se puede dejar la cuenta de fuera y registrar con correo y contraseña', async () => {
    window.history.replaceState({}, '', '/registro?sso=continuar');
    simularApi({
      'GET /auth/sso/proveedores': () => json(proveedores),
      'GET /auth/sso/registro': () => json({ proveedor: 'google', nombre: 'Google', correo: 'eva@gmail.test' }),
    });
    pintar(<RegistroEmpresa />);

    await userEvent.click(await screen.findByRole('button', { name: G.sso.otraForma }));

    expect(screen.getByLabelText(G.email)).toBeTruthy();
    expect(screen.getByLabelText(G.contrasena)).toBeTruthy();
  });
});

describe('el panel de marca', () => {
  it('las pantallas de acceso presentan la aplicación sin quitarle el título al formulario', () => {
    simularApi({});
    pintar(<Login />);

    // El panel es contenido (se lee), con su nombre; el único encabezado sigue siendo el del formulario.
    const panel = screen.getByRole('complementary', { name: T.marca.etiqueta });
    expect(panel.textContent).toContain(T.marca.lema);
    for (const punto of T.marca.puntos) expect(panel.textContent).toContain(punto.titulo);
    expect(screen.getAllByRole('heading')).toHaveLength(1);
  });
});
