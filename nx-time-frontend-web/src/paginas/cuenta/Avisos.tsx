/**
 * Todos mis avisos, por páginas: la lista completa de lo que la campana resume.
 *
 * Abrir un aviso lo marca leído y, si su destino tiene página, lleva a ella: el
 * destino ES la URL (ADR 029, decisión 4). Uno cuyo destino aún no existe en
 * la web se marca leído y se queda aquí, que es lo mismo que hace la app con
 * un destino que su versión no conoce.
 */

import { useNavigate } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useListaPaginada, useMutacion } from '../../api/consultas';
import { Aviso, Boton } from '../../componentes/Basicos';
import { ErrorConReintento, Esqueleto, FinDeLista, Vacio } from '../../componentes/Estados';
import { cuenta } from '../../i18n/es/cuenta';
import { CLAVES_AVISOS } from '../../navegacion/Campana';
import { destinoDeAviso } from '../../navegacion/secciones';
import { fechaHoraCorta } from '../../util/fechas';

const V = cuenta.avisos;

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
                <span className="nx-lista-avisos__titulo">
                  {a.leido !== true && <span className="nx-solo-lector">{V.sinLeer}: </span>}
                  {a.titulo}
                </span>
                {a.cuerpo !== undefined && <span className="nx-sutil">{a.cuerpo}</span>}
                <span className="nx-sutil">{fechaHoraCorta(a.creadoEn)}</span>
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
    <div className="nx-pagina nx-pagina--estrecha">
      <header className="nx-cabecera">
        <h1>{V.titulo}</h1>
        {hayNoLeidos && (
          <Boton variante="texto" ocupado={marcarTodos.isPending} onClick={() => marcarTodos.mutate(undefined)}>
            {V.marcarTodos}
          </Boton>
        )}
      </header>
      {marcarTodos.error !== null && <Aviso>{marcarTodos.error.message}</Aviso>}
      <section className="nx-tarjeta">{contenido}</section>
    </div>
  );
}
