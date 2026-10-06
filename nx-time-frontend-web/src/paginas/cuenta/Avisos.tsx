/**
 * Todos mis avisos, por páginas: la lista completa de lo que la campana resume.
 *
 * Abrir un aviso lo marca leído y, si su destino tiene página, lleva a ella: el
 * destino ES la URL (ADR 029, decisión 4). Uno cuyo destino aún no existe en
 * la web se marca leído y se queda aquí, que es lo mismo que hace la app con
 * un destino que su versión no conoce.
 *
 * A lo ancho (5/10/2026): cada aviso lleva el icono de la sección a la que
 * lleva (el mismo que en el menú), y al lado va cuántos quedan sin leer y el
 * camino a los ajustes de notificaciones.
 */

import { Link, useNavigate } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useListaPaginada, useMutacion } from '../../api/consultas';
import { Aviso, Boton, Tarjeta } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { ErrorConReintento, Esqueleto, FinDeLista, Vacio } from '../../componentes/Estados';
import { Icono, type NombreIcono } from '../../componentes/Icono';
import { cuenta } from '../../i18n/es/cuenta';
import { CLAVES_AVISOS, useAvisosSinLeer } from '../../navegacion/Campana';
import { SECCIONES, destinoDeAviso } from '../../navegacion/secciones';
import { fechaHoraCorta } from '../../util/fechas';

const V = cuenta.avisos;

/** El icono de la sección a la que lleva un aviso; la campana si no lleva a ninguna. */
function iconoDe(rutaDestino: string | undefined): NombreIcono {
  return SECCIONES.find((s) => s.ruta === rutaDestino)?.icono ?? 'campana';
}

export function Avisos() {
  const navegar = useNavigate();
  const lista = useListaPaginada([...CLAVES_AVISOS.todo, 'lista'], (pagina) =>
    pedir(cliente.GET('/api/v1/avisos', { params: { query: { pagina, tamano: 30 } } })),
  );

  const marcarLeido = useMutacion(
    (id: number) => pedir(cliente.PATCH('/api/v1/avisos/{id}/leido', { params: { path: { id } } })),
    { invalida: [CLAVES_AVISOS.todo] },
  );
  const marcarTodos = useMutacion(() => pedir(cliente.PATCH('/api/v1/avisos/leer-todos', {})), {
    invalida: [CLAVES_AVISOS.todo],
    exito: V.todosLeidos,
  });

  function abrir(id: number | undefined, leido: boolean, rutaDestino: string | undefined) {
    if (id !== undefined && !leido) marcarLeido.mutate(id);
    const destino = destinoDeAviso(rutaDestino);
    if (destino !== null) navegar(destino);
  }

  const hayNoLeidos = lista.elementos.some((a) => a.leido !== true);
  const sinLeer = useAvisosSinLeer();

  let contenido;
  if (lista.isPending) contenido = <Esqueleto lineas={6} />;
  else if (lista.isError && lista.elementos.length === 0) {
    contenido = <ErrorConReintento mensaje={lista.error.message} alReintentar={() => void lista.refetch()} />;
  } else if (lista.elementos.length === 0) contenido = <Vacio titulo={V.vacioTitulo} detalle={V.vacioTexto} />;
  else {
    contenido = (
      <>
        <ul className="nx-lista-avisos">
          {lista.elementos.map((a) => (
            <li key={a.id}>
              <button
                type="button"
                className={`nx-lista-avisos__aviso${a.leido === true ? '' : ' nx-lista-avisos__aviso--nuevo'}`}
                onClick={() => abrir(a.id, a.leido === true, a.rutaDestino)}
              >
                <span className="nx-lista-avisos__icono">
                  <Icono nombre={iconoDe(a.rutaDestino)} tamano={20} />
                </span>
                <span className="nx-lista-avisos__texto">
                  <span className="nx-lista-avisos__titulo">
                    {a.leido !== true && <span className="nx-solo-lector">{V.sinLeer}: </span>}
                    {a.titulo}
                  </span>
                  {a.cuerpo !== undefined && <span className="nx-sutil">{a.cuerpo}</span>}
                </span>
                <span className="nx-sutil nx-lista-avisos__cuando">{fechaHoraCorta(a.creadoEn)}</span>
              </button>
            </li>
          ))}
        </ul>
        <FinDeLista
          hayMas={lista.hasNextPage}
          cargando={lista.isFetchingNextPage}
          fallo={lista.isFetchNextPageError}
          alPedirMas={() => void lista.fetchNextPage()}
        />
      </>
    );
  }

  return (
    <div className="nx-pagina">
      <CabeceraDePagina
        titulo={V.titulo}
        descripcion={V.explicacion}
        acciones={
          <>
            {hayNoLeidos && (
              <Boton variante="texto" ocupado={marcarTodos.isPending} onClick={() => marcarTodos.mutate(undefined)}>
                {V.marcarTodos}
              </Boton>
            )}
          </>
        }
      />
      {marcarTodos.error !== null && <Aviso>{marcarTodos.error.message}</Aviso>}
      <div className="nx-composicion nx-composicion--principal-lateral">
        <section className="nx-tarjeta">{contenido}</section>

        <div className="nx-columna">
          {sinLeer.data !== undefined && (
            <section className="nx-tarjeta">
              <Cifras>
                <Cifra
                  icono={sinLeer.data > 0 ? 'campana' : 'hecho'}
                  etiqueta={V.resumen}
                  valor={sinLeer.data}
                  tono={sinLeer.data > 0 ? 'destacada' : 'normal'}
                  {...(sinLeer.data === 0 ? { detalle: V.alDia } : {})}
                />
              </Cifras>
            </section>
          )}
          <Tarjeta titulo={V.ajustesTitulo} descripcion={V.ajustesTexto}>
            <Link className="nx-enlace" to="/ajustes#ajustes-notificaciones">
              {V.ajustesEnlace}
            </Link>
          </Tarjeta>
        </div>
      </div>
    </div>
  );
}
