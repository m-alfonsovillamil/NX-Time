// @vitest-environment-options {"url": "https://nxtime-web.test/"}
// HTTPS: la cookie CSRF lleva el prefijo __Secure-, y jsdom, como un navegador, no la acepta por http.
/**
 * El segundo factor en la web (ADR 051): el código al entrar, la pantalla de
 * quien está obligado, activarlo desde Ajustes, el 403 que lo pide a mitad de
 * sesión y quitárselo a alguien desde la plantilla.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { cliente, reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion, sesionActual } from '../../api/sesion';
import { T } from '../../i18n/es';
import { cuenta } from '../../i18n/es/cuenta';
import { fichar } from '../../i18n/es/fichar';
import { plantilla } from '../../i18n/es/plantilla';
import { json, pintar, problema, sesionDe, simularApi, sinContenido, type Manejador, type Ruta } from '../../pruebas/api';
import { App } from '../../rutas/rutas';
import { pareceUnCodigo } from '../acceso/SegundoPaso';
import { Plantilla } from '../plantilla/Plantilla';
import { Ajustes } from './Ajustes';
import { enGrupos } from './SegundoFactor';

const S = cuenta.ajustes.segundoFactor;
const QR = '<svg xmlns="http://www.w3.org/2000/svg"><rect width="1" height="1"/></svg>';
const CODIGOS = ['abcde-23456', 'fghjk-34567', 'mnpqr-45678'];

/** Lo que el marco y la jornada piden siempre al abrirse. */
function apiBasica(extra: Partial<Record<Ruta, Manejador>> = {}) {
  return simularApi({
    'GET /api/v1/fichaje/activo': () => sinContenido(),
    'GET /api/v1/fichaje/hoy': () => ({ laborable: true }),
    'GET /api/v1/avisos/no-leidos': () => ({ noLeidos: 0 }),
    'GET /api/v1/perfil/aviso-de-privacidad': () => sinContenido(),
    ...extra,
  });
}

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = '__Secure-nx_csrf=; Max-Age=0; Secure';
});

