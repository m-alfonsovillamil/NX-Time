/**
 * La campana de avisos: el contador y los últimos avisos.
 *
 * **El contador se pide cada minuto y solo con la pestaña visible.** Es la
 * petición más repetida de la web; con la pestaña en segundo plano no la ve
 * nadie, y mantener despierto el servidor de Render por una pestaña olvidada
 * sería gastar sus horas gratis en nada. TanStack Query ya hace eso por
 * defecto (`refetchIntervalInBackground: false`), y al volver a la pestaña
 * pide en el acto.
 *
 * Al abrirla enseña la primera página de avisos. La lista completa, con
 * paginación, es la página `/avisos` (llega en W3); hasta entonces, esto es
 * lo que hay, y un aviso cuyo destino aún no tiene página se marca como
 * leído pero no navega.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { cliente } from '../api/cliente';
import { pedir, useMutacion } from '../api/consultas';
import { Aviso, Boton } from '../componentes/Basicos';
import { Dialogo } from '../componentes/Dialogo';
import { EstadoDeConsulta, Vacio } from '../componentes/Estados';
import { Icono } from '../componentes/Icono';
import { T } from '../i18n/es';
import { fechaHoraCorta } from '../util/fechas';
import { destinoDeAviso } from './secciones';

const C = T.navegacion.campana;

export const CLAVES_AVISOS = {
  todo: ['avisos'] as const,
  noLeidos: ['avisos', 'no-leidos'] as const,
  recientes: ['avisos', 'recientes'] as const,
};

const CADA_MINUTO = 60_000;
const RECIENTES = 10;

export function Campana() {
  const [abierta, setAbierta] = useState(false);
  const navegar = useNavigate();

  const noLeidos = useQuery({
    queryKey: CLAVES_AVISOS.noLeidos,
    queryFn: () => pedir(cliente.GET('/api/v1/avisos/no-leidos', {})),
    refetchInterval: CADA_MINUTO,
    select: (r) => r.noLeidos ?? 0,
  });

  const recientes = useQuery({
    queryKey: CLAVES_AVISOS.recientes,
    queryFn: () => pedir(cliente.GET('/api/v1/avisos', { params: { query: { pagina: 0, tamano: RECIENTES } } })),
    enabled: abierta,
    select: (p) => p.contenido ?? [],
  });

  const marcarLeido = useMutacion(
    (id: number) => pedir(cliente.PATCH('/api/v1/avisos/{id}/leido', { params: { path: { id } } })),
    { invalida: [CLAVES_AVISOS.todo] },
  );
  const marcarTodos = useMutacion(() => pedir(cliente.PATCH('/api/v1/avisos/leer-todos', {})), {
    invalida: [CLAVES_AVISOS.todo],
  });

  function abrirAviso(id: number | undefined, leido: boolean, rutaDestino: string | undefined) {
    if (id !== undefined && !leido) marcarLeido.mutate(id);
    const destino = destinoDeAviso(rutaDestino);
    if (destino !== null) {
      setAbierta(false);
      navegar(destino);
    }
  }

  const cuantos = noLeidos.data ?? 0;

  return (
    <>
      <button
        type="button"
        className="nx-campana"
        aria-label={C.etiqueta(cuantos)}
        onClick={() => setAbierta(true)}
      >
        <Icono nombre="campana" />
        {cuantos > 0 && (
          <span className="nx-campana__contador" aria-hidden="true">
            {cuantos > 99 ? '99+' : cuantos}
          </span>
        )}
      </button>

      <Dialogo
        abierto={abierta}
        titulo={C.titulo}
        alCerrar={() => setAbierta(false)}
        acciones={
          <>
            {cuantos > 0 && (
              <Boton variante="texto" ocupado={marcarTodos.isPending} onClick={() => marcarTodos.mutate(undefined)}>
                {C.marcarTodos}
              </Boton>
            )}
            <Boton variante="texto" onClick={() => setAbierta(false)}>
              {T.app.cerrar}
            </Boton>
          </>
        }
      >
        {marcarTodos.error !== null && <Aviso>{marcarTodos.error.message}</Aviso>}
        <EstadoDeConsulta consulta={recientes}>
          {(avisos) =>
            avisos.length === 0 ? (
              <Vacio titulo={C.ninguno} />
            ) : (
              <ul className="nx-lista-avisos">
                {avisos.map((a) => (
                  <li key={a.id}>
                    <button
                      type="button"
                      className={`nx-lista-avisos__aviso${a.leido === true ? '' : ' nx-lista-avisos__aviso--nuevo'}`}
                      onClick={() => abrirAviso(a.id, a.leido === true, a.rutaDestino)}
                    >
                      <span className="nx-lista-avisos__titulo">
                        {a.leido !== true && <span className="nx-solo-lector">{C.sinLeer}: </span>}
                        {a.titulo}
                      </span>
                      {a.cuerpo !== undefined && <span className="nx-sutil">{a.cuerpo}</span>}
                      <span className="nx-sutil">{fechaHoraCorta(a.creadoEn)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )
          }
        </EstadoDeConsulta>
      </Dialogo>
    </>
  );
}
