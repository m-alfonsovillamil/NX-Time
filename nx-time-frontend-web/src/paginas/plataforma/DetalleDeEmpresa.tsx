/**
 * Una empresa vista desde el panel de plataforma (ADR 040): sus cifras de uso y
 * a quién escribir.
 *
 * Cada vez que se abre, el servidor apunta quién ha mirado a quién: aquí salen
 * los correos de los administradores de una empresa que no es la de quien mira.
 */

import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Insignia, Tarjeta } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { EstadoDeConsulta, EsqueletoDePagina, Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { plataforma } from '../../i18n/es/plataforma';
import { fechaHoraCorta } from '../../util/fechas';
import { CLAVE_PLATAFORMA, diaDe } from './Plataforma';

const D = plataforma.detalle;

type Detalle = components['schemas']['PlatformCompanyDetailResponse'];
type Plantilla = NonNullable<Detalle['plantilla']>[number];

/** `3738` → `3,7 kB`. Con coma, como el resto de las cifras de la aplicación. */
export function tamano(bytes: number): string {
  if (bytes < 1000) return `${bytes} B`;
  const unidades = ['kB', 'MB', 'GB', 'TB'];
  let valor = bytes;
  let unidad = -1;
  do {
    valor /= 1000;
    unidad++;
  } while (valor >= 1000 && unidad < unidades.length - 1);
  return `${valor.toLocaleString('es-ES', { maximumFractionDigits: 1 })} ${unidades[unidad]}`;
}

const COLUMNAS_DE_PLANTILLA: readonly Columna<Plantilla>[] = [
  { clave: 'rol', cabecera: D.rol, celda: (p) => D.roles[p.rol ?? ''] ?? p.rol ?? '' },
  { clave: 'activos', cabecera: D.activos, celda: (p) => p.activos ?? 0, numerica: true },
  { clave: 'deBaja', cabecera: D.deBaja, celda: (p) => p.deBaja ?? 0, numerica: true },
];

function Volver() {
  return (
    <Link className="nx-boton nx-boton--texto nx-boton--enlace" to="/plataforma">
      {D.volver}
    </Link>
  );
}

export function DetalleDeEmpresa({ id }: { id: number }) {
  const consulta = useQuery({
    queryKey: [...CLAVE_PLATAFORMA, 'empresa', id],
    queryFn: () => pedir(cliente.GET('/api/v1/plataforma/empresas/{id}', { params: { path: { id } } })),
  });

  return (
    <div className="nx-pagina">
      <EstadoDeConsulta consulta={consulta} cargando={<EsqueletoDePagina />}>
        {(e) => <Ficha empresa={e} />}
      </EstadoDeConsulta>
      {/* Si no existe (o falla), que haya por dónde volver. */}
      {consulta.isError && (
        <div>
          <Volver />
        </div>
      )}
    </div>
  );
}

function Ficha({ empresa: e }: { empresa: Detalle }) {
  const administradores = e.administradores ?? [];
  const fichajes = e.fichajes ?? {};
  const sesiones = e.sesiones ?? {};

  return (
    <>
      <CabeceraDePagina titulo={e.nombre ?? ''} descripcion={D.explicacion} icono="empresa" acciones={<Volver />} />

      <div className="nx-composicion nx-composicion--mitades nx-composicion--arriba">
        <div className="nx-columna">
          <Tarjeta titulo={D.datos} icono="empresa">
            <dl className="nx-datos">
              <dt>{D.alta}</dt>
              <dd>{diaDe(e.creadaEn, D.sinFecha)}</dd>
              <dt>{D.zona}</dt>
              <dd>{e.zonaHoraria}</dd>
            </dl>
          </Tarjeta>

          <Tarjeta titulo={D.contacto} icono="persona" descripcion={D.contactoDescripcion}>
            {administradores.length === 0 ? (
              <Vacio icono="persona" titulo={D.sinContacto} />
            ) : (
              <ul className="nx-lista-simple">
                {administradores.map((a) => (
                  <li key={a.email}>
                    {a.nombre}{' '}
                    <a className="nx-enlace" href={`mailto:${a.email ?? ''}`}>
                      {a.email}
                    </a>
                    {a.correoSinConfirmar === true && (
                      <>
                        {' '}
                        <Insignia tono="aviso">{D.sinConfirmar}</Insignia>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Tarjeta>

          <Tarjeta titulo={D.plantilla} icono="grupo">
            <Tabla
              titulo={D.plantillaTabla}
              columnas={COLUMNAS_DE_PLANTILLA}
              filas={e.plantilla ?? []}
              claveDeFila={(p) => p.rol ?? ''}
            />
          </Tarjeta>
        </div>

        <div className="nx-columna">
          <Tarjeta titulo={D.actividad} icono="reloj" className="nx-expediente">
            <Cifras etiqueta={D.actividad}>
              <Cifra icono="hecho" etiqueta={D.fichajes7} valor={fichajes.en7Dias ?? 0} />
              <Cifra icono="calendario" etiqueta={D.fichajes30} valor={fichajes.en30Dias ?? 0} />
              <Cifra icono="grupo" etiqueta={D.personas} valor={fichajes.personasEn30Dias ?? 0} />
              <Cifra icono="historial" etiqueta={D.fichajesTotal} valor={fichajes.total ?? 0} />
            </Cifras>
            <dl className="nx-datos">
              <dt>{D.ultimoFichaje}</dt>
              <dd>{fechaHoraCorta(fichajes.ultimo) || D.nunca}</dd>
              <dt>{D.ultimaSesion}</dt>
              <dd>{fechaHoraCorta(sesiones.ultima) || D.nunca}</dd>
              <dt>{D.sesiones}</dt>
              <dd>
                {D.web}: {sesiones.webEn30Dias ?? 0} · {D.app}: {sesiones.appEn30Dias ?? 0}
              </dd>
            </dl>
          </Tarjeta>

          <Tarjeta titulo={D.uso} icono="organizar">
            <dl className="nx-datos">
              <dt>{D.departamentos}</dt>
              <dd>{e.departamentos ?? 0}</dd>
              <dt>{D.proyectos}</dt>
              <dd>{e.proyectos ?? 0}</dd>
              <dt>{D.kioscos}</dt>
              <dd>{e.kioscos ?? 0}</dd>
              <dt>{D.push}</dt>
              <dd>{e.dispositivosPush ?? 0}</dd>
              <dt>{D.google}</dt>
              <dd>{e.cuentasConGoogle ?? 0}</dd>
              <dt>{D.microsoft}</dt>
              <dd>{e.cuentasConMicrosoft ?? 0}</dd>
              <dt>{D.adjuntos}</dt>
              <dd>{tamano(e.bytesDeAdjuntos ?? 0)}</dd>
              <dt>{D.borrados}</dt>
              <dd>{e.borradosPendientes ?? 0}</dd>
              <dt>{D.auditoria}</dt>
              <dd>{e.movimientosDeAuditoria ?? 0}</dd>
            </dl>
          </Tarjeta>
        </div>
      </div>
    </>
  );
}
