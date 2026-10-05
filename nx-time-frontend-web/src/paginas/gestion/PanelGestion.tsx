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

import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import type { components } from '../../api/schema';
import { useSesion } from '../../api/useSesion';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Icono, type NombreIcono } from '../../componentes/Icono';
import { gestion } from '../../i18n/es/gestion';
import { menuPara, SECCIONES } from '../../navegacion/secciones';
import { CLAVE_PENDIENTES } from './claves';

const P = gestion.panel;

type Pendientes = components['schemas']['PendingWorkResponse'];

interface Bandeja {
  ruta: string;
  texto: string;
  cuantos: (p: Pendientes) => number;
}

const BANDEJAS: readonly Bandeja[] = [
  { ruta: 'ausencias-equipo/pendientes', texto: P.ausencias, cuantos: (p) => p.ausencias ?? 0 },
  { ruta: 'correcciones/pendientes', texto: P.correcciones, cuantos: (p) => p.correcciones ?? 0 },
  { ruta: 'horas-extra', texto: P.horasExtra, cuantos: (p) => p.horasExtra ?? 0 },
  { ruta: 'borrados', texto: P.borrados, cuantos: (p) => p.borrados ?? 0 },
];

/** El icono de la sección a la que lleva cada bandeja: el mismo que en el menú. */
const iconoDe = (ruta: string): NombreIcono => SECCIONES.find((s) => s.ruta === ruta)?.icono ?? 'bandeja';

/** Las bandejas que esta persona puede abrir y que la web ya tiene. */
export function bandejasPara(authorities: readonly string[]): Bandeja[] {
  const abiertas = new Set(menuPara(authorities).map((s) => s.ruta));
  // «Horas extra» también es de lo mío y está en el menú de todos: la bandeja
  // del equipo pide su propio permiso.
  return BANDEJAS.filter((b) => abiertas.has(b.ruta) && (b.ruta !== 'horas-extra' || authorities.includes('horasextra:revisar')));
}

export function PanelGestion() {
  const { sesion } = useSesion();
  const authorities = sesion?.authorities ?? [];
  const pendientes = useQuery({
    queryKey: CLAVE_PENDIENTES,
    queryFn: () => pedir(cliente.GET('/api/v1/dashboard/pendientes', {})),
  });

  const bandejas = bandejasPara(authorities);
  const accesos = menuPara(authorities).filter((s) => s.grupo === 'gestion' && s.ruta !== 'gestion');

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <h1>{P.titulo}</h1>
      </header>

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
                      etiqueta={b.texto}
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
