/**
 * El marco de todas las páginas con sesión: menú, cabecera y el hueco del contenido.
 *
 * **Barra lateral en escritorio y barra inferior en el móvil**, como la app.
 * Las dos están en el HTML y el CSS enseña una u otra (`display: none` las
 * saca también del árbol de accesibilidad, así que un lector de pantalla no
 * oye el menú dos veces). Qué cabe en la barra inferior lo decide
 * `barraInferior`.
 *
 * El menú se construye con las authorities de la sesión (ver `secciones.ts`):
 * un EMPLEADO no ve el grupo de gestión porque no tiene ninguna de sus
 * authorities, no porque aquí se mire el rol. Dentro de cada grupo va por
 * apartados plegables (`MenuAgrupado`), en el lateral y en «Más» del móvil; la
 * barra inferior no cambia.
 *
 * Desde el 5/10/2026 (ADR 035) el menú **se reconoce de un vistazo**: cada
 * apartado lleva su icono en un recuadro del color de su zona (teal en «Lo
 * mío», índigo en «Gestión», como la app), cada entrada el suyo, la que espera
 * una decisión enseña cuántas (`pendientes.ts`), y el apartado de la página
 * abierta queda marcado aunque esté plegado. Arriba, el estado de la jornada
 * (`ChipDeJornada`).
 */

import { Suspense, useEffect, useId, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';

import { salir } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Dialogo } from '../componentes/Dialogo';
import { EsqueletoDePagina } from '../componentes/Estados';
import { Icono } from '../componentes/Icono';
import { Iniciales } from '../componentes/Iniciales';
import { Notificaciones } from '../componentes/Notificaciones';
import { T } from '../i18n/es';
import { Campana } from './Campana';
import { ChipDeJornada } from './ChipDeJornada';
import { usePendientesPorRuta } from './pendientes';
import {
  barraInferior,
  disponibles,
  ICONOS_DE_SUBGRUPO,
  menuAgrupado,
  menuPara,
  seccionDeRuta,
  type Seccion,
  type Subgrupo,
} from './secciones';

const N = T.navegacion;

/**
 * El número de pendientes de una entrada o de un apartado.
 *
 * A la vista es una pastilla dentro del enlace; para un lector de pantalla es
 * la **descripción** del enlace (`aria-describedby`), no parte de su nombre:
 * se oye «Ausencias del equipo, enlace, 3 pendientes», y el enlace se sigue
 * llamando como la sección, que es por lo que se busca.
 *
 * Por eso son dos piezas: la pastilla (`aria-hidden`) va dentro, y el texto
 * que se lee va **fuera**, al lado. Dentro pasaría a formar parte del nombre.
 */
function Contador({ cuantos }: { cuantos: number }) {
  return (
    <span className="nx-menu-contador" aria-hidden="true">
      {cuantos > 99 ? '99+' : cuantos}
    </span>
  );
}

function TextoDeContador({ id, cuantos }: { id: string; cuantos: number }) {
  return (
    <span id={id} className="nx-solo-lector">
      {N.pendientes(cuantos)}
    </span>
  );
}

function Enlace({ seccion, alPulsar, pendientes = 0 }: { seccion: Seccion; alPulsar?: () => void; pendientes?: number }) {
  const idContador = useId();
  // Pedir ya el JS de la página: para cuando se pulse, estará (ver `perezosa`).
  const precargar = () => seccion.pagina?.precargar();
  return (
    <>
      <NavLink
        to={`/${seccion.ruta}`}
        className="nx-enlace-menu"
        onClick={alPulsar}
        onPointerEnter={precargar}
        onFocus={precargar}
        aria-describedby={pendientes > 0 ? idContador : undefined}
      >
        <span className="nx-enlace-menu__icono">
          <Icono nombre={seccion.icono} />
        </span>
        <span className="nx-enlace-menu__texto">{seccion.etiqueta}</span>
        {pendientes > 0 && <Contador cuantos={pendientes} />}
      </NavLink>
      {pendientes > 0 && <TextoDeContador id={idContador} cuantos={pendientes} />}
    </>
  );
}

/*
 * Qué apartados del menú están abiertos. Es una preferencia de este navegador,
 * como el tema: todo acceso va en try/catch, y sin almacenamiento el menú
 * funciona igual, solo que no se acuerda.
 */
const CLAVE_ABIERTOS = 'nx-menu-abiertos';
type Abiertos = Partial<Record<Subgrupo, boolean>>;

function abiertosGuardados(): Abiertos {
  try {
    const valor: unknown = JSON.parse(globalThis.localStorage?.getItem(CLAVE_ABIERTOS) ?? '{}');
    return typeof valor === 'object' && valor !== null ? (valor as Abiertos) : {};
  } catch {
    return {};
  }
}

function guardarAbiertos(abiertos: Abiertos): void {
  try {
    globalThis.localStorage?.setItem(CLAVE_ABIERTOS, JSON.stringify(abiertos));
  } catch {
    // Sin almacenamiento, el menú no se acuerda: nada más.
  }
}

