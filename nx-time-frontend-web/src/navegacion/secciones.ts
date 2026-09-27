/**
 * El catálogo de secciones: de aquí salen el menú, las rutas y los destinos de los avisos.
 *
 * Una sola lista para las tres cosas, porque son la misma pregunta —«¿qué
 * páginas hay y quién las ve?»— y en Android hicieron falta tres sitios
 * (`Pantalla`, `Permisos` y `DestinoDeAviso.kt`) que había que tocar a la vez.
 *
 * ## La URL es el destino del aviso (ADR 029, decisión 4)
 *
 * El backend manda con cada aviso un destino lógico (`ausencias`,
 * `correcciones/pendientes`…; ver `NoticeType.java`). En la web ese destino
 * **es** la ruta: el aviso `ausencias` lleva a `/ausencias`, sin tabla de
 * traducción, y un correo puede enlazar a la web con el mismo texto. El test
 * de este fichero lee `NoticeType.java` y falla si algún destino no tiene
 * sección aquí.
 *
 * ## Las que aún no tienen página
 *
 * Todas las secciones del plan están ya en el catálogo, con la fase que traerá
 * su página (`llegaEn`). Mientras no la tengan no salen en el menú ni tienen
 * ruta, y un aviso que apunte a ellas se lee pero no navega — igual que en
 * Android cuando la app instalada no conoce un destino nuevo. Tenerlas aquí
 * desde W0 permite comprobar ya que cada `requiere` es una authority que el
 * backend conoce, y que el menú de cada rol saldrá como debe.
 */

import { lazy, type ComponentType, type LazyExoticComponent } from 'react';

import type { NombreIcono } from '../componentes/Icono';
import { T } from '../i18n/es';

export type Grupo = 'personal' | 'gestion';
export type Fase = 'W2' | 'W3' | 'W4' | 'W5' | 'W6' | 'W7';

export interface Seccion {
  /** Sin barra inicial. Si la sección es destino de algún aviso, es ese destino tal cual. */
  ruta: string;
  etiqueta: string;
  icono: NombreIcono;
  grupo: Grupo;
  /** La authority que hace falta, resuelta por el servidor. Sin ella, la ve todo el que ha entrado. */
  requiere?: string;
  /** Si sale en el menú. Las que no, se alcanzan desde otra página, la campana o el menú de usuario. */
  enMenu: boolean;
  /** En el móvil, en la barra inferior y no en «Más». Como mucho cuatro: la quinta casilla es «Más». */
  principal?: boolean;
  /** La página, cargada al visitarla (`React.lazy`). Sin ella, la sección todavía no existe en la web. */
  pagina?: LazyExoticComponent<ComponentType>;
  /** La fase del plan que traerá la página. */
  llegaEn?: Fase;
}

/*
 * Cada página en su propio trozo de JS: quien solo ficha no se descarga el
 * editor de cuadrantes. El `then` es porque las páginas se exportan con
 * nombre y `lazy` quiere un `default`.
 */
const Fichar = lazy(() => import('../paginas/Fichar').then((m) => ({ default: m.Fichar })));

const S = T.navegacion.secciones;

