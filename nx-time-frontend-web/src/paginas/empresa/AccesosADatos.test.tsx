/** El registro de accesos a datos personales, para el ADMIN (ADR 047). */

import { screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reiniciarEstadoDeRed } from '../../api/cliente';
import { cerrarSesion } from '../../api/sesion';
import { empresa } from '../../i18n/es/empresa';
import { pintar, sesionDe, simularApi } from '../../pruebas/api';
import { AccesosADatos } from './AccesosADatos';

const A = empresa.accesos;

beforeEach(() => {
  reiniciarEstadoDeRed();
  cerrarSesion();
});

afterEach(() => {
  vi.unstubAllGlobals();
  cerrarSesion();
});

describe('accesos a datos personales', () => {
  it('enseña quién vio qué de quién, y de una denuncia no dice de quién', async () => {
    simularApi({
      'GET /api/v1/empresa/accesos': () => [
        {
          en: '2026-10-18T09:30:00Z',
          quienId: 4,
          quien: 'Marta Gil',
          que: 'INFORME_MENSUAL',
          sobreId: 7,
          sobre: 'Ana Pruebas',
          detalle: 'Registro de 2026-09',
        },
        { en: '2026-10-17T09:30:00Z', quienId: 1, quien: 'Luis Admin', que: 'DENUNCIA', detalle: 'Expediente 3' },
      ],
    });
    pintar(<AccesosADatos />, { sesion: sesionDe('ADMIN') });

    expect(await screen.findByText('Ana Pruebas')).toBeTruthy();
    expect(screen.getByText(A.que.INFORME_MENSUAL)).toBeTruthy();
    expect(screen.getByText(A.que.DENUNCIA)).toBeTruthy();
    expect(screen.getByText(A.denuncia)).toBeTruthy();
  });

  it('sin accesos, lo dice', async () => {
    simularApi({ 'GET /api/v1/empresa/accesos': () => [] });
    pintar(<AccesosADatos />, { sesion: sesionDe('ADMIN') });

    expect(await screen.findByText(A.vacioTitulo)).toBeTruthy();
  });
});
