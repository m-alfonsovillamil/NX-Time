/**
 * La prueba de carga (docs/PRUEBA-DE-CARGA.md): mucha gente a la vez contra un
 * backend con la configuración de producción, y cuánto tarda cada cosa.
 *
 * Sin dependencias: Node 22 o posterior y nada más. Necesita una base sembrada
 * con `sembrar.sql` (las cuentas son p<k>@carga<n>.test, con la contraseña de
 * la demo) y el backend en marcha.
 *
 *   node scripts/carga/carga.mjs entrada --personas 500 --rampa 60
 *   node scripts/carga/carga.mjs entrada --personas 500 --rampa 60 --con-sesion
 *   node scripts/carga/carga.mjs oficina --personas 40
 *   node scripts/carga/carga.mjs mixto --personas 300 --duracion 60
 *   node scripts/carga/carga.mjs integridad --personas 200 --duracion 30
 *
 * Fichar la entrada solo se puede una vez al día: para repetir «entrada» sin
 * volver a copiar la base, --desde N empieza por la persona N.
 *
 * Los escenarios:
 *
 *   entrada     «Las 9:00»: cada persona entra con su contraseña, mira si tiene
 *               la jornada abierta, ficha la entrada y pide su día. Llegan
 *               repartidas a lo largo de --rampa segundos (0 = todas a la vez).
 *               Con --con-sesion ya tienen la sesión abierta, como en la app:
 *               se entra antes, despacio y sin medir, y al llegar solo se
 *               renueva la sesión.
 *   oficina     Lo mismo, pero todas desde la misma IP, como una oficina
 *               detrás de un router. Sale a qué número de personas empieza a
 *               rechazar el límite de entradas por IP.
 *   mixto       Uso normal durante --duracion segundos: cada persona, con unos
 *               segundos entre una cosa y otra, mira su jornada, su historial,
 *               sus avisos y su resumen; una de cada diez es gestora y mira el
 *               historial del equipo y el panel de la empresa.
 *   integridad  El mixto, y a la vez una cuenta de RRHH pidiendo la
 *               comprobación completa de la traza en bucle.
 *
 * Cada persona va con su IP (cabecera X-Forwarded-For), como si cada una
 * estuviera en su casa: el backend tiene que estar arrancado con un proxy de
 * confianza, que es como va en producción.
 */
import { writeFileSync } from 'node:fs';

const [, , escenario, ...resto] = process.argv;
const opcion = (nombre, porDefecto) => {
  const i = resto.indexOf(`--${nombre}`);
  return i === -1 ? porDefecto : resto[i + 1];
};
const bandera = (nombre) => resto.includes(`--${nombre}`);

const API = opcion('api', 'http://localhost:8080');
const PERSONAS = Number(opcion('personas', '100'));
const RAMPA = Number(opcion('rampa', '0'));
/** Por qué persona se empieza: para encadenar tandas de «entrada» sin repetir a nadie (solo se ficha una vez). */
const DESDE = Number(opcion('desde', '0'));
const DURACION = Number(opcion('duracion', '60'));
const EMPRESAS = Number(opcion('empresas', '100'));
const POR_EMPRESA = Number(opcion('por-empresa', '30'));
const CON_SESION = bandera('con-sesion');
const SALIDA = opcion('salida', null);
const CONTRASENA = 'demo1234';
/** Lo que se espera una respuesta antes de darla por perdida. */
const ESPERA_MAXIMA = 60_000;

if (!['entrada', 'oficina', 'mixto', 'integridad'].includes(escenario)) {
  console.error('Uso: node scripts/carga/carga.mjs <entrada|oficina|mixto|integridad> [--personas N] [--rampa s] [--duracion s] [--con-sesion]');
  process.exit(2);
}

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Quién es la persona número i. Se reparten entre las empresas (la 0 en la
 * primera, la 1 en la segunda…) y, dentro de cada una, a partir de p5: las
 * cuatro primeras son ADMIN, RRHH y dos gestoras.
 */
function persona(i) {
  const empresa = (i % EMPRESAS) + 1;
  const k = 5 + Math.floor(i / EMPRESAS);
  if (k > POR_EMPRESA) throw new Error(`No hay tanta gente sembrada: la persona ${i} sería la p${k} de su empresa.`);
  return { email: `p${k}@carga${empresa}.test`, ip: `198.51.${Math.floor(i / 250)}.${(i % 250) + 1}`, empresa };
}

/* ------------------------------------------------------------------ */
/* Medir                                                               */
/* ------------------------------------------------------------------ */

