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
 * authorities, no porque aquí se mire el rol.
 */

import { Suspense, useState } from 'react';
import { NavLink, Outlet } from 'react-router';

import { salir } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Dialogo } from '../componentes/Dialogo';
import { Esqueleto } from '../componentes/Estados';
import { Icono } from '../componentes/Icono';
import { Notificaciones } from '../componentes/Notificaciones';
import { T } from '../i18n/es';
import { Campana } from './Campana';
import { barraInferior, menuPara, type Grupo, type Seccion } from './secciones';

const N = T.navegacion;
const GRUPOS: readonly Grupo[] = ['personal', 'gestion'];

function Enlace({ seccion, alPulsar }: { seccion: Seccion; alPulsar?: () => void }) {
  return (
    <NavLink to={`/${seccion.ruta}`} className="nx-enlace-menu" onClick={alPulsar}>
      <Icono nombre={seccion.icono} />
      <span>{seccion.etiqueta}</span>
    </NavLink>
  );
}

function MenuAgrupado({ secciones, alPulsar }: { secciones: readonly Seccion[]; alPulsar?: () => void }) {
  return (
    <>
      {GRUPOS.map((grupo) => {
        const delGrupo = secciones.filter((s) => s.grupo === grupo);
        if (delGrupo.length === 0) return null;
        return (
          <div key={grupo} className="nx-menu-grupo">
            <h2 className="nx-menu-grupo__titulo">{N.grupos[grupo]}</h2>
            <ul>
              {delGrupo.map((s) => (
                <li key={s.ruta}>
                  <Enlace seccion={s} {...(alPulsar !== undefined ? { alPulsar } : {})} />
                </li>
              ))}
            </ul>
          </div>
        );
      })}
    </>
  );
}

/**
 * El nombre y lo que cuelga de él: perfil, ajustes (cuando lleguen en W3) y salir.
 *
 * `<details>` y no un menú desplegable hecho a mano: abre y cierra con el
 * teclado y anuncia su estado sin una línea de JavaScript.
 */
function MenuDeUsuario({ nombre }: { nombre: string }) {
  return (
    <details className="nx-menu-usuario">
      <summary aria-label={N.usuario.menu(nombre)}>
        <Icono nombre="persona" />
        <span className="nx-menu-usuario__nombre">{nombre}</span>
      </summary>
      <div className="nx-menu-usuario__opciones">
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

      <main id="contenido" className="nx-contenido" tabIndex={-1}>
        {/* Cada página es un trozo de JS aparte: mientras llega, su esqueleto. */}
        <Suspense fallback={<Esqueleto />}>
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
