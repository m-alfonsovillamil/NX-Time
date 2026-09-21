import { describe, expect, it } from 'vitest';

import { T } from '../i18n/es';
import { detalleDelProblema, mensajeDeError, mensajeDeRed, mensajeGenerico } from './errores';

describe('el mensaje de un error del servidor', () => {
  it('prefiere el detail del ProblemDetail, que está escrito para leerse', () => {
    const cuerpo = {
      type: 'about:blank',
      title: 'Conflict',
      status: 409,
      detail: 'Ya hay una jornada activa.',
      instance: '/api/v1/fichaje',
    };

    expect(mensajeDeError(cuerpo, 409)).toBe('Ya hay una jornada activa.');
  });

  /*
   * El cuerpo de un error no siempre lo escribe la aplicación: un proxy o un
   * balanceador pueden responder HTML, y Render lo hace mientras levanta la
   * instancia. Quedarse sin mensaje no puede convertirse en un error de
   * JavaScript justo cuando algo ya ha ido mal.
   */
  it.each([
    ['nada', undefined],
    ['null', null],
    ['una cadena suelta', '<html>502 Bad Gateway</html>'],
    ['un objeto sin detail', { title: 'Conflict' }],
    ['un detail que no es texto', { detail: 42 }],
    ['un detail en blanco', { detail: '   ' }],
  ])('cae en el genérico si el cuerpo es %s', (_caso, cuerpo) => {
    expect(detalleDelProblema(cuerpo)).toBeNull();
    expect(mensajeDeError(cuerpo, 500)).toBe(T.errores.servidor);
  });

  it('el genérico explica qué hacer, no repite el número', () => {
    expect(mensajeGenerico(400)).toBe(T.errores.datosInvalidos);
    expect(mensajeGenerico(403)).toBe(T.errores.sinPermisos);
    expect(mensajeGenerico(429)).toBe(T.errores.demasiadosIntentos);
    // Cualquier 5xx, no solo el 500: Render responde 502 y 503 al despertar.
    expect(mensajeGenerico(502)).toBe(T.errores.servidor);
    expect(mensajeGenerico(503)).toBe(T.errores.servidor);
    expect(mensajeGenerico(418)).toBe(T.errores.inesperado);
  });
});

describe('el mensaje de un fallo de red', () => {
  /*
   * Un timeout aquí no es "lo has cancelado tú": es la espera larga del
   * arranque en frío agotada, así que lo correcto es hablar del servidor y no
   * de la conexión de quien lo lee.
   */
  it('un timeout se cuenta como servidor que no responde', () => {
    expect(mensajeDeRed(new DOMException('', 'TimeoutError'))).toBe(T.errores.servidor);
    expect(mensajeDeRed(new DOMException('', 'AbortError'))).toBe(T.errores.servidor);
  });

  it('lo demás es un problema de conexión', () => {
    expect(mensajeDeRed(new TypeError('Failed to fetch'))).toBe(T.errores.red);
  });
});
