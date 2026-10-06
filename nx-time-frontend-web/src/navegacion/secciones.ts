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

/**
 * Los apartados plegables de cada grupo en el menú lateral (1/10/2026). Un
 * ADMIN veía 28 entradas seguidas; así ve siete apartados, como los submenús
 * de la app. `cuenta` no sale en el menú: son las secciones del menú de usuario.
 */
export type Subgrupo =
  | 'jornada'
  | 'ausencias'
  | 'en-la-empresa'
  | 'cuenta'
  | 'equipo'
  | 'organizacion'
  | 'control'
  | 'administracion';

/** Los subgrupos de cada grupo, en el orden en que salen en el menú. */
export const SUBGRUPOS: Readonly<Record<Grupo, readonly Subgrupo[]>> = {
  personal: ['jornada', 'ausencias', 'en-la-empresa', 'cuenta'],
  gestion: ['equipo', 'organizacion', 'control', 'administracion'],
};

/**
 * El icono de cada apartado plegable. Distinto de los de sus secciones: un
 * apartado y su primera entrada con el mismo dibujo se leen como una entrada
 * repetida (lo comprueba el test).
 */
export const ICONOS_DE_SUBGRUPO: Readonly<Record<Subgrupo, NombreIcono>> = {
  jornada: 'lapso',
  ausencias: 'vacaciones',
  'en-la-empresa': 'edificio',
  cuenta: 'persona',
  equipo: 'grupo',
  organizacion: 'organizar',
  control: 'control',
  administracion: 'escudo',
};

/**
 * Una página perezosa que además se puede **pedir por adelantado**.
 *
 * `React.lazy` solo descarga el trozo de JS al pintar la página, es decir,
 * después del clic: en una conexión lenta se veía el esqueleto medio segundo
 * en cada cambio de sección. Con `precargar`, el menú lo pide al pasar el
 * ratón o al llegar con el tabulador, y para cuando se pulsa ya está. El
 * navegador no descarga dos veces el mismo módulo, así que precargar y luego
 * pintar es una sola petición.
 */
export type PaginaPerezosa = LazyExoticComponent<ComponentType> & { precargar: () => void };

function perezosa(cargar: () => Promise<{ default: ComponentType }>): PaginaPerezosa {
  return Object.assign(lazy(cargar), {
    // Si falla (sin red), no pasa nada: se volverá a intentar al pulsar.
    precargar: () => void cargar().catch(() => undefined),
  });
}

export interface Seccion {
  /** Sin barra inicial. Si la sección es destino de algún aviso, es ese destino tal cual. */
  ruta: string;
  etiqueta: string;
  icono: NombreIcono;
  grupo: Grupo;
  /** El apartado plegable del menú lateral. Tiene que ser de los de su `grupo`. */
  subgrupo: Subgrupo;
  /** La authority que hace falta, resuelta por el servidor. Sin ella, la ve todo el que ha entrado. */
  requiere?: string;
  /** Si sale en el menú. Las que no, se alcanzan desde otra página, la campana o el menú de usuario. */
  enMenu: boolean;
  /** En el móvil, en la barra inferior y no en «Más». Como mucho cuatro: la quinta casilla es «Más». */
  principal?: boolean;
  /** La página, cargada al visitarla (`React.lazy`). Sin ella, la sección todavía no existe en la web. */
  pagina?: PaginaPerezosa;
  /** La fase del plan que traerá la página. */
  llegaEn?: Fase;
}

/*
 * Cada página en su propio trozo de JS: quien solo ficha no se descarga el
 * editor de cuadrantes. El `then` es porque las páginas se exportan con
 * nombre y `lazy` quiere un `default`.
 */