/**
 * Hasta cuántas entradas el menú sale entero, con todo abierto, mientras la
 * persona no cierre nada. El de un EMPLEADO (once) cabe de un vistazo; el de
 * un GESTOR o un ADMIN no, y sale con solo el apartado de la página abierta.
 */
const MENU_CORTO = 12;

/**
 * El menú por grupos y apartados plegables. Un apartado con una sola entrada
 * no se pliega: es un enlace más. El de la página abierta se abre solo al
 * llegar a ella (también desde un aviso o un enlace), y lo que la persona abre
 * o cierra se recuerda. Cada apartado es un botón con `aria-expanded`, y lo
 * cerrado va con `hidden`: fuera de la vista, del tabulador y del lector.
 */
function MenuAgrupado({
  secciones,
  alPulsar,
  pendientes,
}: {
  secciones: readonly Seccion[];
  alPulsar?: () => void;
  /** Cuánto espera una decisión en cada ruta (solo las que tienen algo). */
  pendientes: ReadonlyMap<string, number>;
}) {
  const prefijo = useId();
  const actual = seccionDeRuta(useLocation().pathname)?.subgrupo;
  const [abiertos, setAbiertos] = useState<Abiertos>(() =>
    actual !== undefined ? { ...abiertosGuardados(), [actual]: true } : abiertosGuardados(),
  );
  const porDefecto = secciones.length <= MENU_CORTO;
  const abierto = (subgrupo: Subgrupo) => abiertos[subgrupo] ?? porDefecto;

  // Al cambiar de página, se abre su apartado. Durante el render y no en un
  // efecto, para que no se pinte un instante cerrado.
  const [vista, setVista] = useState(actual);
  if (actual !== vista) {
    setVista(actual);
    if (actual !== undefined && !abierto(actual)) setAbiertos({ ...abiertos, [actual]: true });
  }

  function cambiar(subgrupo: Subgrupo) {
    const nuevos = { ...abiertos, [subgrupo]: !abierto(subgrupo) };
    setAbiertos(nuevos);
    guardarAbiertos(nuevos);
  }

  const enlace = (s: Seccion) => (
    <li key={s.ruta}>
      <Enlace seccion={s} pendientes={pendientes.get(s.ruta) ?? 0} {...(alPulsar !== undefined ? { alPulsar } : {})} />
    </li>
  );

  return (
    <>
      {menuAgrupado(secciones).map(({ grupo, apartados }) => (
        <div key={grupo} className="nx-menu-grupo" data-zona={grupo}>
          <h2 className="nx-menu-grupo__titulo">{N.grupos[grupo]}</h2>
          <ul>
            {apartados.map(({ subgrupo, secciones: delApartado }) => {
              const [unica] = delApartado;
              if (delApartado.length === 1 && unica !== undefined) return enlace(unica);
              const id = `${prefijo}-${subgrupo}`;
              const estaVez = abierto(subgrupo);
              // Plegado, el apartado dice cuánto hay dentro: si no, un «3» que
              // espera quedaría escondido hasta abrirlo.
              const dentro = delApartado.reduce((suma, s) => suma + (pendientes.get(s.ruta) ?? 0), 0);
              const conContador = !estaVez && dentro > 0;
              return (
                <li key={subgrupo} className="nx-subgrupo">
                  <button
                    type="button"
                    className="nx-enlace-menu nx-subgrupo__boton"
                    aria-expanded={estaVez}
                    aria-controls={id}
                    aria-describedby={conContador ? `${id}-pendientes` : undefined}
                    // El apartado de la página abierta, para marcarlo también plegado.
                    data-actual={subgrupo === actual ? '' : undefined}
                    onClick={() => cambiar(subgrupo)}
                  >
                    <span className="nx-enlace-menu__icono">
                      <Icono nombre={ICONOS_DE_SUBGRUPO[subgrupo]} />
                    </span>
                    <span className="nx-enlace-menu__texto">{N.subgrupos[subgrupo]}</span>
                    {conContador && <Contador cuantos={dentro} />}
                    <Icono nombre="desplegar" />
                  </button>
                  {conContador && <TextoDeContador id={`${id}-pendientes`} cuantos={dentro} />}
                  <ul id={id} hidden={!estaVez}>
                    {delApartado.map(enlace)}
                  </ul>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </>
  );
}

/**
 * El nombre y lo que cuelga de él: mi perfil, ajustes y salir.
 *
 * `<details>` y no un menú desplegable hecho a mano: abre y cierra con el
 * teclado y anuncia su estado sin una línea de JavaScript. Al elegir algo se
 * cierra, que un `<details>` no lo hace solo.
 */
function MenuDeUsuario({ nombre }: { nombre: string }) {
  const [abierto, setAbierto] = useState(false);
  const cuelgan = disponibles().filter((s) => s.ruta === 'perfil' || s.ruta === 'ajustes');
  return (
    <details className="nx-menu-usuario" open={abierto} onToggle={(e) => setAbierto(e.currentTarget.open)}>
      <summary aria-label={N.usuario.menu(nombre)}>
        {nombre !== '' ? <Iniciales nombre={nombre} tamano="s" /> : <Icono nombre="persona" />}
        <span className="nx-menu-usuario__nombre">{nombre}</span>
      </summary>
      <div className="nx-menu-usuario__opciones">
        {cuelgan.map((s) => (
          <Enlace key={s.ruta} seccion={s} alPulsar={() => setAbierto(false)} />
        ))}
        <button type="button" className="nx-enlace-menu" onClick={salir}>
          <span className="nx-enlace-menu__icono">
            <Icono nombre="salir" />
          </span>
          <span className="nx-enlace-menu__texto">{N.usuario.salir}</span>
        </button>
      </div>
    </details>
  );
}

/**
 * Pide en un rato libre el JS de las secciones de la barra inferior.
 *
 * En escritorio, pasar el ratón por el menú ya adelanta la descarga (ver
 * `Enlace`). **En el móvil no hay ratón**: el dedo llega y pulsa a la vez, y
 * en una red lenta cada cambio de pestaña esperaba a su trozo de JS (unos
 * 330 ms medidos con `scripts/medir.mjs`). Las de la barra inferior son las
 * cuatro que más se usan y pesan poco: se piden solas cuando el navegador no
 * tiene otra cosa que hacer, una vez, tras la primera pantalla.
 *
 * Con «ahorro de datos» activado no se pide nada por adelantado.
 */
function usePrecargaEnReposo(secciones: readonly Seccion[]) {
  const rutas = secciones.map((s) => s.ruta).join(' ');
  useEffect(() => {
    const conexion = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection;
    if (conexion?.saveData === true) return;
    const precargar = () => {
      for (const seccion of secciones) seccion.pagina?.precargar();
    };
    // Safari no tiene `requestIdleCallback`: un par de segundos después de pintar hace el mismo papel.
    if (typeof globalThis.requestIdleCallback === 'function') {
      const id = globalThis.requestIdleCallback(precargar, { timeout: 4000 });
      // Se mira otra vez al limpiar: que exista una no garantiza la otra, y un
      // fallo aquí sería un error al desmontar el marco entero.
      return () => {
        if (typeof globalThis.cancelIdleCallback === 'function') globalThis.cancelIdleCallback(id);
      };
    }
    const id = setTimeout(precargar, 2000);
    return () => clearTimeout(id);
    // Depende de las rutas y no de `secciones`, que es una lista nueva en cada
    // render: las secciones salen del catálogo, y con las mismas rutas son las mismas.
  }, [rutas]);
}

export function Marco() {
  const { sesion } = useSesion();
  const [todasAbiertas, setTodasAbiertas] = useState(false);
  const menu = menuPara(sesion?.authorities ?? []);

  const { enBarra, conMas } = barraInferior(menu);
  // El acento de la página (teal o índigo) sale de su grupo, como en la app.
  const zona = seccionDeRuta(useLocation().pathname)?.grupo;
  const pendientes = usePendientesPorRuta();
  usePrecargaEnReposo(enBarra);

  return (
    <div className="nx-marco">
      <a href="#contenido" className="nx-saltar">
        {N.saltarAlContenido}
      </a>

      <nav className="nx-lateral" aria-label={N.menuPrincipal}>
        <p className="nx-lateral__marca">
          <img src="/iconos/icono.svg" alt="" width={32} height={32} />
          {T.app.nombre}
        </p>
        <MenuAgrupado secciones={menu} pendientes={pendientes} />
      </nav>

      <header className="nx-barra-superior">
        <p className="nx-barra-superior__marca">{T.app.nombre}</p>
        <div className="nx-barra-superior__acciones">
          <ChipDeJornada />
          <Campana />
          <MenuDeUsuario nombre={sesion?.nombre ?? ''} />
        </div>
      </header>

      <main id="contenido" className="nx-contenido" tabIndex={-1} data-zona={zona}>
        {/* Cada página es un trozo de JS aparte: mientras llega, su esqueleto. */}
        <Suspense fallback={<EsqueletoDePagina />}>
          <Outlet />
        </Suspense>
      </main>

      {enBarra.length > 0 && (
        <nav className="nx-inferior" aria-label={N.menuPrincipal}>
          <ul>
            {enBarra.map((s) => (
              <li key={s.ruta}>
                <Enlace seccion={s} pendientes={pendientes.get(s.ruta) ?? 0} />
              </li>
            ))}
            {conMas && (
              <li>
                <button type="button" className="nx-enlace-menu" onClick={() => setTodasAbiertas(true)}>
                  <span className="nx-enlace-menu__icono">
                    <Icono nombre="mas" />
                  </span>
                  <span className="nx-enlace-menu__texto">{N.mas}</span>
                </button>
              </li>
            )}
          </ul>
        </nav>
      )}

      <Dialogo abierto={todasAbiertas} titulo={N.todasLasSecciones} alCerrar={() => setTodasAbiertas(false)}>
        <nav aria-label={N.todasLasSecciones} className="nx-menu-completo">
          <MenuAgrupado secciones={menu} pendientes={pendientes} alPulsar={() => setTodasAbiertas(false)} />
        </nav>
      </Dialogo>

      <Notificaciones />
    </div>
  );
}
