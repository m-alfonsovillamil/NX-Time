/**
 * Los datos del titular salen de application.yml (scripts/titular.mjs). Estos
 * tests fijan la forma de la lectura y, sobre todo, que las copias que no se
 * pueden generar (SECURITY.md) y el security.txt publicado no se queden atrás.
 */

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import {
  CONFIGURACION,
  MARCADOS,
  caducidad,
  generarSecurityTxt,
  generarTs,
  leerBloque,
  leerTitular,
  marcarIndex,
  marcarManifest,
  marcarSw,
} from './titular.mjs';

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

  it('index.html, el manifest y sw.js llevan el nombre de application.yml', () => {
    for (const { ruta, marcar } of MARCADOS) {
      const publicado = readFileSync(ruta, 'utf8');
      expect(publicado, ruta).toBe(marcar(publicado, datos));
    }
  });

  it('el nombre se pone solo en su sitio, y escapado donde hace falta', () => {
    const otro = { titular: { producto: 'Horas & <Co>' } };
    expect(marcarIndex('<head><title>NX Time</title></head>', otro)).toBe('<head><title>Horas &amp; &lt;Co&gt;</title></head>');
    expect(marcarManifest('{ "name": "NX Time", "short_name": "NX Time", "lang": "es" }', otro)).toBe(
      '{ "name": "Horas & <Co>", "short_name": "Horas & <Co>", "lang": "es" }',
    );
    expect(marcarSw("showNotification(datos.titulo || 'NX Time', {", otro)).toBe(
      'showNotification(datos.titulo || "Horas & <Co>", {',
    );
    // Si el sitio no está (o está dos veces), falla en vez de dejarlo a medias.
    expect(() => marcarIndex('<head></head>', otro)).toThrow(/se esperaba una vez/);
  });

  it('SECURITY.md da el mismo correo de seguridad', () => {
    expect(readFileSync(join(RAIZ, 'SECURITY.md'), 'utf8')).toContain(datos.titular['correo-seguridad']);
  });
});