const Fichar = perezosa(() => import('../paginas/jornada/Fichar').then((m) => ({ default: m.Fichar })));
const Historial = perezosa(() => import('../paginas/historial/Historial').then((m) => ({ default: m.Historial })));
const Ausencias = perezosa(() => import('../paginas/ausencias/Ausencias').then((m) => ({ default: m.Ausencias })));
const Calendario = perezosa(() => import('../paginas/ausencias/Calendario').then((m) => ({ default: m.Calendario })));
const Avisos = perezosa(() => import('../paginas/cuenta/Avisos').then((m) => ({ default: m.Avisos })));
const Perfil = perezosa(() => import('../paginas/cuenta/Perfil').then((m) => ({ default: m.PaginaPerfil })));
const Ajustes = perezosa(() => import('../paginas/cuenta/Ajustes').then((m) => ({ default: m.Ajustes })));
const MiCuadrante = perezosa(() => import('../paginas/cuadrante/MiCuadrante').then((m) => ({ default: m.MiCuadrante })));
const Incidencias = perezosa(() => import('../paginas/cuadrante/Incidencias').then((m) => ({ default: m.Incidencias })));
const Firmas = perezosa(() => import('../paginas/cuadrante/Firmas').then((m) => ({ default: m.Firmas })));
const HorasExtra = perezosa(() => import('../paginas/revisiones/HorasExtra').then((m) => ({ default: m.HorasExtra })));
const Correcciones = perezosa(() => import('../paginas/revisiones/Correcciones').then((m) => ({ default: m.Correcciones })));
const Ofertas = perezosa(() => import('../paginas/ofertas/Ofertas').then((m) => ({ default: m.Ofertas })));
const MisCandidaturas = perezosa(() => import('../paginas/ofertas/Ofertas').then((m) => ({ default: m.PaginaMisCandidaturas })));
const Denuncias = perezosa(() => import('../paginas/denuncias/Denuncias').then((m) => ({ default: m.Denuncias })));
const HistorialEquipo = perezosa(() => import('../paginas/equipo/HistorialEquipo').then((m) => ({ default: m.HistorialEquipo })));
const PanelGestion = perezosa(() => import('../paginas/gestion/PanelGestion').then((m) => ({ default: m.PanelGestion })));
const AusenciasEquipo = perezosa(() => import('../paginas/gestion/AusenciasEquipo').then((m) => ({ default: m.AusenciasEquipo })));
const AusenciasResueltas = perezosa(() => import('../paginas/gestion/AusenciasEquipo').then((m) => ({ default: m.PaginaAusenciasResueltas })));
const Plantilla = perezosa(() => import('../paginas/plantilla/Plantilla').then((m) => ({ default: m.Plantilla })));
const Departamentos = perezosa(() => import('../paginas/plantilla/Departamentos').then((m) => ({ default: m.Departamentos })));
const Proyectos = perezosa(() => import('../paginas/proyectos/Proyectos').then((m) => ({ default: m.Proyectos })));
const CalendarioLaboral = perezosa(() => import('../paginas/proyectos/CalendarioLaboral').then((m) => ({ default: m.CalendarioLaboral })));
const PanelEmpresa = perezosa(() => import('../paginas/empresa/PanelEmpresa').then((m) => ({ default: m.PanelEmpresa })));
const Informes = perezosa(() => import('../paginas/empresa/Informes').then((m) => ({ default: m.Informes })));
const Integridad = perezosa(() => import('../paginas/empresa/Integridad').then((m) => ({ default: m.Integridad })));
const AjustesEmpresa = perezosa(() =>
  import('../paginas/empresa/AjustesEmpresa').then((m) => ({ default: m.AjustesEmpresa })),
);
const TarjetasKiosco = perezosa(() =>
  import('../paginas/plantilla/TarjetasKiosco').then((m) => ({ default: m.TarjetasKiosco })),
);
const Borrados = perezosa(() => import('../paginas/borrados/Borrados').then((m) => ({ default: m.Borrados })));
const GestionOfertas = perezosa(() => import('../paginas/ofertas/GestionOfertas').then((m) => ({ default: m.GestionOfertas })));
const CanalDenuncias = perezosa(() => import('../paginas/denuncias/CanalDenuncias').then((m) => ({ default: m.CanalDenuncias })));
const VisadoFirmas = perezosa(() => import('../paginas/cuadrante/VisadoFirmas').then((m) => ({ default: m.VisadoFirmas })));
const Analitica = perezosa(() => import('../paginas/analitica/Analitica').then((m) => ({ default: m.Analitica })));
const Cuadrantes = perezosa(() => import('../paginas/cuadrantes/Cuadrantes').then((m) => ({ default: m.Cuadrantes })));

const S = T.navegacion.secciones;

