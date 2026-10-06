/**
 * Avisos, mi perfil y ajustes: lo que se manda al servidor y lo que se enseña.
 */

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion, haySesion } from '../../api/sesion';
import { Notificaciones } from '../../componentes/Notificaciones';
import { cuenta } from '../../i18n/es/cuenta';
import { encenderPush, FalloDePush } from '../../push/push';
import { json, pintar, sesionDe, simularApi, sinContenido, type Manejador, type Ruta } from '../../pruebas/api';
import { Ajustes } from './Ajustes';
import { Avisos } from './Avisos';
import { formularioDeSubida, subirAdjunto } from './adjuntos';
import { PaginaPerfil, problemaDelFichero } from './Perfil';

// La lógica del push la prueba push.test.ts; aquí solo la tarjeta de Ajustes.
const push = vi.hoisted(() => ({ estado: 'sin-configurar' as string }));
vi.mock('../../push/push', async (original) => ({
  ...(await original<typeof import('../../push/push')>()),
  estadoPush: () => push.estado,
  encenderPush: vi.fn(async () => {
    push.estado = 'encendido';
  }),
  apagarPush: vi.fn(async () => {
    push.estado = 'apagado';
  }),
}));

vi.mock('./adjuntos', async (original) => ({
  ...(await original<typeof import('./adjuntos')>()),
  subirAdjunto: vi.fn(async () => ({ id: 3, tipo: 'CV', nombreOriginal: 'cv.pdf' })),
}));

const PERFIL = {
  id: 5,
  email: 'ana@nxtime.test',
  nombre: 'Ana',
  apellidos: 'Pérez',
  nombreCompleto: 'Ana Pérez',
  iniciales: 'AP',
  fechaNacimiento: null,
  puesto: null,
  departamentoId: null,
  departamentoNombre: null,
  rol: 'EMPLEADO',
  activo: true,
  horasSemanales: 37.5,
  diasVacaciones: 22,
  authorities: [],
};

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.mocked(subirAdjunto).mockClear();
  document.documentElement.removeAttribute('data-tema');
  localStorage.clear();
});

describe('avisos', () => {
  const AVISOS = {
    contenido: [
      { id: 1, tipo: 'BIENVENIDA', titulo: 'Bienvenida', cuerpo: null, rutaDestino: 'fichar', leido: false, creadoEn: '2026-09-26T08:00:00Z' },
      { id: 2, tipo: 'AUSENCIA_RESUELTA', titulo: 'Ausencia aprobada', cuerpo: 'Que la disfrutes', rutaDestino: 'ausencias', leido: true, creadoEn: '2026-09-20T08:00:00Z' },
    ],
    hayMas: false,
  };

  it('abrir uno sin leer lo marca leído y lleva a su página', async () => {
    const llamadas = simularApi({
      'GET /api/v1/avisos': () => AVISOS,
      'PATCH /api/v1/avisos/{id}/leido': () => sinContenido(),
    });
    pintar(
      <Routes>
        <Route path="/avisos" element={<Avisos />} />
        <Route path="/fichar" element={<h1>Mi jornada</h1>} />
      </Routes>,
      { ruta: '/avisos', sesion: sesionDe('EMPLEADO') },
    );

    await userEvent.click(await screen.findByRole('button', { name: /Bienvenida/ }));

    expect(await screen.findByRole('heading', { name: 'Mi jornada' })).toBeTruthy();
    await waitFor(() => expect(llamadas.a('PATCH', '/api/v1/avisos/1/leido')).toHaveLength(1));
  });

  it('uno ya leído no se vuelve a marcar', async () => {
    const llamadas = simularApi({ 'GET /api/v1/avisos': () => AVISOS });
    pintar(<Avisos />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: /Ausencia aprobada/ }));

    expect(llamadas.llamadas.filter((l) => l.metodo === 'PATCH')).toHaveLength(0);
  });

  it('«Marcar todos» solo sale si hay alguno sin leer', async () => {
    const llamadas = simularApi({
      'GET /api/v1/avisos': () => AVISOS,
      'PATCH /api/v1/avisos/leer-todos': () => sinContenido(),
    });
    pintar(<Avisos />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: cuenta.avisos.marcarTodos }));
    await waitFor(() => expect(llamadas.a('PATCH', '/api/v1/avisos/leer-todos')).toHaveLength(1));
  });
});

