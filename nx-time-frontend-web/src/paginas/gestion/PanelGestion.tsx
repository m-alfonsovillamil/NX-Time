/**
 * El panel de gestión: lo que espera una decisión y el acceso a lo demás. Es
 * la `PanelGestionScreen` de la app.
 *
 * **Cada contador es exactamente lo que se ve al abrir su bandeja** (lo
 * garantiza `/dashboard/pendientes`), no un total de la empresa: un «3» que
 * al entrar son dos es peor que no poner nada. Sin permiso para una bandeja,
 * su tarjeta no sale; y tampoco la de una bandeja que la web aún no tenga,
 * porque sería un número que no lleva a ningún sitio.
 *
 * Los accesos salen del mismo catálogo que el menú (`secciones.ts`), así que
 * no puede ofrecer algo que el menú no ofrezca.
 */

import { Link } from 'react-router';

import { useSesion } from '../../api/useSesion';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Icono, type NombreIcono } from '../../componentes/Icono';
import { gestion } from '../../i18n/es/gestion';
import { bandejasPara, usePendientes } from '../../navegacion/pendientes';
import { menuPara, SECCIONES } from '../../navegacion/secciones';

const P = gestion.panel;

/** Cómo se llama cada bandeja aquí. Cuáles hay y cuánto tienen lo sabe `navegacion/pendientes`. */
const TEXTOS: Readonly<Record<string, string>> = {
  'ausencias-equipo/pendientes': P.ausencias,
  'correcciones/pendientes': P.correcciones,
  'horas-extra': P.horasExtra,
  borrados: P.borrados,
};

/** El icono de la sección a la que lleva cada bandeja: el mismo que en el menú. */
const iconoDe = (ruta: string): NombreIcono => SECCIONES.find((s) => s.ruta === ruta)?.icono ?? 'bandeja';

export function PanelGestion() {
  const { sesion } = useSesion();
  const authorities = sesion?.authorities ?? [];
  const pendientes = usePendientes();

  const bandejas = bandejasPara(authorities);
  const accesos = menuPara(authorities).filter((s) => s.grupo === 'gestion' && s.ruta !== 'gestion');

  return (
    <div className="nx-pagina">
      <CabeceraDePagina titulo={P.titulo} />

      <section className="nx-tarjeta" aria-labelledby="panel-pendiente">
        <h2 id="panel-pendiente">{P.pendiente}</h2>
        <EstadoDeConsulta consulta={pendientes} cargando={<Esqueleto forma="recuadros" lineas={bandejas.length} />}>
          {(p) =>
            bandejas.every((b) => b.cuantos(p) === 0) ? (
              <Vacio icono="hecho" titulo={P.nadaPendiente} />
            ) : (
              <Cifras>
                {bandejas.map((b) => {
                  const n = b.cuantos(p);
                  return (
                    <Cifra
                      key={b.ruta}
                      icono={iconoDe(b.ruta)}
                      etiqueta={TEXTOS[b.ruta] ?? b.ruta}
                      valor={n}
                      tono={n > 0 ? 'destacada' : 'normal'}
                      a={`/${b.ruta}`}
                    />
                  );
                })}
              </Cifras>
            )
          }
        </EstadoDeConsulta>
      </section>

      <section className="nx-tarjeta" aria-labelledby="panel-accesos">
        <h2 id="panel-accesos">{P.accesos}</h2>
        <ul className="nx-accesos">
          {accesos.map((s) => (
            <li key={s.ruta}>
              <Link className="nx-acceso" to={`/${s.ruta}`}>
                <Icono nombre={s.icono} />
                <span>{s.etiqueta}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
