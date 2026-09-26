/**
 * Los estados que no son «aquí están los datos»: cargando, vacío, fallo y fin de lista.
 *
 * Todas las pantallas pasan por ellos, y si cada una los pintara a su manera
 * la web parecería hecha por cinco personas. Además, aquí se decide algo que
 * no es de estilo: **un arranque en frío de Render no es un error.** La
 * primera petición del día puede tardar minutos; mientras, se ve un esqueleto
 * (y el cartel de `ServidorDespertando`), no un mensaje de fallo.
 */

import type { UseQueryResult } from '@tanstack/react-query';
import { useEffect, useRef, type ReactNode } from 'react';

import { T } from '../i18n/es';
import { Boton } from './Basicos';

/**
 * Unas barras grises con la forma aproximada de lo que viene.
 *
 * El texto «Cargando…» sigue ahí, oculto a la vista: las barras son
 * decoración y un lector de pantalla necesita saber que algo está en marcha.
 */
export function Esqueleto({ lineas = 3 }: { lineas?: number }) {
  return (
    <div className="nx-esqueleto" role="status">
      <span className="nx-solo-lector">{T.app.cargando}</span>
      {Array.from({ length: lineas }, (_, i) => (
        <span key={i} className="nx-esqueleto__linea" aria-hidden="true" />
      ))}
    </div>
  );
}

export function Vacio({ titulo, detalle, accion }: { titulo: string; detalle?: string; accion?: ReactNode }) {
  return (
    <div className="nx-vacio">
      <p className="nx-vacio__titulo">{titulo}</p>
      {detalle !== undefined && <p className="nx-sutil">{detalle}</p>}
      {accion}
    </div>
  );
}

/** Un fallo al cargar, con la salida al lado: reintentar sin recargar la página. */
export function ErrorConReintento({ mensaje, alReintentar }: { mensaje: string; alReintentar: () => void }) {
  return (
    <div className="nx-aviso nx-aviso--con-accion" role="alert">
      <span>{mensaje}</span>
      <Boton variante="texto" onClick={alReintentar}>
        {T.app.reintentar}
      </Boton>
    </div>
  );
}

/**
 * Los tres estados de una consulta, siempre igual: esqueleto, fallo con
 * reintentar, o los datos.
 *
 * ```tsx
 * <EstadoDeConsulta consulta={jornada}>{(j) => <Cronometro jornada={j} />}</EstadoDeConsulta>
 * ```
 */
export function EstadoDeConsulta<D>({
  consulta,
  cargando,
  children,
}: {
  consulta: UseQueryResult<D, Error>;
  cargando?: ReactNode;
  children: (datos: D) => ReactNode;
}) {
  if (consulta.isPending) return <>{cargando ?? <Esqueleto />}</>;
  if (consulta.isError) {
    return <ErrorConReintento mensaje={consulta.error.message} alReintentar={() => void consulta.refetch()} />;
  }
  return <>{children(consulta.data)}</>;
}

/**
 * El final de una lista por páginas: pide la siguiente al verse.
 *
 * Si la página falla, **no** se reintenta sola: se para y ofrece reintentar,
 * como `finDeLista` en Android. Reintentar en bucle cada vez que el final
 * vuelve a verse convertiría una caída del servidor en una ráfaga de
 * peticiones.
 *
 * Sin `IntersectionObserver` (navegadores viejos, los tests) queda un botón
 * de «Cargar más».
 */
export function FinDeLista({
  hayMas,
  cargando,
  fallo,
  alPedirMas,
}: {
  hayMas: boolean;
  cargando: boolean;
  fallo: boolean;
  alPedirMas: () => void;
}) {
  const centinela = useRef<HTMLDivElement>(null);
  const pedir = useRef(alPedirMas);
  pedir.current = alPedirMas;
  const puedeObservar = typeof IntersectionObserver !== 'undefined';

  useEffect(() => {
    const nodo = centinela.current;
    if (!puedeObservar || nodo === null || !hayMas || cargando || fallo) return;
    const observador = new IntersectionObserver((entradas) => {
      if (entradas.some((e) => e.isIntersecting)) pedir.current();
    });
    observador.observe(nodo);
    return () => observador.disconnect();
  }, [puedeObservar, hayMas, cargando, fallo]);

  if (!hayMas) return null;

  return (
    <div ref={centinela} className="nx-fin-de-lista">
      {fallo ? (
        <ErrorConReintento mensaje={T.listas.falloAlCargarMas} alReintentar={alPedirMas} />
      ) : cargando ? (
        <p className="nx-sutil" role="status">
          {T.listas.cargandoMas}
        </p>
      ) : (
        !puedeObservar && (
          <Boton variante="secundario" onClick={alPedirMas}>
            {T.listas.cargarMas}
          </Boton>
        )
      )}
    </div>
  );
}