export const SECCIONES: readonly Seccion[] = [
  /* ---- Lo mío ---- */
  { ruta: 'fichar', etiqueta: S.fichar, icono: 'reloj', grupo: 'personal', subgrupo: 'jornada', enMenu: true, principal: true, pagina: Fichar },
  { ruta: 'historial', etiqueta: S.historial, icono: 'historial', grupo: 'personal', subgrupo: 'jornada', enMenu: true, principal: true, pagina: Historial },
  { ruta: 'ausencias', etiqueta: S.ausencias, icono: 'ausencia', grupo: 'personal', subgrupo: 'ausencias', requiere: 'ausencia:leer', enMenu: true, principal: true, pagina: Ausencias },
  { ruta: 'calendario', etiqueta: S.calendario, icono: 'calendario', grupo: 'personal', subgrupo: 'ausencias', requiere: 'calendario:leer', enMenu: true, principal: true, pagina: Calendario },
  { ruta: 'avisos', etiqueta: S.avisos, icono: 'campana', grupo: 'personal', subgrupo: 'cuenta', enMenu: false, pagina: Avisos },
  { ruta: 'perfil', etiqueta: S.perfil, icono: 'persona', grupo: 'personal', subgrupo: 'cuenta', enMenu: false, pagina: Perfil },
  { ruta: 'ajustes', etiqueta: S.ajustes, icono: 'ajustes', grupo: 'personal', subgrupo: 'cuenta', enMenu: false, pagina: Ajustes },
  { ruta: 'cuadrante', etiqueta: S.cuadrante, icono: 'semana', grupo: 'personal', subgrupo: 'jornada', requiere: 'cuadrante:leer', enMenu: true, pagina: MiCuadrante },
  // Lo propio y, con `cuadrante:incidencias:revisar`, la bandeja del equipo:
  // la misma página, como en Android. Por eso no pide permiso para entrar.
  { ruta: 'incidencias', etiqueta: S.incidencias, icono: 'incidencia', grupo: 'personal', subgrupo: 'jornada', enMenu: true, pagina: Incidencias },
  { ruta: 'firmas', etiqueta: S.firmas, icono: 'firma', grupo: 'personal', subgrupo: 'jornada', enMenu: true, pagina: Firmas },
  { ruta: 'horas-extra', etiqueta: S.horasExtra, icono: 'horas-extra', grupo: 'personal', subgrupo: 'jornada', enMenu: true, pagina: HorasExtra },
  { ruta: 'correcciones/pendientes', etiqueta: S.correcciones, icono: 'correccion', grupo: 'personal', subgrupo: 'jornada', enMenu: true, pagina: Correcciones },
  { ruta: 'ofertas', etiqueta: S.ofertas, icono: 'maletin', grupo: 'personal', subgrupo: 'en-la-empresa', requiere: 'oferta:leer', enMenu: true, pagina: Ofertas },
  { ruta: 'mis-candidaturas', etiqueta: S.misCandidaturas, icono: 'maletin', grupo: 'personal', subgrupo: 'en-la-empresa', requiere: 'candidatura:crear', enMenu: false, pagina: MisCandidaturas },
  { ruta: 'denuncias', etiqueta: S.denuncias, icono: 'denuncia', grupo: 'personal', subgrupo: 'en-la-empresa', requiere: 'denuncia:crear', enMenu: true, pagina: Denuncias },

  /* ---- Gestión ---- */
  { ruta: 'gestion', etiqueta: S.gestion, icono: 'panel', grupo: 'gestion', subgrupo: 'equipo', requiere: 'fichaje:leer:equipo', enMenu: true, pagina: PanelGestion },
  { ruta: 'equipo', etiqueta: S.equipo, icono: 'supervisar', grupo: 'gestion', subgrupo: 'equipo', requiere: 'fichaje:leer:equipo', enMenu: true, pagina: HistorialEquipo },
  { ruta: 'ausencias-equipo/pendientes', etiqueta: S.ausenciasEquipo, icono: 'ausencia-aprobar', grupo: 'gestion', subgrupo: 'equipo', requiere: 'ausencia:aprobar', enMenu: true, pagina: AusenciasEquipo },
  { ruta: 'ausencias-equipo/resueltas', etiqueta: S.ausenciasEquipoResueltas, icono: 'ausencia-aprobar', grupo: 'gestion', subgrupo: 'equipo', requiere: 'ausencia:aprobar', enMenu: false, pagina: AusenciasResueltas },
  { ruta: 'empresa', etiqueta: S.empresa, icono: 'grafico', grupo: 'gestion', subgrupo: 'control', requiere: 'fichaje:leer:equipo', enMenu: true, pagina: PanelEmpresa },
  { ruta: 'plantilla', etiqueta: S.plantilla, icono: 'plantilla', grupo: 'gestion', subgrupo: 'organizacion', requiere: 'empleado:leer', enMenu: true, pagina: Plantilla },
  { ruta: 'departamentos', etiqueta: S.departamentos, icono: 'departamento', grupo: 'gestion', subgrupo: 'organizacion', requiere: 'departamento:gestionar', enMenu: true, pagina: Departamentos },
  { ruta: 'proyectos', etiqueta: S.proyectos, icono: 'proyecto', grupo: 'gestion', subgrupo: 'organizacion', requiere: 'proyecto:gestionar', enMenu: true, pagina: Proyectos },
  { ruta: 'calendario-laboral', etiqueta: S.calendarioLaboral, icono: 'festivo', grupo: 'gestion', subgrupo: 'organizacion', requiere: 'calendario:gestionar', enMenu: true, pagina: CalendarioLaboral },
  { ruta: 'informes', etiqueta: S.informes, icono: 'documento', grupo: 'gestion', subgrupo: 'control', requiere: 'informe:exportar', enMenu: true, pagina: Informes },
  { ruta: 'borrados', etiqueta: S.borrados, icono: 'papelera', grupo: 'gestion', subgrupo: 'control', requiere: 'empleado:gestionar', enMenu: true, pagina: Borrados },
  { ruta: 'gestion-ofertas', etiqueta: S.gestionOfertas, icono: 'anuncio', grupo: 'gestion', subgrupo: 'administracion', requiere: 'oferta:publicar', enMenu: true, pagina: GestionOfertas },
  { ruta: 'canal-denuncias', etiqueta: S.canalDenuncias, icono: 'mazo', grupo: 'gestion', subgrupo: 'administracion', requiere: 'denuncia:instruir', enMenu: true, pagina: CanalDenuncias },
  { ruta: 'integridad', etiqueta: S.integridad, icono: 'verificado', grupo: 'gestion', subgrupo: 'control', requiere: 'fichaje:auditoria', enMenu: true, pagina: Integridad },
  { ruta: 'cuadrantes', etiqueta: S.cuadrantes, icono: 'cuadrantes', grupo: 'gestion', subgrupo: 'organizacion', requiere: 'cuadrante:gestionar', enMenu: true, pagina: Cuadrantes },
  { ruta: 'analitica', etiqueta: S.analitica, icono: 'tendencia', grupo: 'gestion', subgrupo: 'control', requiere: 'analitica:leer', enMenu: true, pagina: Analitica },
  { ruta: 'visado-firmas', etiqueta: S.visadoFirmas, icono: 'visado', grupo: 'gestion', subgrupo: 'control', requiere: 'firma:visar', enMenu: true, pagina: VisadoFirmas },
  { ruta: 'ajustes-empresa', etiqueta: S.ajustesEmpresa, icono: 'empresa', grupo: 'gestion', subgrupo: 'administracion', requiere: 'empresa:configurar', enMenu: true, pagina: AjustesEmpresa },
  { ruta: 'tarjetas-kiosco', etiqueta: S.tarjetasKiosco, icono: 'qr', grupo: 'gestion', subgrupo: 'organizacion', requiere: 'empresa:configurar', enMenu: false, pagina: TarjetasKiosco },
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

export interface ApartadoDelMenu {
  subgrupo: Subgrupo;
  secciones: Seccion[];
}

/**
 * El menú, por grupos y, dentro de cada uno, por subgrupos, en el orden de
 * `SUBGRUPOS` y, dentro de cada subgrupo, en el del catálogo. Los grupos y
 * subgrupos vacíos no salen. Lo que no pliega (un subgrupo de una sola
 * entrada) lo decide quien lo pinta.
 */
export function menuAgrupado(menu: readonly Seccion[]): { grupo: Grupo; apartados: ApartadoDelMenu[] }[] {
  return (Object.keys(SUBGRUPOS) as Grupo[])
    .map((grupo) => ({
      grupo,
      apartados: SUBGRUPOS[grupo]
        .map((subgrupo) => ({ subgrupo, secciones: menu.filter((s) => s.grupo === grupo && s.subgrupo === subgrupo) }))
        .filter((a) => a.secciones.length > 0),
    }))
    .filter((g) => g.apartados.length > 0);
}

/**
 * La sección de una ruta de la barra de direcciones (`/correcciones/pendientes`),
 * para saber qué subgrupo desplegar. Si la ruta va más allá de la sección
 * (`/cuadrantes/3`), la de la ruta más larga que encaje.
 */
export function seccionDeRuta(pathname: string): Seccion | undefined {
  const ruta = pathname.replace(/^\/+|\/+$/g, '');
  return SECCIONES.filter((s) => ruta === s.ruta || ruta.startsWith(`${s.ruta}/`)).sort(
    (a, b) => b.ruta.length - a.ruta.length,
  )[0];
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
