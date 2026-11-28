/**
 * Copia los datos del titular del servicio desde la configuración del backend.
 *
 * Fuente: `nx-time-backend/src/main/resources/application.yml`, bloques
 * `application.titular` y `application.legal` (plan J, ADR 046).
 * Salida, toda versionada:
 *   - `src/legal/titular.ts`: lo que enseñan las páginas legales, y el nombre
 *     y el dominio que usan los textos (`i18n/es`).
 *   - `public/.well-known/security.txt`: a quién avisar de un fallo (RFC 9116).
 *   - el nombre en `index.html` (título), `public/manifest.webmanifest` y
 *     `public/sw.js`, que se escriben a mano: solo se cambia ese sitio.
 *
 * ## Por qué se copia y no se pide al servidor
 *
 * El aviso legal tiene que estar siempre a mano (LSSI, art. 10), también con el
 * backend dormido o caído; una página que espera a la API no lo está. Y por qué
 * se genera y no se escribe a mano: el nombre del producto, el titular y los
 * correos van a cambiar (el nombre «NX Time» está cogido), y con dos copias
 * escritas a mano una se quedaría atrás. El CI regenera esto y falla si no
 * coincide con lo versionado (`npm run verificar:generado`).
 *
 * Lee el YAML a mano, como `tokens.mjs` lee Kotlin: solo hacen falta pares
 * `clave: "valor"` de dos bloques. Y **falla** si falta alguno, en vez de
 * generar una página con un hueco.
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = resolve(AQUI, '..', '..');
export const CONFIGURACION = join(RAIZ, 'nx-time-backend', 'src', 'main', 'resources', 'application.yml');
const SALIDA_TS = resolve(AQUI, '..', 'src', 'legal', 'titular.ts');
const SALIDA_SECURITY = resolve(AQUI, '..', 'public', '.well-known', 'security.txt');

const CLAVES_TITULAR = ['producto', 'nombre', 'nif', 'domicilio', 'correo-privacidad', 'correo-seguridad', 'web'];

/**
 * Los pares de un bloque que cuelga de `application:` (dos espacios), con sus
 * claves a cuatro. Solo admite valores entre comillas dobles: es lo que hay, y
 * así una variable de entorno `${...}` metida aquí por error no pasa.
 */
export function leerBloque(yaml, bloque) {
  const lineas = yaml.split(/\r?\n/);
  const inicio = lineas.findIndex((linea) => linea === `  ${bloque}:`);
  if (inicio < 0) throw new Error(`No está el bloque application.${bloque} en application.yml.`);
  const pares = {};
  for (const linea of lineas.slice(inicio + 1)) {
    if (linea.trim() === '' || linea.trimStart().startsWith('#')) continue;
    if (!linea.startsWith('    ')) break;
    const par = linea.match(/^ {4}([a-z-]+): "([^"$]*)"\s*$/);
    if (!par) throw new Error(`Línea que no se entiende en application.${bloque}: «${linea.trim()}».`);
    pares[par[1]] = par[2];
  }
  return pares;
}

export function leerTitular(yaml) {
  const titular = leerBloque(yaml, 'titular');
  for (const clave of CLAVES_TITULAR) {
    if (!titular[clave]) throw new Error(`Falta application.titular.${clave} en application.yml.`);
  }
  const { version } = leerBloque(yaml, 'legal');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(version ?? '')) {
    throw new Error('application.legal.version tiene que ser una fecha AAAA-MM-DD.');
  }
  return { titular, version };
}

/** Un año después de la versión: lo que pide la RFC 9116 como mucho. Fijo, para que regenerar no cambie nada. */
export function caducidad(version) {
  const fecha = new Date(`${version}T00:00:00Z`);
  fecha.setUTCFullYear(fecha.getUTCFullYear() + 1);
  return fecha.toISOString().replace('.000', '');
}

