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
const Fichar = lazy(() => import('../paginas/jornada/Fichar').then((m) => ({ default: m.Fichar })));
const Historial = lazy(() => import('../paginas/historial/Historial').then((m) => ({ default: m.Historial })));
const Ausencias = lazy(() => import('../paginas/ausencias/Ausencias').then((m) => ({ default: m.Ausencias })));
const Calendario = lazy(() => import('../paginas/ausencias/Calendario').then((m) => ({ default: m.Calendario })));
const Avisos = lazy(() => import('../paginas/cuenta/Avisos').then((m) => ({ default: m.Avisos })));
const Perfil = lazy(() => import('../paginas/cuenta/Perfil').then((m) => ({ default: m.PaginaPerfil })));
const Ajustes = lazy(() => import('../paginas/cuenta/Ajustes').then((m) => ({ default: m.Ajustes })));
const MiCuadrante = lazy(() => import('../paginas/cuadrante/MiCuadrante').then((m) => ({ default: m.MiCuadrante })));
const Incidencias = lazy(() => import('../paginas/cuadrante/Incidencias').then((m) => ({ default: m.Incidencias })));
const Firmas = lazy(() => import('../paginas/cuadrante/Firmas').then((m) => ({ default: m.Firmas })));
const HorasExtra = lazy(() => import('../paginas/revisiones/HorasExtra').then((m) => ({ default: m.HorasExtra })));
const Correcciones = lazy(() => import('../paginas/revisiones/Correcciones').then((m) => ({ default: m.Correcciones })));
const Ofertas = lazy(() => import('../paginas/ofertas/Ofertas').then((m) => ({ default: m.Ofertas })));
const MisCandidaturas = lazy(() => import('../paginas/ofertas/Ofertas').then((m) => ({ default: m.PaginaMisCandidaturas })));
const Denuncias = lazy(() => import('../paginas/denuncias/Denuncias').then((m) => ({ default: m.Denuncias })));
const HistorialEquipo = lazy(() => import('../paginas/equipo/HistorialEquipo').then((m) => ({ default: m.HistorialEquipo })));
const PanelGestion = lazy(() => import('../paginas/gestion/PanelGestion').then((m) => ({ default: m.PanelGestion })));
const AusenciasEquipo = lazy(() => import('../paginas/gestion/AusenciasEquipo').then((m) => ({ default: m.AusenciasEquipo })));
const AusenciasResueltas = lazy(() => import('../paginas/gestion/AusenciasEquipo').then((m) => ({ default: m.PaginaAusenciasResueltas })));
const Plantilla = lazy(() => import('../paginas/plantilla/Plantilla').then((m) => ({ default: m.Plantilla })));
const Departamentos = lazy(() => import('../paginas/plantilla/Departamentos').then((m) => ({ default: m.Departamentos })));
const Proyectos = lazy(() => import('../paginas/proyectos/Proyectos').then((m) => ({ default: m.Proyectos })));
const CalendarioLaboral = lazy(() => import('../paginas/proyectos/CalendarioLaboral').then((m) => ({ default: m.CalendarioLaboral })));
const PanelEmpresa = lazy(() => import('../paginas/empresa/PanelEmpresa').then((m) => ({ default: m.PanelEmpresa })));
const Informes = lazy(() => import('../paginas/empresa/Informes').then((m) => ({ default: m.Informes })));
const Integridad = lazy(() => import('../paginas/empresa/Integridad').then((m) => ({ default: m.Integridad })));
const Borrados = lazy(() => import('../paginas/borrados/Borrados').then((m) => ({ default: m.Borrados })));
const GestionOfertas = lazy(() => import('../paginas/ofertas/GestionOfertas').then((m) => ({ default: m.GestionOfertas })));
const CanalDenuncias = lazy(() => import('../paginas/denuncias/CanalDenuncias').then((m) => ({ default: m.CanalDenuncias })));
const VisadoFirmas = lazy(() => import('../paginas/cuadrante/VisadoFirmas').then((m) => ({ default: m.VisadoFirmas })));

const S = T.navegacion.secciones;

