/**
 * Cuántas consultas SQL cuesta cada listado de la API, con los datos de demo.
 *
 * Para encontrar el listado que empeora con el tamaño de la empresa (una
 * consulta por fila, o por persona) sin leer el código entero: se llama a cada
 * GET sin parámetros de ruta, con tres roles, y se cuentan las líneas
 * «Hibernate:» que aparecen en el log del backend entre medias.
 *
 * Hace falta el backend en marcha con el SQL a la vista y escribiendo a un
 * fichero:
 *
 *   java -jar nx-time-backend/build/libs/nx-time-backend-0.0.1-SNAPSHOT.jar \
 *     --spring.profiles.active=dev,demo --spring.jpa.show-sql=true \
 *     --spring.jpa.properties.hibernate.format_sql=false > backend.log 2>&1 &
 *
 *   node scripts/medir-consultas.mjs docs/openapi.json backend.log
 *
 * **Cómo leer el resultado.** La demo tiene pocas personas, así que un número
 * bajo no absuelve a nadie: hay que mirar las filas. Cuatro filas y diez
 * consultas es peor señal que cincuenta filas y nueve. Toda petición cuesta ya
 * cuatro consultas antes de hacer nada (la persona que la hace y su empresa,
 * una vez en el filtro y otra en el servicio: ADR 034). Lo que confirma un
 * listado sospechoso es un test que doble las filas y vea si crecen las
 * consultas, como ConsultasDeAusenciasIT.
 *
 * La primera llamada a cada ruta no se cuenta: llena cachés.
 */
import { readFileSync, statSync, openSync, readSync, closeSync } from 'node:fs';

const [, , rutaOpenApi, rutaLog] = process.argv;
const API = 'http://localhost:8080';
const openapi = JSON.parse(readFileSync(rutaOpenApi, 'utf8'));

const hoy = new Date();
const dia = (d) => d.toISOString().slice(0, 10);
const lunes = new Date(hoy);
lunes.setUTCDate(hoy.getUTCDate() - ((hoy.getUTCDay() + 6) % 7));
const domingo = new Date(lunes);
domingo.setUTCDate(lunes.getUTCDate() + 6);
const primero = new Date(Date.UTC(hoy.getUTCFullYear(), hoy.getUTCMonth(), 1));
const ultimo = new Date(Date.UTC(hoy.getUTCFullYear(), hoy.getUTCMonth() + 1, 0));

// Valores para los parámetros de consulta obligatorios más comunes.
const PARAMETROS = {
  desde: dia(primero),
  hasta: dia(ultimo),
  anio: String(hoy.getUTCFullYear()),
  mes: String(hoy.getUTCMonth() + 1),
  year: String(hoy.getUTCFullYear()),
  month: String(hoy.getUTCMonth() + 1),
  fecha: dia(hoy),
  pagina: '0',
  tamano: '50',
};

const rutas = Object.entries(openapi.paths)
  .filter(([ruta, def]) => def.get !== undefined && !ruta.includes('{') && ruta.startsWith('/api/v1/'))
  .filter(([ruta]) => !/pdf|excel|exportar|mis-datos|foto|adjunt|informes\//.test(ruta))
  .map(([ruta, def]) => {
    const consulta = (def.get.parameters ?? [])
      .filter((p) => p.in === 'query' && p.required === true)
      .map((p) => `${p.name}=${encodeURIComponent(PARAMETROS[p.name] ?? '1')}`)
      .join('&');
    return consulta === '' ? ruta : `${ruta}?${consulta}`;
  });

function consultasDesde(posicion) {
  const tamano = statSync(rutaLog).size;
  if (tamano <= posicion) return { n: 0, fin: tamano };
  const fd = openSync(rutaLog, 'r');
  const trozo = Buffer.alloc(tamano - posicion);
  readSync(fd, trozo, 0, trozo.length, posicion);
  closeSync(fd);
  return { n: (trozo.toString('utf8').match(/^Hibernate: /gm) ?? []).length, fin: tamano };
}

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

async function entrar(email) {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': `203.0.113.${Math.floor(Math.random() * 250) + 1}` },
    body: JSON.stringify({ email, contrasena: 'demo1234' }),
  });
  if (!r.ok) throw new Error(`login ${email}: ${r.status}`);
  return (await r.json()).token;
}

function filasDe(cuerpo) {
  if (Array.isArray(cuerpo)) return cuerpo.length;
  if (cuerpo !== null && typeof cuerpo === 'object') {
    for (const clave of ['contenido', 'content', 'filas', 'items']) if (Array.isArray(cuerpo[clave])) return cuerpo[clave].length;
  }
  return null;
}

const resultados = [];
for (const [rol, email] of [
  ['EMPLEADO', 'javier.lopez@techcorp.demo'],
  ['RRHH', 'elena.rios@techcorp.demo'],
  ['ADMIN', 'raul.ortega@techcorp.demo'],
]) {
  const token = await entrar(email);
  for (const ruta of rutas) {
    // Una primera llamada calienta cachés (la del usuario, las de segundo nivel si las hubiera).
    await fetch(API + ruta, { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.arrayBuffer());
    await espera(120);
    const antes = statSync(rutaLog).size;
    const r = await fetch(API + ruta, { headers: { Authorization: `Bearer ${token}` } });
    let cuerpo = null;
    try {
      cuerpo = await r.json();
    } catch {
      /* sin cuerpo */
    }
    await espera(150);
    const { n } = consultasDesde(antes);
    if (r.status === 200) resultados.push({ rol, ruta: ruta.split('?')[0], consultas: n, filas: filasDe(cuerpo) });
  }
}

// Por ruta, el peor caso entre roles.
const peor = new Map();
for (const r of resultados) {
  const previo = peor.get(r.ruta);
  if (previo === undefined || r.consultas > previo.consultas) peor.set(r.ruta, r);
}
const orden = [...peor.values()].sort((a, b) => b.consultas - a.consultas);
console.log('consultas  filas  rol       ruta');
for (const r of orden) {
  console.log(`${String(r.consultas).padStart(9)}  ${String(r.filas ?? '-').padStart(5)}  ${r.rol.padEnd(8)}  ${r.ruta}`);
}
console.log(`\n${orden.length} rutas medidas (200). Total de respuestas 200: ${resultados.length}.`);
