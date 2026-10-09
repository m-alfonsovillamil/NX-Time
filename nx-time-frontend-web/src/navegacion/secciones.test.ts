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
import { AUTHORITIES, PLATAFORMA_VER } from '../pruebas/api';
import {
  ICONOS_DE_SUBGRUPO,
  SECCIONES,
  SUBGRUPOS,
  barraInferior,
  destinoDeAviso,
  disponibles,
  menuAgrupado,
  menuPara,
  seccionDeRuta,
} from './secciones';

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
    // Todo lo que hay, salvo la plataforma: no es de ningún rol (ADR 040).
    expect(menu.map((s) => s.ruta)).toEqual(
      disponibles()
        .filter((s) => s.enMenu && s.grupo !== 'plataforma')
        .map((s) => s.ruta),
    );
  });
});

describe('la plataforma', () => {
  const ROLES = ['EMPLEADO', 'GESTOR', 'RRHH', 'ADMIN'] as const;

  it('ningún rol la ve: ni el ADMIN', () => {
    for (const rol of ROLES) {
      expect(menuPara(AUTHORITIES[rol]).filter((s) => s.grupo === 'plataforma'), rol).toEqual([]);
    }
  });

  it('el backend la declara fuera de los roles, y ningún rol la trae', () => {
    const backend = authoritiesDelBackend();
    expect([...(backend['FUERA_DE_LOS_ROLES'] ?? [])]).toEqual([PLATAFORMA_VER]);
    for (const rol of ROLES) {
      expect(backend[rol]?.has(PLATAFORMA_VER), rol).toBe(false);
    }
  });

  it('con el permiso, una empleada ve su apartado además de lo suyo, y nada de gestión', () => {
    const menu = menuPara([...AUTHORITIES.EMPLEADO, PLATAFORMA_VER]);
    expect(menuAgrupado(menu).map((g) => g.grupo)).toEqual(['personal', 'plataforma']);
    expect(menu.filter((s) => s.grupo === 'plataforma').map((s) => s.ruta)).toEqual(['plataforma']);
  });

  it('todo lo del grupo pide el permiso: una sección nueva no se cuela sin él', () => {
    expect(SECCIONES.filter((s) => s.grupo === 'plataforma' && s.requiere !== PLATAFORMA_VER)).toEqual([]);
  });

  it('el detalle de una empresa es la misma sección', () => {
    expect(seccionDeRuta('/plataforma')?.grupo).toBe('plataforma');
  });
});

describe('los apartados del menú', () => {
  const apartadosDe = (rol: keyof typeof AUTHORITIES) =>
    menuAgrupado(menuPara(AUTHORITIES[rol])).map(({ grupo, apartados }) => ({
      grupo,
      apartados: apartados.map((a) => `${a.subgrupo}: ${a.secciones.map((s) => s.ruta).join(', ')}`),
    }));

  it('cada sección está en un apartado de su grupo', () => {
    expect(SECCIONES.filter((s) => !SUBGRUPOS[s.grupo].includes(s.subgrupo)).map((s) => s.ruta)).toEqual([]);
  });

  it('no hay dos entradas del menú con el mismo icono', () => {
    // Con catorce iconos para treinta secciones, «documento» salía nueve veces
    // y el icono dejaba de servir para encontrar nada de un vistazo.
    const visibles = SECCIONES.filter((s) => s.enMenu);
    const repetidos = visibles
      .filter((s, i) => visibles.findIndex((o) => o.icono === s.icono) !== i)
      .map((s) => `${s.ruta}: ${s.icono}`);
    expect(repetidos).toEqual([]);
  });

  it('ningún apartado comparte icono con una entrada del menú ni con otro apartado', () => {
    const deSecciones = new Set(SECCIONES.filter((s) => s.enMenu).map((s) => s.icono));
    const deApartados = Object.values(ICONOS_DE_SUBGRUPO);
    expect(deApartados.filter((i) => deSecciones.has(i))).toEqual([]);
    expect(new Set(deApartados).size).toBe(deApartados.length);
  });

  it('agrupar no quita ni repite ninguna entrada del menú', () => {
    for (const rol of ['EMPLEADO', 'GESTOR', 'RRHH', 'ADMIN'] as const) {
      const menu = menuPara(AUTHORITIES[rol]);
      const agrupadas = menuAgrupado(menu).flatMap((g) => g.apartados.flatMap((a) => a.secciones));
      expect(agrupadas.map((s) => s.ruta).sort(), rol).toEqual(menu.map((s) => s.ruta).sort());
    }
  });

  it('un ADMIN ve siete apartados en vez de 28 entradas seguidas', () => {
    expect(menuPara(AUTHORITIES.ADMIN)).toHaveLength(28);
    expect(apartadosDe('ADMIN')).toEqual([
      {
        grupo: 'personal',
        apartados: [
          'jornada: fichar, historial, cuadrante, incidencias, firmas, horas-extra, correcciones/pendientes',
          'ausencias: ausencias, calendario',
          'en-la-empresa: ofertas, denuncias',
        ],
      },
      {
        grupo: 'gestion',
        apartados: [
          'equipo: gestion, equipo, ausencias-equipo/pendientes',
          'organizacion: plantilla, departamentos, proyectos, calendario-laboral, cuadrantes',
          'control: empresa, informes, borrados, integridad, analitica, visado-firmas',
          'administracion: gestion-ofertas, canal-denuncias, ajustes-empresa',
        ],
      },
    ]);
  });

  it('un EMPLEADO solo ve los apartados de lo suyo', () => {
    expect(apartadosDe('EMPLEADO').map((g) => g.grupo)).toEqual(['personal']);
  });

  it('la sección de una ruta, también con más tramos detrás', () => {
    expect(seccionDeRuta('/correcciones/pendientes')?.subgrupo).toBe('jornada');
    expect(seccionDeRuta('/cuadrantes/3')?.ruta).toBe('cuadrantes');
    expect(seccionDeRuta('/ausencias-equipo/resueltas')?.subgrupo).toBe('equipo');
    expect(seccionDeRuta('/no-existe')).toBeUndefined();
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
    const una = menuPara(AUTHORITIES.EMPLEADO, { conPendientes: true }).slice(0, 1);
    expect(barraInferior(una)).toEqual({ enBarra: [], conMas: false });
  });
});

describe('destinoDeAviso', () => {
  it('un destino con página lleva a su URL, que es el propio destino', () => {
    expect(destinoDeAviso('fichar')).toBe('/fichar');
  });

  /* Desde W7 la web tiene todas las pantallas de la app: si una sección nueva
     se queda sin página, que se note aquí y no en un aviso que no lleva a nada. */
  it('todas las secciones tienen página', () => {
    expect(SECCIONES.filter((s) => s.pagina === undefined).map((s) => s.ruta)).toEqual([]);
  });

  it('un destino desconocido o vacío no navega, y no rompe nada', () => {
    expect(destinoDeAviso('una-pantalla-del-futuro')).toBeNull();
    expect(destinoDeAviso('')).toBeNull();
    expect(destinoDeAviso(undefined)).toBeNull();
  });
});