export const SECCIONES: readonly Seccion[] = [
  /* ---- Lo mío ---- */
  { ruta: 'fichar', etiqueta: S.fichar, icono: 'reloj', grupo: 'personal', enMenu: true, principal: true, pagina: Fichar },
  { ruta: 'historial', etiqueta: S.historial, icono: 'historial', grupo: 'personal', enMenu: true, principal: true, pagina: Historial },
  { ruta: 'ausencias', etiqueta: S.ausencias, icono: 'calendario', grupo: 'personal', requiere: 'ausencia:leer', enMenu: true, principal: true, pagina: Ausencias },
  { ruta: 'calendario', etiqueta: S.calendario, icono: 'calendario', grupo: 'personal', requiere: 'calendario:leer', enMenu: true, principal: true, pagina: Calendario },
  { ruta: 'avisos', etiqueta: S.avisos, icono: 'campana', grupo: 'personal', enMenu: false, pagina: Avisos },
  { ruta: 'perfil', etiqueta: S.perfil, icono: 'persona', grupo: 'personal', enMenu: false, pagina: Perfil },
  { ruta: 'ajustes', etiqueta: S.ajustes, icono: 'persona', grupo: 'personal', enMenu: false, pagina: Ajustes },
  { ruta: 'cuadrante', etiqueta: S.cuadrante, icono: 'calendario', grupo: 'personal', requiere: 'cuadrante:leer', enMenu: true, pagina: MiCuadrante },
  // Lo propio y, con `cuadrante:incidencias:revisar`, la bandeja del equipo:
  // la misma página, como en Android. Por eso no pide permiso para entrar.
  { ruta: 'incidencias', etiqueta: S.incidencias, icono: 'documento', grupo: 'personal', enMenu: true, pagina: Incidencias },
  { ruta: 'firmas', etiqueta: S.firmas, icono: 'documento', grupo: 'personal', enMenu: true, pagina: Firmas },
  { ruta: 'horas-extra', etiqueta: S.horasExtra, icono: 'reloj', grupo: 'personal', enMenu: true, pagina: HorasExtra },
  { ruta: 'correcciones/pendientes', etiqueta: S.correcciones, icono: 'documento', grupo: 'personal', enMenu: true, pagina: Correcciones },
  { ruta: 'ofertas', etiqueta: S.ofertas, icono: 'documento', grupo: 'personal', requiere: 'oferta:leer', enMenu: true, pagina: Ofertas },
  { ruta: 'mis-candidaturas', etiqueta: S.misCandidaturas, icono: 'documento', grupo: 'personal', requiere: 'candidatura:crear', enMenu: false, pagina: MisCandidaturas },
  { ruta: 'denuncias', etiqueta: S.denuncias, icono: 'escudo', grupo: 'personal', requiere: 'denuncia:crear', enMenu: true, pagina: Denuncias },

  /* ---- Gestión ---- */
  { ruta: 'gestion', etiqueta: S.gestion, icono: 'panel', grupo: 'gestion', requiere: 'fichaje:leer:equipo', enMenu: true, pagina: PanelGestion },
  { ruta: 'equipo', etiqueta: S.equipo, icono: 'grupo', grupo: 'gestion', requiere: 'fichaje:leer:equipo', enMenu: true, pagina: HistorialEquipo },
  { ruta: 'ausencias-equipo/pendientes', etiqueta: S.ausenciasEquipo, icono: 'calendario', grupo: 'gestion', requiere: 'ausencia:aprobar', enMenu: true, pagina: AusenciasEquipo },
  { ruta: 'ausencias-equipo/resueltas', etiqueta: S.ausenciasEquipoResueltas, icono: 'calendario', grupo: 'gestion', requiere: 'ausencia:aprobar', enMenu: false, pagina: AusenciasResueltas },
  { ruta: 'empresa', etiqueta: S.empresa, icono: 'grafico', grupo: 'gestion', requiere: 'fichaje:leer:equipo', enMenu: true, pagina: PanelEmpresa },
  { ruta: 'plantilla', etiqueta: S.plantilla, icono: 'grupo', grupo: 'gestion', requiere: 'empleado:leer', enMenu: true, pagina: Plantilla },
  { ruta: 'departamentos', etiqueta: S.departamentos, icono: 'grupo', grupo: 'gestion', requiere: 'departamento:gestionar', enMenu: true, pagina: Departamentos },
  { ruta: 'proyectos', etiqueta: S.proyectos, icono: 'documento', grupo: 'gestion', requiere: 'proyecto:gestionar', enMenu: true, pagina: Proyectos },
  { ruta: 'calendario-laboral', etiqueta: S.calendarioLaboral, icono: 'calendario', grupo: 'gestion', requiere: 'calendario:gestionar', enMenu: true, pagina: CalendarioLaboral },
  { ruta: 'informes', etiqueta: S.informes, icono: 'documento', grupo: 'gestion', requiere: 'informe:exportar', enMenu: true, pagina: Informes },
  { ruta: 'borrados', etiqueta: S.borrados, icono: 'escudo', grupo: 'gestion', requiere: 'empleado:gestionar', enMenu: true, pagina: Borrados },
  { ruta: 'gestion-ofertas', etiqueta: S.gestionOfertas, icono: 'documento', grupo: 'gestion', requiere: 'oferta:publicar', enMenu: true, pagina: GestionOfertas },
  { ruta: 'canal-denuncias', etiqueta: S.canalDenuncias, icono: 'escudo', grupo: 'gestion', requiere: 'denuncia:instruir', enMenu: true, pagina: CanalDenuncias },
  { ruta: 'integridad', etiqueta: S.integridad, icono: 'escudo', grupo: 'gestion', requiere: 'fichaje:auditoria', enMenu: true, pagina: Integridad },
  { ruta: 'cuadrantes', etiqueta: S.cuadrantes, icono: 'calendario', grupo: 'gestion', requiere: 'cuadrante:gestionar', enMenu: true, llegaEn: 'W7' },
  { ruta: 'analitica', etiqueta: S.analitica, icono: 'grafico', grupo: 'gestion', requiere: 'analitica:leer', enMenu: true, llegaEn: 'W7' },
  { ruta: 'visado-firmas', etiqueta: S.visadoFirmas, icono: 'documento', grupo: 'gestion', requiere: 'firma:visar', enMenu: true, pagina: VisadoFirmas },
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