/** paso → { tiempos: ms[], estados: { '200': n, … } } */
const medidas = new Map();

function apuntar(paso, ms, estado) {
  let m = medidas.get(paso);
  if (m === undefined) medidas.set(paso, (m = { tiempos: [], estados: {} }));
  m.tiempos.push(ms);
  m.estados[estado] = (m.estados[estado] ?? 0) + 1;
}

/**
 * Una petición, medida. No lanza nunca: un fallo de red o una espera agotada
 * son un resultado más («red», «tarde»), que es justo lo que se quiere contar.
 */
async function pedir(paso, metodo, ruta, { token, ip, cuerpo, medir = true } = {}) {
  const inicio = performance.now();
  let estado;
  let datos = null;
  try {
    const respuesta = await fetch(API + ruta, {
      method: metodo,
      headers: {
        ...(cuerpo !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token !== undefined ? { Authorization: `Bearer ${token}` } : {}),
        ...(ip !== undefined ? { 'X-Forwarded-For': ip } : {}),
      },
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
      signal: AbortSignal.timeout(ESPERA_MAXIMA),
    });
    estado = String(respuesta.status);
    const texto = await respuesta.text();
    if (respuesta.ok && texto !== '') datos = JSON.parse(texto);
  } catch (fallo) {
    estado = fallo?.name === 'TimeoutError' ? 'tarde' : 'red';
  }
  if (medir) apuntar(paso, performance.now() - inicio, estado);
  return { estado, datos };
}

function percentil(ordenados, p) {
  if (ordenados.length === 0) return 0;
  return ordenados[Math.min(ordenados.length - 1, Math.ceil((p / 100) * ordenados.length) - 1)];
}

function resumen(segundos) {
  const filas = [];
  for (const [paso, m] of medidas) {
    const t = [...m.tiempos].sort((a, b) => a - b);
    const bien = Object.entries(m.estados).filter(([e]) => e.startsWith('2')).reduce((s, [, n]) => s + n, 0);
    filas.push({
      paso,
      peticiones: t.length,
      bien,
      fallos: Object.fromEntries(Object.entries(m.estados).filter(([e]) => !e.startsWith('2'))),
      p50: Math.round(percentil(t, 50)),
      p95: Math.round(percentil(t, 95)),
      p99: Math.round(percentil(t, 99)),
      max: Math.round(t.at(-1) ?? 0),
    });
  }
  const total = filas.reduce((s, f) => s + f.peticiones, 0);
  return { escenario, personas: PERSONAS, rampa: RAMPA, conSesion: CON_SESION, segundos: Math.round(segundos * 10) / 10, porSegundo: Math.round((total / segundos) * 10) / 10, filas };
}

function pintar(r) {
  console.log(`\n${r.escenario}: ${r.personas} personas en ${r.segundos} s (${r.porSegundo} peticiones por segundo)`);
  console.log('paso'.padEnd(22) + 'pet.'.padStart(7) + 'bien'.padStart(7) + 'p50'.padStart(8) + 'p95'.padStart(8) + 'p99'.padStart(8) + 'máx'.padStart(8) + '  fallos');
  for (const f of r.filas) {
    const fallos = Object.entries(f.fallos).map(([e, n]) => `${e}×${n}`).join(' ');
    console.log(
      f.paso.padEnd(22) + String(f.peticiones).padStart(7) + String(f.bien).padStart(7) +
        `${f.p50}`.padStart(8) + `${f.p95}`.padStart(8) + `${f.p99}`.padStart(8) + `${f.max}`.padStart(8) + `  ${fallos}`,
    );
  }
  console.log('(tiempos en milisegundos)');
}

/* ------------------------------------------------------------------ */
/* Lo que hace una persona                                             */
/* ------------------------------------------------------------------ */

async function entrar(p, { medir = true, ip = p.ip } = {}) {
  const { datos } = await pedir('entrar', 'POST', '/auth/login', { ip, cuerpo: { email: p.email, contrasena: CONTRASENA }, medir });
  return datos;
}

/** Entrar sin medir y sin prisa: de cuatro en cuatro, para no ser lo que se mide. */
async function abrirSesiones(personas) {
  const sesiones = new Array(personas.length);
  let siguiente = 0;
  await Promise.all(
    Array.from({ length: 4 }, async () => {
      while (siguiente < personas.length) {
        const i = siguiente++;
        sesiones[i] = await entrar(personas[i], { medir: false });
        if (sesiones[i] === null) throw new Error(`No se ha podido entrar con ${personas[i].email}`);
      }
    }),
  );
  return sesiones;
}