describe('mi perfil', () => {
  function api(extra: Partial<Record<Ruta, Manejador>> = {}) {
    return simularApi({
      'GET /api/v1/perfil': () => PERFIL,
      'GET /api/v1/perfil/adjuntos': () => [],
      ...extra,
    });
  }

  it('los datos laborales son de solo lectura, y dice quién los gestiona', async () => {
    api();
    pintar(<PaginaPerfil />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByText(cuenta.perfil.soloRrhh)).toBeTruthy();
    expect(screen.getByText(cuenta.perfil.jornadaValor('37,5'))).toBeTruthy();
    expect(screen.getAllByRole('button', { name: cuenta.perfil.editar })).toHaveLength(1);
  });

  it('editar manda los cuatro datos personales, sin fecha si está vacía', async () => {
    const llamadas = api({ 'PATCH /api/v1/perfil': () => ({ ...PERFIL, puesto: 'Desarrolladora' }) });
    pintar(<PaginaPerfil />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: cuenta.perfil.editar }));
    await userEvent.type(screen.getByLabelText(cuenta.perfil.puesto), 'Desarrolladora');
    await userEvent.click(screen.getByRole('button', { name: cuenta.perfil.guardar }));

    await waitFor(() =>
      expect(llamadas.a('PATCH', '/api/v1/perfil').map((l) => l.cuerpo)).toEqual([
        { nombre: 'Ana', apellidos: 'Pérez', puesto: 'Desarrolladora' },
      ]),
    );
  });

  it('sin nombre no se guarda', async () => {
    const llamadas = api();
    pintar(<PaginaPerfil />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: cuenta.perfil.editar }));
    await userEvent.clear(screen.getByLabelText(cuenta.perfil.nombre));
    await userEvent.click(screen.getByRole('button', { name: cuenta.perfil.guardar }));

    expect((await screen.findByRole('alert')).textContent).toBe(cuenta.perfil.nombreObligatorio);
    expect(llamadas.a('PATCH', '/api/v1/perfil')).toHaveLength(0);
  });

  /*
   * Si es un PDF de verdad lo decide el servidor por sus primeros bytes; aquí,
   * que se manda el fichero elegido con su tipo. El viaje como multipart se
   * comprueba en Playwright (ver adjuntos.ts).
   */
  it('elegir un CV lo sube con su tipo', async () => {
    api();
    pintar(<PaginaPerfil />, { sesion: sesionDe('EMPLEADO') });

    const fichero = new File(['%PDF-1.7 contenido'], 'cv.pdf', { type: 'application/pdf' });
    await userEvent.upload(await screen.findByLabelText(cuenta.perfil.cv.subir), fichero);

    await waitFor(() => expect(subirAdjunto).toHaveBeenCalledWith('CV', fichero));
  });

  it('el formulario lleva el fichero en el campo «fichero», con su nombre', () => {
    const fichero = new File(['%PDF'], 'cv.pdf', { type: 'application/pdf' });
    const enviado = formularioDeSubida(fichero).get('fichero') as File;
    expect(enviado.name).toBe('cv.pdf');
  });

  it('un CV vacío no se sube', async () => {
    api();
    pintar(<PaginaPerfil />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.upload(await screen.findByLabelText(cuenta.perfil.cv.subir), new File([], 'vacio.pdf', { type: 'application/pdf' }));

    expect((await screen.findByRole('alert')).textContent).toBe(cuenta.perfil.adjuntoVacio);
    expect(subirAdjunto).not.toHaveBeenCalled();
  });

  it('un fichero vacío o de más de 5 MB se para antes de subir', () => {
    expect(problemaDelFichero(new File([], 'vacio.pdf'))).toBe(cuenta.perfil.adjuntoVacio);
    expect(problemaDelFichero(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'grande.pdf'))).toBe(cuenta.perfil.adjuntoGrande);
    expect(problemaDelFichero(new File(['%PDF'], 'bien.pdf'))).toBeNull();
  });
});