describe('el código al entrar', () => {
  it('con segundo factor, la contraseña lleva al código, y el código abre la sesión', async () => {
    const llamadas = apiBasica({
      'POST /auth/login': () => ({ desafio: 'el-desafio', authorities: [] }),
      'POST /auth/2fa': () => ({ token: 't', nombre: 'Ana', authorities: ['fichaje:leer', 'fichaje:escribir'] }),
    });
    pintar(<App />, { ruta: '/' });

    await userEvent.type(await screen.findByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.type(screen.getByLabelText(T.login.contrasena), 'una frase larga');
    await userEvent.click(screen.getByRole('button', { name: T.login.entrar }));

    // Sin sesión todavía: falta el código.
    expect(await screen.findByRole('heading', { name: T.segundoPaso.titulo })).toBeTruthy();
    expect(sesionActual()).toBeNull();

    await userEvent.type(screen.getByLabelText(T.segundoPaso.codigo), 'nada');
    await userEvent.click(screen.getByRole('button', { name: T.segundoPaso.entrar }));
    expect(screen.getByText(T.segundoPaso.faltaCodigo)).toBeTruthy();
    expect(llamadas.a('POST', '/auth/2fa')).toHaveLength(0);

    await userEvent.clear(screen.getByLabelText(T.segundoPaso.codigo));
    await userEvent.type(screen.getByLabelText(T.segundoPaso.codigo), '123 456');
    await userEvent.click(screen.getByRole('button', { name: T.segundoPaso.entrar }));

    expect(await screen.findByRole('heading', { name: fichar.titulo })).toBeTruthy();
    expect(llamadas.a('POST', '/auth/2fa')[0]?.cuerpo).toEqual({ desafio: 'el-desafio', codigo: '123 456', origen: 'WEB' });
  });

  it('un código que no vale se dice, con el mensaje del servidor', async () => {
    apiBasica({
      'POST /auth/login': () => ({ desafio: 'd' }),
      'POST /auth/2fa': () => problema(401, 'El código no es correcto.'),
    });
    pintar(<App />, { ruta: '/' });
    await userEvent.type(await screen.findByLabelText(T.login.email), 'ana@nxtime.test');
    await userEvent.type(screen.getByLabelText(T.login.contrasena), 'una frase larga');
    await userEvent.click(screen.getByRole('button', { name: T.login.entrar }));
    await userEvent.type(await screen.findByLabelText(T.segundoPaso.codigo), '000000');
    await userEvent.click(screen.getByRole('button', { name: T.segundoPaso.entrar }));

    expect(await screen.findByText('El código no es correcto.')).toBeTruthy();
    expect(sesionActual()).toBeNull();
  });

  it('vale un código de la aplicación o uno de recuperación, y nada más', () => {
    expect(pareceUnCodigo('123456')).toBe(true);
    expect(pareceUnCodigo(' 123 456 ')).toBe(true);
    expect(pareceUnCodigo('abcde-23456')).toBe(true);
    expect(pareceUnCodigo('ABCDE23456')).toBe(true);
    expect(pareceUnCodigo('12345')).toBe(false);
    expect(pareceUnCodigo('una contraseña')).toBe(false);
  });
});

describe('quien está obligado y no lo tiene', () => {
  it('solo ve la pantalla de activarlo; al terminar, renueva la sesión y sigue como siempre', async () => {
    const llamadas = apiBasica({
      'GET /api/v1/perfil/segundo-factor': () => ({
        activo: false,
        obligatorio: true,
        motivo: 'Eres operador de la plataforma',
        codigosDeRecuperacion: 0,
      }),
      'POST /api/v1/perfil/segundo-factor/activacion': () => ({ secreto: 'GEZDGNBVGY3TQOJQ', uri: 'otpauth://x', qr: QR }),
      'POST /api/v1/perfil/segundo-factor/activacion/confirmar': () => ({ codigos: CODIGOS }),
      'POST /auth/refresh': () => ({ token: 'nuevo', nombre: 'Olga', authorities: ['fichaje:leer'], segundoFactorPorActivar: false }),
    });
    pintar(<App />, { ruta: '/fichar', sesion: { ...sesionDe('EMPLEADO', 'Olga'), segundoFactorPorActivar: true } });

    expect(await screen.findByRole('heading', { name: S.obligado.titulo })).toBeTruthy();
    expect(await screen.findByText(S.obligado.explicacion('Eres operador de la plataforma'))).toBeTruthy();
    // Nada del marco: lo pediría y el servidor respondería 403.
    expect(llamadas.a('GET', '/api/v1/fichaje/hoy')).toHaveLength(0);

    // La clave, en grupos de cuatro para copiarla a mano.
    expect(await screen.findByText('GEZD GNBV GY3T QOJQ')).toBeTruthy();
    expect(screen.getByRole('img', { name: S.activacion.qr })).toBeTruthy();

    await userEvent.type(screen.getByLabelText(S.activacion.codigo), '123456');
    await userEvent.type(screen.getByLabelText(S.activacion.contrasena), 'una frase larga');
    await userEvent.click(screen.getByRole('button', { name: S.activacion.confirmar }));

    // Los códigos se enseñan antes de soltarle: renovar le saca de aquí.
    const lista = await screen.findByRole('list', { name: S.recuperacion.titulo });
    expect(within(lista).getAllByRole('listitem').map((li) => li.textContent)).toEqual(CODIGOS);
    expect(llamadas.a('POST', '/auth/refresh')).toHaveLength(0);
    expect(llamadas.a('POST', '/api/v1/perfil/segundo-factor/activacion/confirmar')[0]?.cuerpo).toEqual({
      codigo: '123456',
      contrasena: 'una frase larga',
    });

    const listo = screen.getByRole('button', { name: S.recuperacion.listo });
    expect((listo as HTMLButtonElement).disabled).toBe(true);
    await userEvent.click(screen.getByLabelText(S.recuperacion.guardados));
    // La cookie de la sesión, ahora y no al principio: al abrir, la web la
    // usaría para retomar la sesión y pisaría la del test.
    document.cookie = '__Secure-nx_csrf=csrf; Secure';
    await userEvent.click(listo);

    expect(await screen.findByRole('heading', { name: fichar.titulo })).toBeTruthy();
    expect(sesionActual()?.segundoFactorPorActivar).toBe(false);
  });

  it('un 403 que lo pide a mitad de sesión la deja marcada para activarlo', async () => {
    simularApi({
      'GET /api/v1/fichaje/hoy': () =>
        json({ status: 403, detail: 'Tienes que activar el segundo factor', codigo: 'SEGUNDO_FACTOR_OBLIGATORIO' }, 403),
    });
    pintar(<></>, { sesion: sesionDe('ADMIN') });

    await cliente.GET('/api/v1/fichaje/hoy', {});

    expect(sesionActual()?.segundoFactorPorActivar).toBe(true);
  });

  it('un 403 cualquiera no la toca', async () => {
    simularApi({ 'GET /api/v1/fichaje/hoy': () => problema(403, 'No tienes permisos.') });
    pintar(<></>, { sesion: sesionDe('ADMIN') });

    await cliente.GET('/api/v1/fichaje/hoy', {});

    expect(sesionActual()?.segundoFactorPorActivar).toBeUndefined();
  });
});

describe('en Ajustes', () => {
  function api(estado: Record<string, unknown>, extra: Partial<Record<Ruta, Manejador>> = {}) {
    return simularApi({
      'GET /api/v1/perfil/borrado': () => sinContenido(),
      'GET /api/v1/perfil/segundo-factor': () => estado,
      ...extra,
    });
  }

  it('activado: cuántos códigos quedan, avisa si son pocos, y se puede desactivar', async () => {
    const llamadas = api(
      { activo: true, desde: '2026-10-20T10:00:00Z', obligatorio: false, codigosDeRecuperacion: 2 },
      { 'POST /api/v1/perfil/segundo-factor/desactivar': () => sinContenido() },
    );
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(S.activo)).toBeTruthy();
    expect(screen.getByText(S.quedan(2))).toBeTruthy();
    expect(screen.getByText(S.pocos)).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: S.desactivar }));
    const dialogo = await screen.findByRole('dialog');
    await userEvent.type(within(dialogo).getByLabelText(S.desactivacion.contrasena), 'una frase larga');
    await userEvent.type(within(dialogo).getByLabelText(S.desactivacion.codigo), 'abcde-23456');
    await userEvent.click(within(dialogo).getByRole('button', { name: S.desactivacion.confirmar }));

    await waitFor(() => expect(llamadas.a('POST', '/api/v1/perfil/segundo-factor/desactivar')).toHaveLength(1));
    expect(llamadas.a('POST', '/api/v1/perfil/segundo-factor/desactivar')[0]?.cuerpo).toEqual({
      contrasena: 'una frase larga',
      codigo: 'abcde-23456',
    });
  });

  it('a quien se le exige no se le ofrece desactivarlo, y se le dice por qué', async () => {
    api({ activo: true, obligatorio: true, motivo: 'Eres el Responsable del canal de denuncias de tu empresa', codigosDeRecuperacion: 10 });
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(S.obligatorio('Eres el Responsable del canal de denuncias de tu empresa'))).toBeTruthy();
    expect(screen.queryByRole('button', { name: S.desactivar })).toBeNull();
    expect(screen.getByRole('button', { name: S.codigosNuevos })).toBeTruthy();
  });

  it('activarlo pide el código de 6 cifras antes de mandar nada', async () => {
    const llamadas = api(
      { activo: false, obligatorio: false, codigosDeRecuperacion: 0 },
      { 'POST /api/v1/perfil/segundo-factor/activacion': () => ({ secreto: 'GEZDGNBV', uri: 'otpauth://x', qr: QR }) },
    );
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: S.activar }));
    const dialogo = await screen.findByRole('dialog');
    expect(await within(dialogo).findByText('GEZD GNBV')).toBeTruthy();
    await userEvent.type(within(dialogo).getByLabelText(S.activacion.codigo), '12');
    await userEvent.click(within(dialogo).getByRole('button', { name: S.activacion.confirmar }));

    expect(within(dialogo).getByText(S.activacion.faltaCodigo)).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/perfil/segundo-factor/activacion/confirmar')).toHaveLength(0);
  });

  it('la clave se agrupa de cuatro en cuatro', () => {
    expect(enGrupos('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ')).toBe('GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ');
    expect(enGrupos('ABCDEF')).toBe('ABCD EF');
  });
});