/** La secuencia de «las 9:00» de una persona. */
async function ficharLaEntrada(p, sesion, { ip = p.ip } = {}) {
  let token;
  if (sesion === undefined) {
    const nueva = await entrar(p, { ip });
    if (nueva === null) return;
    token = nueva.token;
  } else {
    const { datos } = await pedir('renovar', 'POST', '/auth/refresh', { ip, cuerpo: { refreshToken: sesion.refreshToken } });
    if (datos === null) return;
    token = datos.token;
  }
  await pedir('jornada abierta', 'GET', '/api/v1/fichaje/activo', { token, ip });
  await pedir('FICHAR', 'POST', '/api/v1/fichaje', { token, ip, cuerpo: { tipo: 'INICIO' } });
  await pedir('mi día', 'GET', '/api/v1/fichaje/hoy', { token, ip });
}

const LECTURAS_DE_CUALQUIERA = [
  ['jornada abierta', '/api/v1/fichaje/activo'],
  ['mi día', '/api/v1/fichaje/hoy'],
  ['historial', '/api/v1/fichaje/historial?pagina=0&tamano=50'],
  ['avisos sin leer', '/api/v1/avisos/no-leidos'],
  ['resumen', '/api/v1/dashboard/resumen'],
];
const LECTURAS_DE_GESTORA = [
  ['equipo: historial', '/api/v1/fichaje/gestor/historial?pagina=0&tamano=50'],
  ['empresa: panel', '/api/v1/dashboard/empresa'],
  ['equipo: pendientes', '/api/v1/dashboard/pendientes'],
];

/** Uso normal: una lectura, unos segundos, otra. Hasta que se acabe el tiempo. */
async function usar(p, token, lecturas, hasta) {
  await espera(Math.random() * 3000);
  while (performance.now() < hasta) {
    const [paso, ruta] = lecturas[Math.floor(Math.random() * lecturas.length)];
    await pedir(paso, 'GET', ruta, { token, ip: p.ip });
    await espera(1000 + Math.random() * 3000);
  }
}

/* ------------------------------------------------------------------ */
/* Los escenarios                                                      */
/* ------------------------------------------------------------------ */

const personas = Array.from({ length: PERSONAS }, (_, i) => persona(DESDE + i));
let inicio;

if (escenario === 'entrada') {
  const sesiones = CON_SESION ? await abrirSesiones(personas) : [];
  if (CON_SESION) console.log(`${PERSONAS} sesiones abiertas. Empieza la medición.`);
  inicio = performance.now();
  await Promise.all(
    personas.map(async (p, i) => {
      await espera(RAMPA > 0 ? Math.random() * RAMPA * 1000 : 0);
      await ficharLaEntrada(p, sesiones[i]);
    }),
  );
} else if (escenario === 'oficina') {
  // Todas desde la misma IP y de una en una: lo que se mira es cuántas entran.
  inicio = performance.now();
  for (const p of personas) await ficharLaEntrada(p, undefined, { ip: '198.51.100.77' });
} else {
  // Una de cada diez, gestora: p3 de su empresa.
  const gestoras = Array.from({ length: Math.max(1, Math.floor(PERSONAS / 10)) }, (_, i) => ({
    email: `p3@carga${(i % EMPRESAS) + 1}.test`,
    ip: `198.52.0.${(i % 250) + 1}`,
  }));
  const rrhh = { email: 'p2@carga1.test', ip: '198.53.0.1' };
  const todas = [...personas, ...gestoras.slice(0, EMPRESAS), ...(escenario === 'integridad' ? [rrhh] : [])];
  const sesiones = await abrirSesiones(todas);
  console.log(`${todas.length} sesiones abiertas. Empieza la medición.`);
  inicio = performance.now();
  const hasta = inicio + DURACION * 1000;
  const tareas = todas.map((p, i) => {
    if (escenario === 'integridad' && p === rrhh) {
      return (async () => {
        while (performance.now() < hasta) {
          await pedir('COMPROBAR LA TRAZA', 'GET', '/api/v1/auditoria/integridad', { token: sesiones[i].token, ip: p.ip });
        }
      })();
    }
    return usar(p, sesiones[i].token, i < personas.length ? LECTURAS_DE_CUALQUIERA : LECTURAS_DE_GESTORA, hasta);
  });
  await Promise.all(tareas);
}

const r = resumen((performance.now() - inicio) / 1000);
pintar(r);
if (SALIDA !== null) writeFileSync(SALIDA, JSON.stringify(r, null, 2));