export function generarTs({ titular, version }) {
  const campo = (clave) => JSON.stringify(titular[clave]);
  return `/**
 * GENERADO por \`npm run titular\` desde application.yml del backend: no se edita
 * a mano (ver scripts/titular.mjs). Quién presta el servicio, para las páginas
 * legales (plan J, ADR 046).
 */

export const TITULAR = {
  producto: ${campo('producto')},
  /** El dominio de la web, sin protocolo: el que se le dice a la gente que teclee. */
  dominio: ${JSON.stringify(new URL(titular.web).host)},
  nombre: ${campo('nombre')},
  nif: ${campo('nif')},
  domicilio: ${campo('domicilio')},
  correoPrivacidad: ${campo('correo-privacidad')},
  correoSeguridad: ${campo('correo-seguridad')},
  web: ${campo('web')},
} as const;

/** La versión de las condiciones del servicio y del contrato de encargo que se acepta al registrarse. */
export const VERSION_CONDICIONES = ${JSON.stringify(version)};
`;
}

export function generarSecurityTxt({ titular, version }) {
  const web = titular.web.replace(/\/$/, '');
  return [
    `# Cómo avisar de un fallo de seguridad en ${titular.producto}. Generado desde application.yml.`,
    `Contact: mailto:${titular['correo-seguridad']}`,
    `Expires: ${caducidad(version)}`,
    'Preferred-Languages: es, en',
    `Canonical: ${web}/.well-known/security.txt`,
    `Policy: ${web}/legal/seguridad`,
    '',
  ].join('\n');
}

/**
 * Cambia lo que casa con `patron` (una sola vez en el fichero, o falla) por
 * `nuevo`. Para los ficheros escritos a mano que llevan el nombre en un sitio
 * fijo: el título de la página, el manifest y el service worker.
 */
function sustituirUnaVez(texto, patron, nuevo, fichero) {
  const veces = texto.match(new RegExp(patron.source, 'g'))?.length ?? 0;
  if (veces !== 1) throw new Error(`${fichero}: se esperaba una vez «${patron.source}» y sale ${veces}.`);
  return texto.replace(patron, nuevo);
}

const escaparHtml = (texto) => texto.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** `index.html`: el título de la pestaña. */
export function marcarIndex(html, { titular }) {
  return sustituirUnaVez(html, /<title>[^<]*<\/title>/, `<title>${escaparHtml(titular.producto)}</title>`, 'index.html');
}

/** `manifest.webmanifest`: el nombre con el que se instala la web. */
export function marcarManifest(json, { titular }) {
  const nombre = JSON.stringify(titular.producto);
  let salida = sustituirUnaVez(json, /"name": "[^"]*"/, `"name": ${nombre}`, 'manifest.webmanifest');
  salida = sustituirUnaVez(salida, /"short_name": "[^"]*"/, `"short_name": ${nombre}`, 'manifest.webmanifest');
  return salida;
}

/** `sw.js`: el título de un push que llegue sin él (el backend siempre lo manda). */
export function marcarSw(js, { titular }) {
  return sustituirUnaVez(js, /datos\.titulo \|\| (?:'[^']*'|"[^"]*")/, `datos.titulo || ${JSON.stringify(titular.producto)}`, 'sw.js');
}

/** Los ficheros públicos que llevan el nombre, con la función que lo pone. */
export const MARCADOS = [
  { ruta: resolve(AQUI, '..', 'index.html'), marcar: marcarIndex },
  { ruta: resolve(AQUI, '..', 'public', 'manifest.webmanifest'), marcar: marcarManifest },
  { ruta: resolve(AQUI, '..', 'public', 'sw.js'), marcar: marcarSw },
];

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const datos = leerTitular(readFileSync(CONFIGURACION, 'utf8'));
  mkdirSync(dirname(SALIDA_TS), { recursive: true });
  mkdirSync(dirname(SALIDA_SECURITY), { recursive: true });
  writeFileSync(SALIDA_TS, generarTs(datos));
  writeFileSync(SALIDA_SECURITY, generarSecurityTxt(datos));
  for (const { ruta, marcar } of MARCADOS) {
    writeFileSync(ruta, marcar(readFileSync(ruta, 'utf8'), datos));
  }
  console.log(`titular.ts, security.txt, index.html, manifest y sw.js al día (condiciones ${datos.version}).`);
}