export const SECCIONES: readonly Seccion[] = [
  /* ---- Lo mío ---- */
  { ruta: 'fichar', etiqueta: S.fichar, icono: 'reloj', grupo: 'personal', enMenu: true, principal: true, pagina: Fichar },
  { ruta: 'historial', etiqueta: S.historial, icono: 'historial', grupo: 'personal', enMenu: true, principal: true, llegaEn: 'W2' },
  { ruta: 'ausencias', etiqueta: S.ausencias, icono: 'calendario', grupo: 'personal', enMenu: true, principal: true, llegaEn: 'W3' },
  { ruta: 'calendario', etiqueta: S.calendario, icono: 'calendario', grupo: 'personal', enMenu: true, principal: true, llegaEn: 'W3' },
  { ruta: 'avisos', etiqueta: S.avisos, icono: 'campana', grupo: 'personal', enMenu: false, llegaEn: 'W3' },
  { ruta: 'perfil', etiqueta: S.perfil, icono: 'persona', grupo: 'personal', enMenu: false, llegaEn: 'W3' },
  { ruta: 'ajustes', etiqueta: S.ajustes, icono: 'persona', grupo: 'personal', enMenu: false, llegaEn: 'W3' },
  { ruta: 'cuadrante', etiqueta: S.cuadrante, icono: 'calendario', grupo: 'personal', requiere: 'cuadrante:leer', enMenu: true, llegaEn: 'W4' },
  // Lo propio y, con `cuadrante:incidencias:revisar`, la bandeja del equipo:
  // la misma página, como en Android. Por eso no pide permiso para entrar.
  { ruta: 'incidencias', etiqueta: S.incidencias, icono: 'documento', grupo: 'personal', enMenu: true, llegaEn: 'W4' },
  { ruta: 'firmas', etiqueta: S.firmas, icono: 'documento', grupo: 'personal', enMenu: true, llegaEn: 'W4' },
  { ruta: 'horas-extra', etiqueta: S.horasExtra, icono: 'reloj', grupo: 'personal', enMenu: true, llegaEn: 'W4' },
  { ruta: 'correcciones/pendientes', etiqueta: S.correcciones, icono: 'documento', grupo: 'personal', enMenu: true, llegaEn: 'W4' },
  { ruta: 'ofertas', etiqueta: S.ofertas, icono: 'documento', grupo: 'personal', requiere: 'oferta:leer', enMenu: true, llegaEn: 'W4' },
  { ruta: 'mis-candidaturas', etiqueta: S.misCandidaturas, icono: 'documento', grupo: 'personal', requiere: 'candidatura:crear', enMenu: false, llegaEn: 'W4' },
  { ruta: 'denuncias', etiqueta: S.denuncias, icono: 'escudo', grupo: 'personal', requiere: 'denuncia:crear', enMenu: true, llegaEn: 'W4' },

  /* ---- Gestión ---- */
  { ruta: 'gestion', etiqueta: S.gestion, icono: 'panel', grupo: 'gestion', requiere: 'fichaje:leer:equipo', enMenu: true, llegaEn: 'W5' },
  { ruta: 'equipo', etiqueta: S.equipo, icono: 'grupo', grupo: 'gestion', requiere: 'fichaje:leer:equipo', enMenu: true, llegaEn: 'W5' },
  { ruta: 'ausencias-equipo/pendientes', etiqueta: S.ausenciasEquipo, icono: 'calendario', grupo: 'gestion', requiere: 'ausencia:aprobar', enMenu: true, llegaEn: 'W5' },
  { ruta: 'ausencias-equipo/resueltas', etiqueta: S.ausenciasEquipoResueltas, icono: 'calendario', grupo: 'gestion', requiere: 'ausencia:aprobar', enMenu: false, llegaEn: 'W5' },
  { ruta: 'empresa', etiqueta: S.empresa, icono: 'grafico', grupo: 'gestion', requiere: 'fichaje:leer:equipo', enMenu: true, llegaEn: 'W6' },
  { ruta: 'plantilla', etiqueta: S.plantilla, icono: 'grupo', grupo: 'gestion', requiere: 'empleado:leer', enMenu: true, llegaEn: 'W6' },
  { ruta: 'departamentos', etiqueta: S.departamentos, icono: 'grupo', grupo: 'gestion', requiere: 'departamento:gestionar', enMenu: true, llegaEn: 'W6' },
  { ruta: 'proyectos', etiqueta: S.proyectos, icono: 'documento', grupo: 'gestion', requiere: 'proyecto:gestionar', enMenu: true, llegaEn: 'W6' },
  { ruta: 'calendario-laboral', etiqueta: S.calendarioLaboral, icono: 'calendario', grupo: 'gestion', requiere: 'calendario:gestionar', enMenu: true, llegaEn: 'W6' },
  { ruta: 'informes', etiqueta: S.informes, icono: 'documento', grupo: 'gestion', requiere: 'informe:exportar', enMenu: true, llegaEn: 'W6' },
  { ruta: 'borrados', etiqueta: S.borrados, icono: 'escudo', grupo: 'gestion', requiere: 'empleado:gestionar', enMenu: true, llegaEn: 'W6' },
  { ruta: 'gestion-ofertas', etiqueta: S.gestionOfertas, icono: 'documento', grupo: 'gestion', requiere: 'oferta:publicar', enMenu: true, llegaEn: 'W6' },
  { ruta: 'canal-denuncias', etiqueta: S.canalDenuncias, icono: 'escudo', grupo: 'gestion', requiere: 'denuncia:instruir', enMenu: true, llegaEn: 'W6' },
  { ruta: 'integridad', etiqueta: S.integridad, icono: 'escudo', grupo: 'gestion', requiere: 'fichaje:auditoria', enMenu: true, llegaEn: 'W6' },
  { ruta: 'cuadrantes', etiqueta: S.cuadrantes, icono: 'calendario', grupo: 'gestion', requiere: 'cuadrante:gestionar', enMenu: true, llegaEn: 'W7' },
  { ruta: 'analitica', etiqueta: S.analitica, icono: 'grafico', grupo: 'gestion', requiere: 'analitica:leer', enMenu: true, llegaEn: 'W7' },
  { ruta: 'visado-firmas', etiqueta: S.visadoFirmas, icono: 'documento', grupo: 'gestion', requiere: 'firma:visar', enMenu: true, llegaEn: 'W7' },
];

