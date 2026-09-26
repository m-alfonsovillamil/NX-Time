/**
 * El catálogo de secciones contra **el backend de verdad**: sus avisos y sus authorities.
 *
 * Los dos primeros tests leen ficheros Java del backend, y a propósito. Son
 * las dos formas de que la web y el servidor se separen sin que nada compile
 * mal:
 *
 * - Alguien añade un `NoticeType` con un destino nuevo, y la web no tiene
 *   sección para él: el aviso llega y no lleva a ninguna parte, para siempre.
 * - Alguien escribe `requiere: 'ausencias:aprobar'` (con una s de más): la
 *   sección no la vería nadie, y el test de la página seguiría en verde
 *   porque usa la misma cadena mal escrita.
 *
 * Por eso `web.yml` vigila también esos dos ficheros.
 */

import { describe, expect, it } from 'vitest';

// `?raw` y no `node:fs`: Vite entrega el fichero como texto, y así los tipos
// de Node no entran en un proyecto que corre en el navegador.
import noticeTypeJava from '../../../nx-time-backend/src/main/java/com/nxtime/nxtime/domain/NoticeType.java?raw';
import roleAuthoritiesJava from '../../../nx-time-backend/src/main/java/com/nxtime/nxtime/domain/RoleAuthorities.java?raw';
import { AUTHORITIES } from '../pruebas/api';
import { SECCIONES, barraInferior, destinoDeAviso, disponibles, menuPara } from './secciones';

/** Los destinos de `NoticeType.java`: `AUSENCIA_SOLICITADA("ausencias-equipo/pendientes"),`. */
function destinosDelBackend(): string[] {
  return [...noticeTypeJava.matchAll(/^\s*[A-Z_]+\("([^"]+)"\)[,;]/gm)].map((m) => m[1] ?? '');
}

/**
 * El reparto de `RoleAuthorities.java`, rol por rol, con su herencia
 * (`GESTOR = union(EMPLEADO, Set.of(...))`).
 */
function authoritiesDelBackend(): Record<string, Set<string>> {
  const fuente = roleAuthoritiesJava;
  const roles: Record<string, Set<string>> = {};
  const declaracion = /Set<String> (\w+) = (?:union\((\w+), )?Set\.of\(([^)]*)\)/g;
  for (const [, rol, base, lista] of fuente.matchAll(declaracion)) {
    if (rol === undefined || lista === undefined) continue;
    const propias = [...lista.matchAll(/"([^"]+)"/g)].map((m) => m[1] ?? '');
    roles[rol] = new Set([...(base !== undefined ? (roles[base] ?? []) : []), ...propias]);
  }
  return roles;
}

describe('el catálogo y el backend', () => {
  it('cada destino de aviso del backend tiene su sección', () => {
    const destinos = destinosDelBackend();
    // Si la expresión dejara de casar, el test pasaría sin comprobar nada.
    expect(destinos.length).toBeGreaterThan(15);

    const rutas = new Set(SECCIONES.map((s) => s.ruta));
    expect(destinos.filter((d) => !rutas.has(d))).toEqual([]);
  });

  it('cada authority que pide una sección existe en RoleAuthorities.java', () => {
    const todas = new Set(Object.values(authoritiesDelBackend()).flatMap((s) => [...s]));
    expect(todas.size).toBeGreaterThan(30);

    const requeridas = SECCIONES.flatMap((s) => (s.requiere !== undefined ? [s.requiere] : []));
    expect(requeridas.filter((a) => !todas.has(a))).toEqual([]);
  });

  it('las sesiones de prueba reparten las authorities como el backend', () => {
    const backend = authoritiesDelBackend();
    for (const rol of ['EMPLEADO', 'GESTOR', 'RRHH', 'ADMIN'] as const) {
      expect([...AUTHORITIES[rol]].sort(), rol).toEqual([...(backend[rol] ?? [])].sort());
    }
  });

  it('no hay dos secciones con la misma ruta', () => {
    const rutas = SECCIONES.map((s) => s.ruta);
    expect(new Set(rutas).size).toBe(rutas.length);
  });

  it('cada sección sin página dice en qué fase llega', () => {
    expect(SECCIONES.filter((s) => s.pagina === undefined && s.llegaEn === undefined)).toEqual([]);
  });
});

describe('el menú de cada rol', () => {
  const rutasDe = (rol: keyof typeof AUTHORITIES) =>
    menuPara(AUTHORITIES[rol], { conPendientes: true }).map((s) => s.ruta);

  it('un EMPLEADO no ve nada de gestión', () => {
    const menu = menuPara(AUTHORITIES.EMPLEADO, { conPendientes: true });
    expect(menu.filter((s) => s.grupo === 'gestion')).toEqual([]);
    expect(menu.map((s) => s.ruta)).toContain('fichar');
  });

  it('un GESTOR ve su equipo, pero no los informes ni los borrados', () => {
    const menu = rutasDe('GESTOR');
    expect(menu).toEqual(expect.arrayContaining(['gestion', 'equipo', 'ausencias-equipo/pendientes', 'analitica']));
    expect(menu).not.toContain('informes');
    expect(menu).not.toContain('borrados');
  });

  it('las denuncias recibidas son solo del ADMIN (Ley 2/2023)', () => {
    expect(rutasDe('RRHH')).not.toContain('canal-denuncias');
    expect(rutasDe('ADMIN')).toContain('canal-denuncias');
  });

  it('sin las pendientes, el menú solo tiene lo que ya existe', () => {
    const menu = menuPara(AUTHORITIES.ADMIN);
    expect(menu.every((s) => s.pagina !== undefined)).toBe(true);
    expect(menu.map((s) => s.ruta)).toEqual(disponibles().filter((s) => s.enMenu).map((s) => s.ruta));
  });
});

describe('la barra inferior del móvil', () => {
  it('con el menú entero, las cuatro principales y «Más»', () => {
    const { enBarra, conMas } = barraInferior(menuPara(AUTHORITIES.EMPLEADO, { conPendientes: true }));
    expect(enBarra.map((s) => s.ruta)).toEqual(['fichar', 'historial', 'ausencias', 'calendario']);
    expect(conMas).toBe(true);
  });

  it('si caben todas (cinco), todas y sin «Más»', () => {
    const cinco = menuPara(AUTHORITIES.EMPLEADO, { conPendientes: true }).slice(0, 5);
    expect(barraInferior(cinco)).toEqual({ enBarra: cinco, conMas: false });
  });

  it('con una sola sección no hay barra', () => {
    expect(barraInferior(menuPara(AUTHORITIES.EMPLEADO))).toEqual({ enBarra: [], conMas: false });
  });
});

describe('destinoDeAviso', () => {
  it('un destino con página lleva a su URL, que es el propio destino', () => {
    expect(destinoDeAviso('fichar')).toBe('/fichar');
  });

  it('un destino sin página todavía no navega', () => {
    const pendiente = SECCIONES.find((s) => s.pagina === undefined);
    expect(pendiente).toBeDefined();
    expect(destinoDeAviso(pendiente?.ruta)).toBeNull();
  });

  it('un destino desconocido o vacío no navega, y no rompe nada', () => {
    expect(destinoDeAviso('una-pantalla-del-futuro')).toBeNull();
    expect(destinoDeAviso('')).toBeNull();
    expect(destinoDeAviso(undefined)).toBeNull();
  });
});
