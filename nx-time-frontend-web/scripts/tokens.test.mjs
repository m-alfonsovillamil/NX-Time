/**
 * Los tokens se generan leyendo Kotlin con expresiones regulares, y una
 * expresión regular falla en silencio: no avisa de que ha dejado de
 * encontrar un color, simplemente devuelve uno menos.
 *
 * Por eso hay dos clases de test aquí:
 *
 * 1. **Sobre fixtures**, que fijan la forma de la salida (nombres, hex,
 *    tema claro contra oscuro) sin depender del tema real.
 * 2. **Sobre el `Color.kt` y el `Type.kt` de verdad**, que comprueban que
 *    no se ha quedado ninguna declaración fuera. Ese es el que se pondría
 *    rojo el día que alguien añada un color con otra forma.
 */

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { aHexCss, aNombreCss, leerColores, leerColoresDeJornada, leerTipografia } from './tokens.mjs';

const AQUI = dirname(fileURLToPath(import.meta.url));
const TEMA = resolve(AQUI, '..', '..', 'nx-time-frontend-android', 'src', 'main', 'java', 'com', 'nxtime', 'app', 'ui', 'theme');
const COLOR_KT = readFileSync(join(TEMA, 'Color.kt'), 'utf8');
const TYPE_KT = readFileSync(join(TEMA, 'Type.kt'), 'utf8');

describe('aNombreCss', () => {
  it('quita el prefijo del tema y separa las mayúsculas', () => {
    expect(aNombreCss('LightPrimary')).toBe('primary');
    expect(aNombreCss('DarkOnPrimaryContainer')).toBe('on-primary-container');
    expect(aNombreCss('LightSurfaceContainerHighest')).toBe('surface-container-highest');
  });

  it('no se come el prefijo de un nombre que solo empieza igual', () => {
    expect(aNombreCss('LightingBadge')).toBe('ing-badge');
  });
});

describe('aHexCss', () => {
  it('convierte 0xFFRRGGBB en #rrggbb', () => {
    expect(aHexCss('0xFF0E7C86')).toBe('#0e7c86');
  });

  it('se niega a adivinar si el alfa no es opaco', () => {
    // Un color semitransparente necesitaría rgba(), y colarlo como #rrggbb
    // sería pintarlo opaco sin que nadie se entere.
    expect(() => aHexCss('0x800E7C86')).toThrow(/alfa o formato inesperado/);
  });
});

describe('leerColores', () => {
  const fuente = `
val LightPrimary = Color(0xFF0E7C86)
val LightOnPrimary = Color(0xFFFFFFFF)

/** Un comentario por en medio no puede estorbar. */
val DarkPrimary = Color(0xFF5BD2DE)
val DarkOnPrimary = Color(0xFF00363B)
`;

  it('separa el tema claro del oscuro', () => {
    const { claros, oscuros } = leerColores(fuente);

    expect([...claros]).toEqual([['primary', '#0e7c86'], ['on-primary', '#ffffff']]);
    expect(oscuros.get('primary')).toBe('#5bd2de');
  });

  it('falla si deja de encontrar colores en vez de generar un CSS vacío', () => {
    expect(() => leerColores('val LightPrimary = MaterialTheme.colorScheme.primary'))
      .toThrow(/No se han encontrado colores/);
  });

  it('ignora lo que no sea una declaración de nivel superior', () => {
    // `Color(...)` dentro de una función no es un token del tema.
    const conRuido = `${fuente}\nfun algo() {\n    val LightInterna = Color(0xFF123456)\n}\n`;
    expect(leerColores(conRuido).claros.has('interna')).toBe(false);
  });
});

describe('leerColoresDeJornada', () => {
  it('lee los seis colores de cada tema', () => {
    const { Claro, Oscuro } = leerColoresDeJornada(COLOR_KT);

    expect(Claro.size).toBe(6);
    expect(Oscuro.size).toBe(6);
    expect([...Claro.keys()]).toEqual(
      ['trabajando', 'on-trabajando', 'en-pausa', 'on-en-pausa', 'parado', 'on-parado']);
    expect(Claro.get('trabajando')).not.toBe(Oscuro.get('trabajando'));
  });

  it('falla si el bloque cambia de forma, en vez de leer de menos', () => {
    const mutilado = COLOR_KT.replace('parado = Color(0xFFB3261E),', '');
    expect(() => leerColoresDeJornada(mutilado)).toThrow(/debería tener 6 colores/);
  });
});

describe('leerTipografia', () => {
  it('lee tamaño, interlineado y de qué familia es cada estilo', () => {
    const estilos = leerTipografia(TYPE_KT);
    const display = estilos.get('display-medium');

    expect(display.fontSize).toBeGreaterThan(0);
    expect(display.lineHeight).toBeGreaterThan(display.fontSize);
    expect(display.titular).toBe(true);

    // El cuerpo usa la fuente del sistema a propósito: respeta el tamaño
    // accesible que tenga configurado quien lee.
    expect(estilos.get('body-large').titular).toBe(false);
  });
});

describe('el tema real entero', () => {
  /**
   * El test que justifica el fichero.
   *
   * Cuenta a mano las declaraciones del fuente y las compara con lo leído.
   * Si alguien añade un color con otra forma (`Color(0xFF…, alpha)`, un
   * `val` multilínea, una constante compartida), aquí sale rojo; sin esto,
   * el CSS saldría con un token menos y nadie lo vería hasta que una
   * pantalla apareciera sin fondo.
   */
  it('no deja ninguna declaración de color sin leer', () => {
    const declarados = { Light: 0, Dark: 0 };
    for (const [, tema] of COLOR_KT.matchAll(/^val\s+(Light|Dark)\w+\s*=\s*Color\(/gm)) {
      declarados[tema] += 1;
    }
    const { claros, oscuros } = leerColores(COLOR_KT);

    expect(claros.size).toBe(declarados.Light);
    expect(oscuros.size).toBe(declarados.Dark);
    expect(claros.size).toBeGreaterThan(20);
  });

  it('los dos temas definen exactamente los mismos tokens', () => {
    // Si no, una pantalla en oscuro heredaría el color del claro por la
    // cascada y el fallo sería un contraste malo, no un error.
    const { claros, oscuros } = leerColores(COLOR_KT);
    expect([...oscuros.keys()].sort()).toEqual([...claros.keys()].sort());
  });

  it('el CSS versionado contiene todos los tokens que se leen', () => {
    const css = readFileSync(resolve(AQUI, '..', 'src', 'estilos', 'tokens.css'), 'utf8');
    const { claros } = leerColores(COLOR_KT);

    for (const [nombre, valor] of claros) {
      expect(css, `falta --nx-${nombre} en tokens.css; ejecuta npm run tokens`)
        .toContain(`--nx-${nombre}: ${valor};`);
    }
  });

  it('el aviso de contraste viaja con los colores', () => {
    // Quien vaya a "ajustar un color" tiene que leer cuál es el par que
    // cae primero, y no va a abrir Color.kt para enterarse.
    const css = readFileSync(resolve(AQUI, '..', 'src', 'estilos', 'tokens.css'), 'utf8');
    expect(css).toContain('4.67:1');
  });
});