/** Si esta cuenta puede ver la sección, según las authorities que mandó el servidor. */
export function permitida(seccion: Seccion, authorities: readonly string[]): boolean {
  return seccion.requiere === undefined || authorities.includes(seccion.requiere);
}

/** Las secciones que ya tienen página. Son las que tienen ruta. */
export function disponibles(): Seccion[] {
  return SECCIONES.filter((s) => s.pagina !== undefined);
}

/**
 * El menú de esta cuenta, en el orden del catálogo.
 *
 * @param conPendientes incluye las que aún no tienen página. Solo para los
 *   tests: así se comprueba desde ya el menú que verá cada rol al final.
 */
export function menuPara(authorities: readonly string[], { conPendientes = false } = {}): Seccion[] {
  return SECCIONES.filter(
    (s) => s.enMenu && permitida(s, authorities) && (conPendientes || s.pagina !== undefined),
  );
}

/** En el móvil caben cinco casillas (el límite de Material 3, como en la app). */
const CASILLAS = 5;

/**
 * Qué va en la barra inferior del móvil.
 *
 * Si caben todas, todas. Si no, las cuatro primeras principales (luego las
 * demás, en orden) y la quinta casilla es «Más», que abre el menú entero. Con
 * una sola sección no hay barra: no llevaría a ningún sitio.
 */
export function barraInferior(menu: readonly Seccion[]): { enBarra: Seccion[]; conMas: boolean } {
  if (menu.length < 2) return { enBarra: [], conMas: false };
  if (menu.length <= CASILLAS) return { enBarra: [...menu], conMas: false };
  const ordenadas = [...menu.filter((s) => s.principal === true), ...menu.filter((s) => s.principal !== true)];
  return { enBarra: ordenadas.slice(0, CASILLAS - 1), conMas: true };
}

/**
 * A dónde lleva un aviso, o `null` si no lleva a ninguna parte todavía.
 *
 * `null` también cuando la sección existe pero su página no ha llegado: el
 * aviso se lee igual y no navega, que es la misma degradación que Android
 * aplica a un destino que su versión no conoce.
 */
export function destinoDeAviso(rutaDestino: string | null | undefined): string | null {
  if (rutaDestino === null || rutaDestino === undefined || rutaDestino === '') return null;
  const seccion = SECCIONES.find((s) => s.ruta === rutaDestino);
  return seccion?.pagina !== undefined ? `/${seccion.ruta}` : null;
}
