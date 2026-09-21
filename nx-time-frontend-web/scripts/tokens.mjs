/**
 * Convierte el tema de la app Android en variables CSS.
 *
 * Fuente: `nx-time-frontend-android/.../ui/theme/Color.kt` y `Type.kt`.
 * Salida:  `src/estilos/tokens.css`, que se versiona.
 *
 * ## Por qué se genera y no se copia
 *
 * El color de un botón no puede tener dos verdades. Copiar la paleta a mano
 * funciona el primer día y se desincroniza el segundo, y el resultado no es un
 * error que salte: es una web que se parece a la app sin llegar a ser la misma.
 *
 * Generarlo tiene además un efecto que copiar no tiene: el CI regenera este
 * fichero y falla si el resultado no coincide con lo versionado, así que
 * cambiar el teal en Android **obliga** a que la web se entere.
 *
 * ## Por qué a mano y sin dependencias
 *
 * Kotlin no expone su tema a nadie, así que hay que leer el fuente. Un parser
 * de Kotlin sería desproporcionado para lo que hace falta: declaraciones
 * `val Nombre = Color(0xFFRRGGBB)` y unos cuantos números con `.sp`. Lo que sí
 * importa es que el script **falle** si deja de encontrar lo que espera, en vez
 * de generar un CSS a medias que nadie miraría.
 */

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = resolve(AQUI, '..', '..');
const TEMA = join(RAIZ, 'nx-time-frontend-android', 'src', 'main', 'java', 'com', 'nxtime', 'app', 'ui', 'theme');
const SALIDA = resolve(AQUI, '..', 'src', 'estilos', 'tokens.css');