describe('ajustes', () => {
  function api(extra: Partial<Record<Ruta, Manejador>> = {}) {
    return simularApi({ 'GET /api/v1/perfil/borrado': () => sinContenido(), ...extra });
  }

  it('las notificaciones: sin configurar se dice, y encenderlas cambia la tarjeta', async () => {
    const N = cuenta.ajustes.notificaciones;
    api();
    push.estado = 'sin-configurar';
    const { unmount } = pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });
    expect(screen.getByText(N.detalle['sin-configurar'])).toBeTruthy();
    expect(screen.queryByRole('button', { name: N.encender })).toBeNull();
    unmount();

    push.estado = 'apagado';
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });
    await userEvent.click(screen.getByRole('button', { name: N.encender }));

    expect(await screen.findByText(N.encendidas)).toBeTruthy();
    expect(screen.getByRole('button', { name: N.apagar })).toBeTruthy();
  });

  it('las notificaciones: el botón dice por qué paso va, y si uno falla, cuál y por qué', async () => {
    const N = cuenta.ajustes.notificaciones;
    api();
    push.estado = 'apagado';
    let fallar: (e: unknown) => void = () => undefined;
    vi.mocked(encenderPush).mockImplementationOnce(async (alAvanzar) => {
      alAvanzar?.('token');
      await new Promise((_, rechazar) => {
        fallar = rechazar;
      });
    });
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(screen.getByRole('button', { name: N.encender }));
    expect(await screen.findByRole('button', { name: N.pasos.token })).toBeTruthy();

    fallar(new FalloDePush('token', 'Registration failed - push service error'));

    const aviso = await screen.findByRole('alert');
    expect(aviso.textContent).toContain(N.fallos.token);
    expect(aviso.textContent).toContain(N.detalleTecnico('Registration failed - push service error'));
    expect(screen.getByRole('button', { name: N.reintentar })).toBeTruthy();
  });

  it('el tema se aplica en el acto y se recuerda', async () => {
    api();
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.selectOptions(screen.getByLabelText(cuenta.ajustes.tema), 'oscuro');

    expect(document.documentElement.dataset['tema']).toBe('oscuro');
    expect(localStorage.getItem('nx-tema')).toBe('oscuro');

    await userEvent.selectOptions(screen.getByLabelText(cuenta.ajustes.tema), 'sistema');
    expect(document.documentElement.dataset['tema']).toBeUndefined();
    expect(localStorage.getItem('nx-tema')).toBeNull();
  });

  it('cambiar la contraseña comprueba largo y repetición antes de mandar nada', async () => {
    const llamadas = api({ 'POST /api/v1/usuario/cambiar-contrasena': () => json({}) });
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });
    const C = cuenta.ajustes.contrasena;

    await userEvent.click(screen.getByRole('button', { name: C.boton }));
    const dialogo = await screen.findByRole('dialog', { name: C.titulo });
    await userEvent.type(within(dialogo).getByLabelText(C.actual), 'vieja');
    await userEvent.type(within(dialogo).getByLabelText(C.nueva), 'corta');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.guardar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(C.corta);

    await userEvent.clear(within(dialogo).getByLabelText(C.nueva));
    await userEvent.type(within(dialogo).getByLabelText(C.nueva), 'unaBuena123');
    await userEvent.type(within(dialogo).getByLabelText(C.repetir), 'otraDistinta');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.guardar }));
    expect((await within(dialogo).findByRole('alert')).textContent).toBe(C.noCoinciden);
    expect(llamadas.a('POST', '/api/v1/usuario/cambiar-contrasena')).toHaveLength(0);

    await userEvent.clear(within(dialogo).getByLabelText(C.repetir));
    await userEvent.type(within(dialogo).getByLabelText(C.repetir), 'unaBuena123');
    await userEvent.click(within(dialogo).getByRole('button', { name: C.guardar }));
    await waitFor(() =>
      expect(llamadas.a('POST', '/api/v1/usuario/cambiar-contrasena').map((l) => l.cuerpo)).toEqual([
        { contrasenaAntigua: 'vieja', contrasenaNueva: 'unaBuena123' },
      ]),
    );
  });

  /* Revoca todas, también esta: quedarse «dentro» con una sesión que no se puede renovar confundiría. */
  it('cerrar todas las sesiones pide confirmación y después cierra también esta', async () => {
    const llamadas = api({ 'POST /api/v1/usuario/cerrar-sesiones': () => json({}) });
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(screen.getByRole('button', { name: cuenta.ajustes.cerrarTodas.boton }));
    const dialogo = await screen.findByRole('dialog', { name: cuenta.ajustes.cerrarTodas.titulo });
    expect(llamadas.a('POST', '/api/v1/usuario/cerrar-sesiones')).toHaveLength(0);
    await userEvent.click(within(dialogo).getByRole('button', { name: cuenta.ajustes.cerrarTodas.confirmar }));

    await waitFor(() => expect(haySesion()).toBe(false));
    expect(llamadas.a('POST', '/api/v1/usuario/cerrar-sesiones')).toHaveLength(1);
  });

  /*
   * La copia de mis datos es legal: tiene que salir como está guardada. El
   * cliente quita los null de las respuestas JSON para las pantallas, y aquí
   * no debe hacerlo.
   */
  it('el JSON de mis datos se descarga tal cual, con sus null', async () => {
    api({ 'GET /api/v1/perfil/mis-datos': () => json({ perfil: { puesto: null } }) });
    let descargado: Blob | null = null;
    vi.stubGlobal('URL', Object.assign(URL, {
      createObjectURL: vi.fn((blob: Blob) => {
        descargado = blob;
        return 'blob:nx';
      }),
      revokeObjectURL: vi.fn(),
    }));
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(screen.getByRole('button', { name: cuenta.ajustes.misDatos.json }));

    await waitFor(() => expect(descargado).not.toBeNull());
    expect(await (descargado as unknown as Blob).text()).toBe('{"perfil":{"puesto":null}}');
  });

  it('pedir el borrado explica qué se pierde, y manda el motivo si lo hay', async () => {
    const llamadas = api({
      'POST /api/v1/perfil/borrado': () => json({ id: 1, estado: 'PENDIENTE', creadaEn: '2026-09-27T10:00:00Z' }, 201),
    });
    pintar(
      <>
        <Ajustes />
        <Notificaciones />
      </>,
      { sesion: sesionDe('EMPLEADO') },
    );
    const B = cuenta.ajustes.borrado;

    await userEvent.click(await screen.findByRole('button', { name: B.pedir }));
    const dialogo = await screen.findByRole('dialog', { name: B.confirmarTitulo });
    expect(within(dialogo).getByText(B.confirmarDetalle[2])).toBeTruthy();
    await userEvent.type(within(dialogo).getByLabelText(B.motivo), 'Me voy de la empresa');
    await userEvent.click(within(dialogo).getByRole('button', { name: B.pedir }));

    expect(await screen.findByText(B.enviada)).toBeTruthy();
    expect(llamadas.a('POST', '/api/v1/perfil/borrado').map((l) => l.cuerpo)).toEqual([{ motivo: 'Me voy de la empresa' }]);
  });

  it('con una solicitud pendiente, se puede retirar', async () => {
    const llamadas = api({
      'GET /api/v1/perfil/borrado': () => ({ id: 1, estado: 'PENDIENTE', creadaEn: '2026-09-20T10:00:00Z', comentarioResolucion: null }),
      'POST /api/v1/perfil/borrado/cancelar': () => ({ id: 1, estado: 'CANCELADA' }),
    });
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: cuenta.ajustes.borrado.retirar }));

    await waitFor(() => expect(llamadas.a('POST', '/api/v1/perfil/borrado/cancelar')).toHaveLength(1));
  });
});

