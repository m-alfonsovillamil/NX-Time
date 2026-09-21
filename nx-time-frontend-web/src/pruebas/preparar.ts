/**
 * Preparacion comun de los tests.
 *
 * `@testing-library/jest-dom` no se usa: sus matchers son comodos pero anaden
 * una dependencia y un `expect` extendido para poco. Lo unico que hace falta
 * aqui es limpiar el DOM entre tests, que sin ello dejaria dos copias de la
 * misma pantalla montadas y haria que `getByRole` encontrara dos botones.
 */
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});
