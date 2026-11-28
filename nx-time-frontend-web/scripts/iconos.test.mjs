/**
 * El gráfico de la ficha de Play sale del nombre de `titular.ts` y del lema de
 * la pantalla de acceso (scripts/iconos.mjs): si alguno cambia de forma, que
 * falle aquí y no al generar las imágenes.
 */

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { htmlDelGrafico, leerTexto } from './iconos.mjs';
import { TITULAR } from '../src/legal/titular';
import { acceso } from '../src/i18n/es/acceso';

const WEB = resolve(dirname(fileURLToPath(import.meta.url)), '..');

describe('el gráfico de funciones de Play', () => {
  it('lee el nombre y el lema de sus ficheros, sin importarlos', () => {
    expect(leerTexto(readFileSync(join(WEB, 'src', 'legal', 'titular.ts'), 'utf-8'), 'producto')).toBe(TITULAR.producto);
    expect(leerTexto(readFileSync(join(WEB, 'src', 'i18n', 'es', 'acceso.ts'), 'utf-8'), 'lema')).toBe(acceso.marca.lema);
  });

  it('escapa el nombre y el lema', () => {
    const html = htmlDelGrafico({
      producto: 'Horas & <Co>',
      lema: 'Uno <b>dos</b>',
      icono: '<svg></svg>',
      colores: { fondoArriba: '#ffffff', fondoAbajo: '#eeeeee', primario: '#000000', texto: '#111111' },
    });
    expect(html).toContain('<h1>Horas &amp; &lt;Co&gt;</h1>');
    expect(html).toContain('<p>Uno &lt;b&gt;dos&lt;/b&gt;</p>');
  });
});