describe('en la plantilla', () => {
  it('se ve quién lo tiene, y a esa persona se le puede quitar', async () => {
    const P = plantilla;
    const persona = (id: number, nombre: string, segundoFactor: boolean) => ({
      id,
      nombre,
      email: `${nombre.toLowerCase()}@nxtime.test`,
      activo: true,
      horasSemanales: 40,
      diasVacaciones: 22,
      rol: 'EMPLEADO',
      segundoFactor,
    });
    const llamadas = simularApi({
      'GET /api/v1/gestor/plantilla': () => [persona(2, 'Luis', true), persona(3, 'Eva', false)],
      'GET /api/v1/departamentos': () => [],
      'DELETE /api/v1/gestor/empleados/{id}/segundo-factor': () => sinContenido(),
    });
    pintar(<Plantilla />, { sesion: sesionDe('ADMIN') });

    const luis = (await screen.findByText('Luis')).closest('tr') as HTMLElement;
    const eva = screen.getByText('Eva').closest('tr') as HTMLElement;
    expect(within(luis).getByText(P.conSegundoFactor)).toBeTruthy();
    expect(within(eva).queryByText(P.conSegundoFactor)).toBeNull();
    expect(within(eva).queryByRole('button', { name: P.quitarSegundoFactor })).toBeNull();

    await userEvent.click(within(luis).getByRole('button', { name: P.quitarSegundoFactor }));
    const dialogo = await screen.findByRole('dialog');
    await userEvent.click(within(dialogo).getByRole('button', { name: P.quitarSegundoFactor }));

    await waitFor(() => expect(llamadas.a('DELETE', '/api/v1/gestor/empleados/2/segundo-factor')).toHaveLength(1));
  });
});
