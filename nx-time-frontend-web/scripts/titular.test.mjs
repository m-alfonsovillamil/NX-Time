/**
 * Los datos del titular salen de application.yml (scripts/titular.mjs). Estos
 * tests fijan la forma de la lectura y, sobre todo, que las copias que no se
 * pueden generar (SECURITY.md) y el security.txt publicado no se queden atrás.
 */

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { CONFIGURACION, caducidad, generarSecurityTxt, generarTs, leerBloque, leerTitular } from './titular.mjs';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = resolve(AQUI, '..', '..');
const datos = leerTitular(readFileSync(CONFIGURACION, 'utf8'));

describe('la lectura de application.yml', () => {
  it('lee los pares de un bloque y se para al salir de él', () => {
    const yaml = ['application:', '  titular:', '    # comentario', '    nombre: "Ana"', '', '  otro:', '    nombre: "Luis"'].join('\n');
    expect(leerBloque(yaml, 'titular')).toEqual({ nombre: 'Ana' });
  });

  it('no admite una variable de entorno donde va un dato que se publica', () => {
    expect(() => leerBloque('application:\n  titular:\n    nif: "${NIF:x}"\n', 'titular')).toThrow(/no se entiende/);
  });

  it('falla si falta un dato del titular, en vez de publicar una página con un hueco', () => {
    expect(() => leerTitular('application:\n  titular:\n    producto: "X"\n  legal:\n    version: "2026-01-01"\n')).toThrow(
      /Falta application.titular/,
    );
  });
});

describe('las copias', () => {
  it('src/legal/titular.ts está al día con application.yml', () => {
    expect(readFileSync(resolve(AQUI, '..', 'src', 'legal', 'titular.ts'), 'utf8')).toBe(generarTs(datos));
  });

  it('el security.txt publicado está al día y no ha caducado', () => {
    const publicado = readFileSync(resolve(AQUI, '..', 'public', '.well-known', 'security.txt'), 'utf8');
    expect(publicado).toBe(generarSecurityTxt(datos));
    // La RFC 9116 pide que caduque; uno caducado dice que nadie lo mantiene.
    // Si esto falla, toca revisar las condiciones y subir application.legal.version.
    expect(new Date(caducidad(datos.version)).getTime()).toBeGreaterThan(Date.now());
  });

  it('SECURITY.md da el mismo correo de seguridad', () => {
    expect(readFileSync(join(RAIZ, 'SECURITY.md'), 'utf8')).toContain(datos.titular['correo-seguridad']);
  });
});
