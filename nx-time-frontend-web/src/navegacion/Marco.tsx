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
 */

import { Suspense, useId, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';

import { salir } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Dialogo } from '../componentes/Dialogo';
import { EsqueletoDePagina } from '../componentes/Estados';
import { Icono } from '../componentes/Icono';
import { Notificaciones } from '../componentes/Notificaciones';
import { T } from '../i18n/es';
import { Campana } from './Campana';
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

function Enlace({ seccion, alPulsar }: { seccion: Seccion; alPulsar?: () => void }) {
  return (
    <NavLink to={`/${seccion.ruta}`} className="nx-enlace-menu" onClick={alPulsar}>
      <Icono nombre={seccion.icono} />
      <span>{seccion.etiqueta}</span>
    </NavLink>
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
function MenuAgrupado({ secciones, alPulsar }: { secciones: readonly Seccion[]; alPulsar?: () => void }) {
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
      <Enlace seccion={s} {...(alPulsar !== undefined ? { alPulsar } : {})} />
    </li>
  );

  return (
    <>
      {menuAgrupado(secciones).map(({ grupo, apartados }) => (
        <div key={grupo} className="nx-menu-grupo">
          <h2 className="nx-menu-grupo__titulo">{N.grupos[grupo]}</h2>
          <ul>
            {apartados.map(({ subgrupo, secciones: delApartado }) => {
              const [unica] = delApartado;
              if (delApartado.length === 1 && unica !== undefined) return enlace(unica);
              const id = `${prefijo}-${subgrupo}`;
              const estaVez = abierto(subgrupo);
              return (
                <li key={subgrupo} className="nx-subgrupo">
                  <button
                    type="button"
                    className="nx-enlace-menu nx-subgrupo__boton"
                    aria-expanded={estaVez}
                    aria-controls={id}
                    onClick={() => cambiar(subgrupo)}
                  >
                    <Icono nombre={ICONOS_DE_SUBGRUPO[subgrupo]} />
                    <span>{N.subgrupos[subgrupo]}</span>
                    <Icono nombre="desplegar" />
                  </button>
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
        <Icono nombre="persona" />
        <span className="nx-menu-usuario__nombre">{nombre}</span>
      </summary>
      <div className="nx-menu-usuario__opciones">
        {cuelgan.map((s) => (
          <Enlace key={s.ruta} seccion={s} alPulsar={() => setAbierto(false)} />
        ))}
        <button type="button" className="nx-enlace-menu" onClick={salir}>
          <Icono nombre="salir" />
          <span>{N.usuario.salir}</span>
        </button>
      </div>
    </details>
  );
}

export function Marco() {
  const { sesion } = useSesion();
  const [todasAbiertas, setTodasAbiertas] = useState(false);
  const menu = menuPara(sesion?.authorities ?? []);

  const { enBarra, conMas } = barraInferior(menu);
  // El acento de la página (teal o índigo) sale de su grupo, como en la app.
  const zona = seccionDeRuta(useLocation().pathname)?.grupo;

  return (
    <div className="nx-marco">
      <a href="#contenido" className="nx-saltar">
        {N.saltarAlContenido}
      </a>

      <nav className="nx-lateral" aria-label={N.menuPrincipal}>
        <p className="nx-lateral__marca">{T.app.nombre}</p>
        <MenuAgrupado secciones={menu} />
      </nav>

      <header className="nx-barra-superior">
        <p className="nx-barra-superior__marca">{T.app.nombre}</p>
        <div className="nx-barra-superior__acciones">
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
                <Enlace seccion={s} />
              </li>
            ))}
            {conMas && (
              <li>
                <button type="button" className="nx-enlace-menu" onClick={() => setTodasAbiertas(true)}>
                  <Icono nombre="mas" />
                  <span>{N.mas}</span>
                </button>
              </li>
            )}
          </ul>
        </nav>
      )}

      <Dialogo abierto={todasAbiertas} titulo={N.todasLasSecciones} alCerrar={() => setTodasAbiertas(false)}>
        <nav aria-label={N.todasLasSecciones} className="nx-menu-completo">
          <MenuAgrupado secciones={menu} alPulsar={() => setTodasAbiertas(false)} />
        </nav>
      </Dialogo>

      <Notificaciones />
    </div>
  );
}
