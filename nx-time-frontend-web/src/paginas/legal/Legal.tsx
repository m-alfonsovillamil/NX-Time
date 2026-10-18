/**
 * Las páginas legales: el índice en `/legal` y cada texto en `/legal/<ruta>`.
 *
 * No piden nada al servidor: el aviso legal tiene que poder leerse siempre,
 * también con la API dormida (ver scripts/titular.mjs).
 */

import { useEffect } from 'react';
import { Link, NavLink, useParams } from 'react-router';

import { PieLegal } from '../../componentes/PieLegal';
import { TITULAR, VERSION_CONDICIONES } from '../../legal/titular';
import { DOCUMENTOS, documento } from './documentos';

/** «17 de octubre de 2026», de la versión de los textos. */
export function fechaDeLaVersion(version: string = VERSION_CONDICIONES): string {
  return new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${version}T00:00:00Z`),
  );
}

export function Legal() {
  const { ruta } = useParams();
  const actual = documento(ruta);
  const titulo = actual?.titulo ?? 'Información legal';

  useEffect(() => {
    document.title = `${titulo} · ${TITULAR.producto}`;
  }, [titulo]);

  return (
    <div className="nx-legal">
      <header className="nx-legal__cabecera">
        <Link to="/" className="nx-legal__marca">
          <img src="/iconos/icono.svg" alt="" width={32} height={32} />
          {TITULAR.producto}
        </Link>
      </header>

      <div className="nx-legal__cuerpo">
        <nav className="nx-legal__indice" aria-label="Textos legales">
          <ul>
            {DOCUMENTOS.map((doc) => (
              <li key={doc.ruta}>
                <NavLink to={`/legal/${doc.ruta}`}>{doc.titulo}</NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <main className="nx-legal__texto">
          {ruta !== undefined && actual === undefined ? (
            <>
              <h1>No existe ese texto</h1>
              <p>
                <Link to="/legal">Ver todos los textos legales</Link>
              </p>
            </>
          ) : actual === undefined ? (
            <>
              <h1>Información legal</h1>
              <p>
                {TITULAR.producto} es un servicio de registro de jornada para empresas. Estos son los textos que lo
                regulan. Versión del {fechaDeLaVersion()}.
              </p>
              <ul className="nx-legal__lista-de-textos">
                {DOCUMENTOS.map((doc) => (
                  <li key={doc.ruta}>
                    <Link to={`/legal/${doc.ruta}`}>{doc.titulo}</Link>
                    <span className="nx-sutil">{doc.resumen}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <article>
              <h1>{actual.titulo}</h1>
              <p className="nx-sutil">Versión del {fechaDeLaVersion()}.</p>
              <actual.Texto />
            </article>
          )}
        </main>
      </div>

      <PieLegal />
    </div>
  );
}
