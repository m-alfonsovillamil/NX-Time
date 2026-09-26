/**
 * El nombre del fichero descargado sale de `Content-Disposition`, y con acentos hay que leer `filename*`.
 */

import { describe, expect, it } from 'vitest';

import { nombreDeContentDisposition } from './descargar';

describe('nombreDeContentDisposition', () => {
  it('lee filename entre comillas', () => {
    expect(nombreDeContentDisposition('attachment; filename="horas-2026-09.xlsx"')).toBe('horas-2026-09.xlsx');
  });

  it('lee filename sin comillas', () => {
    expect(nombreDeContentDisposition('attachment; filename=horas.xlsx')).toBe('horas.xlsx');
  });

  it('prefiere filename* y decodifica los acentos', () => {
    const cabecera = `attachment; filename="informe-Jos.pdf"; filename*=UTF-8''informe-Jos%C3%A9-Mu%C3%B1oz.pdf`;
    expect(nombreDeContentDisposition(cabecera)).toBe('informe-José-Muñoz.pdf');
  });

  it('con filename* mal codificado, se queda con filename', () => {
    expect(nombreDeContentDisposition(`attachment; filename="a.pdf"; filename*=UTF-8''%E0%A4%A.pdf`)).toBe('a.pdf');
  });

  it('sin cabecera o sin nombre, null (y quien llama pone el suyo)', () => {
    expect(nombreDeContentDisposition(null)).toBeNull();
    expect(nombreDeContentDisposition('attachment')).toBeNull();
  });
});
