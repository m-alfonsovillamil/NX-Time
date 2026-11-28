/**
 * El marco de las pantallas de acceso (plan P13): el «Qué es…» también en el
 * móvil, plegado, y el enlace a la app donde tiene sentido.
 */

import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { T } from '../../i18n/es';
import { URL_APP } from '../../legal/titular';
import { MarcoDeAcceso } from './MarcoDeAcceso';

function pintarMarco() {
  return render(
    <MemoryRouter>
      <MarcoDeAcceso>
        <h1>Entrar</h1>
      </MarcoDeAcceso>
    </MemoryRouter>,
  );
}

describe('el marco de acceso', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('lleva el «Qué es…» plegado bajo el formulario, con el enlace a la app', () => {
    const { container } = pintarMarco();

    const plegado = container.querySelector('details.nx-acceso-marca-movil') as HTMLElement;
    expect(plegado).not.toBeNull();
    expect(within(plegado).getByText(T.marca.etiqueta)).toBeTruthy();
    // El formulario sigue siendo lo primero.
    expect(screen.getByRole('heading', { level: 1 }).compareDocumentPosition(plegado)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
    if (URL_APP) {
      expect(within(plegado).getByRole('link', { name: new RegExp(T.marca.app) }).getAttribute('href')).toBe(URL_APP);
    }
  });

  it('en un iPhone no ofrece la app, que es solo para Android', () => {
    vi.stubGlobal('navigator', { ...navigator, userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)' });
    pintarMarco();

    expect(screen.queryAllByRole('link', { name: new RegExp(T.marca.app) })).toHaveLength(0);
  });
});