/* Las cuentas de Google o de Microsoft con las que se entra (ADR 036). */
describe('cuentas vinculadas', () => {
  const V = cuenta.ajustes.vinculadas;
  const GOOGLE = { id: 'google', nombre: 'Google', inicio: 'https://api.nxtime-web.com/auth/sso/google/iniciar' };
  const MICROSOFT = { id: 'microsoft', nombre: 'Microsoft', inicio: 'https://api.nxtime-web.com/auth/sso/microsoft/iniciar' };
  const DE_GOOGLE = {
    proveedor: 'google',
    nombre: 'Google',
    correo: 'ana@gmail.com',
    vinculadaEn: '2026-10-06T10:00:00Z',
    ultimoAcceso: null,
  };

  function api(extra: Partial<Record<Ruta, Manejador>> = {}) {
    return simularApi({
      'GET /api/v1/perfil/borrado': () => sinContenido(),
      'GET /auth/sso/proveedores': () => [GOOGLE, MICROSOFT],
      'GET /api/v1/perfil/identidades': () => [DE_GOOGLE],
      ...extra,
    });
  }

  afterEach(() => {
    window.history.replaceState({}, '', '/');
  });

  it('enseña la vinculada con su correo y, de la que no lo está, el enlace para vincularla', async () => {
    api();
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    const tarjeta = (await screen.findByRole('heading', { name: V.titulo })).closest('section') as HTMLElement;
    expect(await within(tarjeta).findByText(/ana@gmail\.com/)).toBeTruthy();
    expect(within(tarjeta).getByRole('button', { name: V.desvincularDe('Google') })).toBeTruthy();
    // Vincular es una navegación al servidor, con la marca de que es para vincular.
    const vincular = await within(tarjeta).findByRole('link', { name: V.vincular('Microsoft') });
    expect(vincular.getAttribute('href')).toBe(MICROSOFT.inicio + '?vincular=1');
    // Y sale en el índice de la página.
    expect(screen.getByRole('link', { name: V.titulo })).toBeTruthy();
  });

  it('desvincular pide confirmación y borra la de ese proveedor', async () => {
    let vinculadas = [DE_GOOGLE];
    const llamadas = api({
      'GET /api/v1/perfil/identidades': () => vinculadas,
      'DELETE /api/v1/perfil/identidades/{proveedor}': () => {
        vinculadas = [];
        return sinContenido();
      },
    });
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    await userEvent.click(await screen.findByRole('button', { name: V.desvincularDe('Google') }));
    expect(llamadas.a('DELETE', '/api/v1/perfil/identidades/google')).toHaveLength(0);
    const dialogo = await screen.findByRole('dialog', { name: V.confirmarTitulo('Google') });
    await userEvent.click(within(dialogo).getByRole('button', { name: V.desvincular }));

    await waitFor(() => expect(llamadas.a('DELETE', '/api/v1/perfil/identidades/google')).toHaveLength(1));
    // Vuelve a pedirse la lista, y Google pasa a poder vincularse.
    expect(await screen.findByRole('link', { name: V.vincular('Google') })).toBeTruthy();
  });

  it('sin proveedores y sin ninguna vinculada, la sección no existe', async () => {
    const llamadas = api({
      'GET /auth/sso/proveedores': () => [],
      'GET /api/v1/perfil/identidades': () => [],
    });
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    await waitFor(() => expect(llamadas.a('GET', '/api/v1/perfil/identidades')).toHaveLength(1));
    await waitFor(() => expect(llamadas.a('GET', '/auth/sso/proveedores')).toHaveLength(1));
    expect(screen.queryByRole('heading', { name: V.titulo })).toBeNull();
    expect(screen.queryByRole('link', { name: V.titulo })).toBeNull();
  });

  /* Un proveedor que se apaga en el servidor no deja a nadie con una cuenta que no puede quitar. */
  it('una ya vinculada de un proveedor apagado se sigue viendo, para poder quitarla', async () => {
    api({ 'GET /auth/sso/proveedores': () => [] });
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    expect(await screen.findByRole('button', { name: V.desvincularDe('Google') })).toBeTruthy();
    expect(screen.queryByRole('link', { name: V.vincular('Microsoft') })).toBeNull();
  });

  it('al volver sin haber podido vincular, dice por qué', async () => {
    window.history.replaceState({}, '', '/ajustes?sso=ya-vinculada');
    api();
    pintar(<Ajustes />, { sesion: sesionDe('EMPLEADO') });

    expect((await screen.findByText(V.motivos['ya-vinculada'])).getAttribute('role')).toBe('alert');
    await waitFor(() => expect(window.location.search).toBe(''));
  });
});
