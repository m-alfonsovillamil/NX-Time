/**
 * Las páginas legales (plan J): que estén, que digan quién presta el servicio
 * con los datos de la configuración, y que no esperen a la API.
 */

import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';

import { TITULAR } from '../../legal/titular';
import { pintar, simularApi } from '../../pruebas/api';
import { DOCUMENTOS } from './documentos';
import { Legal } from './Legal';

function pintarEn(ruta: string) {
  const api = simularApi({});
  pintar(
    <Routes>
      <Route path="/legal" element={<Legal />} />
      <Route path="/legal/:ruta" element={<Legal />} />
    </Routes>,
    { ruta },
  );
  return api;
}

describe('los textos legales', () => {
  it('el índice enlaza todos los textos', () => {
    pintarEn('/legal');
    expect(screen.getByRole('heading', { level: 1, name: 'Información legal' })).toBeTruthy();
    for (const doc of DOCUMENTOS) {
      expect(screen.getAllByRole('link', { name: doc.titulo }).length).toBeGreaterThan(0);
    }
  });

  it('el aviso legal dice quién lo presta, con su NIF, su domicilio y su correo, sin pedir nada al servidor', () => {
    const api = pintarEn('/legal/aviso-legal');
    expect(screen.getByRole('heading', { level: 1, name: 'Aviso legal' })).toBeTruthy();
    expect(screen.getByText(TITULAR.nombre)).toBeTruthy();
    expect(screen.getByText(TITULAR.nif)).toBeTruthy();
    expect(screen.getByText(TITULAR.domicilio)).toBeTruthy();
    expect(screen.getByRole('link', { name: TITULAR.correoPrivacidad }).getAttribute('href')).toBe(
      `mailto:${TITULAR.correoPrivacidad}`,
    );
    expect(api.llamadas).toHaveLength(0);
  });

  it.each(DOCUMENTOS.map((doc) => [doc.ruta, doc.titulo] as const))('%s se abre con su título', (ruta, titulo) => {
    pintarEn(`/legal/${ruta}`);
    expect(screen.getByRole('heading', { level: 1, name: titulo })).toBeTruthy();
  });

  it('una ruta que no existe lo dice y deja volver al índice', () => {
    pintarEn('/legal/inventada');
    expect(screen.getByRole('heading', { level: 1, name: 'No existe ese texto' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ver todos los textos legales' })).toBeTruthy();
  });
});