/** `LightPrimaryContainer` -> `primary-container`. Quita el prefijo del tema. */
function aNombreCss(nombreKotlin) {
  return nombreKotlin
    .replace(/^(Light|Dark)/, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .toLowerCase();
}

/**
 * `0xFF0E7C86` -> `#0e7c86`. El alfa siempre es FF en esta paleta.
 *
 * Se comprueba que lo sea, y no solo que el literal mida lo que mide: un
 * `0x800E7C86` tiene exactamente la misma longitud y saldría como
 * `#0e7c86`, es decir, **pintado opaco** sin que nadie se entere. Un color
 * semitransparente necesita `rgba()`, así que aquí se para.
 */
function aHexCss(literal) {
  const partes = literal.toLowerCase().match(/^0x(ff)([0-9a-f]{6})$/);
  if (!partes) {
    throw new Error(
      `Color con alfa o formato inesperado: ${literal}. Este script asume 0xFFRRGGBB; `
      + 'un color semitransparente hay que escribirlo a mano como rgba().');
  }
  return `#${partes[2]}`;
}

function leerColores(fuente) {
  const claros = new Map();
  const oscuros = new Map();
  const patron = /^val\s+(Light|Dark)(\w+)\s*=\s*Color\((0x[0-9A-Fa-f]{8})\)/gm;

  for (const [, tema, nombre, literal] of fuente.matchAll(patron)) {
    (tema === 'Light' ? claros : oscuros).set(aNombreCss(nombre), aHexCss(literal));
  }
  if (claros.size === 0 || oscuros.size === 0) {
    throw new Error('No se han encontrado colores en Color.kt. ¿Ha cambiado la forma de declararlos?');
  }
  return { claros, oscuros };
}

/**
 * Los colores de jornada, que no son parte del esquema de Material.
 *
 * Son los que hacen que el botón de fichar se lea de lejos sin leer nada, y
 * `Color.kt` dice literalmente que no se tocan. Por eso salen con su propio
 * prefijo en vez de mezclarse con el resto.
 */
function leerColoresDeJornada(fuente) {
  const porTema = {};
  for (const tema of ['Claro', 'Oscuro']) {
    // Hasta el paréntesis de cierre que está solo en su línea. Contar
    // paréntesis anidados no vale: cada color es otro `Color(...)`.
    const bloque = fuente.match(
      new RegExp(`val ColoresJornada${tema} = ColoresJornada\\(([\\s\\S]*?)\\n\\)`));
    if (!bloque) {
      throw new Error(`No se ha encontrado ColoresJornada${tema} en Color.kt.`);
    }
    const colores = new Map();
    for (const [, campo, literal] of bloque[1].matchAll(/(\w+)\s*=\s*Color\((0x[0-9A-Fa-f]{8})\)/g)) {
      colores.set(aNombreCss(campo), aHexCss(literal));
    }
    if (colores.size !== 6) {
      throw new Error(`ColoresJornada${tema} debería tener 6 colores y se han leído ${colores.size}.`);
    }
    porTema[tema] = colores;
  }
  return porTema;
}

/** La escala tipográfica: nombre del estilo -> tamaño, interlineado y espaciado. */
function leerTipografia(fuente) {
  const estilos = new Map();
  const patron = /(\w+)\s*=\s*TextStyle\(([\s\S]*?)\n\s*\),?/g;

  for (const [, nombre, cuerpo] of fuente.matchAll(patron)) {
    const numero = (clave) => {
      const hallado = cuerpo.match(new RegExp(`${clave}\\s*=\\s*\\(?(-?[\\d.]+)\\)?\\.sp`));
      return hallado ? Number(hallado[1]) : null;
    };
    estilos.set(aNombreCss(nombre), {
      fontSize: numero('fontSize'),
      lineHeight: numero('lineHeight'),
      letterSpacing: numero('letterSpacing'),
      // El cuerpo usa la fuente del sistema a propósito, para respetar el
      // tamaño accesible que tenga configurado quien lee (ver Type.kt).
      titular: !cuerpo.includes('FontFamily.Default')
    });
  }
  if (estilos.size === 0) {
    throw new Error('No se han encontrado estilos en Type.kt. ¿Ha cambiado la forma de declararlos?');
  }
  return estilos;
}

/** La advertencia de contraste de Color.kt, para que viaje con los colores. */
function leerAvisoDeContraste(fuente) {
  const parrafo = fuente.match(/\*\s*El par más justo[\s\S]*?primero\./);
  if (!parrafo) {
    throw new Error('No se ha encontrado la advertencia de contraste en Color.kt.');
  }
  return parrafo[0].replace(/^\s*\*\s?/gm, '').trim().replace(/\s+/g, ' ');
}

function generar() {
  const colorKt = readFileSync(join(TEMA, 'Color.kt'), 'utf8');
  const typeKt = readFileSync(join(TEMA, 'Type.kt'), 'utf8');

  const { claros, oscuros } = leerColores(colorKt);
  const jornada = leerColoresDeJornada(colorKt);
  const tipografia = leerTipografia(typeKt);
  const aviso = leerAvisoDeContraste(colorKt);

  const linea = ([nombre, valor]) => `  --nx-${nombre}: ${valor};`;
  const lineasJornada = (tema) =>
    [...jornada[tema]].map(([nombre, valor]) => `  --nx-jornada-${nombre}: ${valor};`).join('\n');

  const tipos = [...tipografia]
    .filter(([, e]) => e.fontSize !== null)
    .flatMap(([nombre, e]) => {
      const salida = [`  --nx-fs-${nombre}: ${e.fontSize}px;`];
      if (e.lineHeight !== null) salida.push(`  --nx-lh-${nombre}: ${e.lineHeight}px;`);
      if (e.letterSpacing !== null) salida.push(`  --nx-ls-${nombre}: ${e.letterSpacing}px;`);
      salida.push(`  --nx-ff-${nombre}: var(${e.titular ? '--nx-font-titular' : '--nx-font-cuerpo'});`);
      return salida;
    })
    .join('\n');

  const css = `/* GENERADO POR scripts/tokens.mjs. No editar a mano.
 *
 * La fuente es el tema de la app Android (ui/theme/Color.kt y Type.kt): el
 * color de un botón no puede tener dos verdades. Para cambiar algo, cámbialo
 * allí y ejecuta \`npm run tokens\`.
 *
 * AVISO DE CONTRASTE, copiado de Color.kt:
 * ${aviso}
 */

:root {
  /* Sora solo en titulares y cifras; el cuerpo usa la fuente del sistema para
     respetar el tamaño accesible de quien lee (ver Type.kt). */
  --nx-font-titular: 'Sora', system-ui, sans-serif;
  --nx-font-cuerpo: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;

${tipos}

${[...claros].map(linea).join('\n')}

${lineasJornada('Claro')}
}

/* El degradado del fondo es una decisión del tema, no decoración: las
   superficies flotan sobre él y los Scaffold van transparentes. */
.nx-fondo {
  background: linear-gradient(to bottom, var(--nx-fondo-arriba), var(--nx-fondo-abajo));
  min-height: 100%;
}

/* Oscuro por preferencia del sistema, salvo que la página pida claro a mano. */
@media (prefers-color-scheme: dark) {
  :root:not([data-tema='claro']) {
${[...oscuros].map(linea).join('\n')}

${lineasJornada('Oscuro')}
  }
}

/* Oscuro elegido a mano, que manda sobre la preferencia del sistema. */
:root[data-tema='oscuro'] {
${[...oscuros].map(linea).join('\n')}

${lineasJornada('Oscuro')}
}
`;

  mkdirSync(dirname(SALIDA), { recursive: true });
  writeFileSync(SALIDA, css, 'utf8');
  return { colores: claros.size, estilos: tipografia.size, salida: SALIDA };
}

// Solo al ejecutarlo directamente: el test lo importa para probarlo.
if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  const { colores, estilos, salida } = generar();
  console.log(`tokens.css generado: ${colores} colores y ${estilos} estilos -> ${salida}`);
}

export { generar, aNombreCss, aHexCss, leerColores, leerColoresDeJornada, leerTipografia };
