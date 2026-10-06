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
import { Icono, type NombreIcono } from './Icono';

/**
 * Unas barras grises con la forma aproximada de lo que viene.
 *
 * **Con la forma de lo que viene, y a su ancho**: unas líneas de texto, una
 * rejilla de recuadros o las filas de una tabla. Antes era siempre una
 * columna de 560 px centrada, y en una página ancha la carga terminaba con un
 * salto: el bloque gris estrecho se convertía de golpe en una tabla de lado a
 * lado (lo que Lighthouse cuenta como CLS).
 *
 * El texto «Cargando…» sigue ahí, oculto a la vista: las barras son
 * decoración y un lector de pantalla necesita saber que algo está en marcha.
 */
export function Esqueleto({
  lineas = 3,
  forma = 'lineas',
}: {
  /** Las líneas de texto, las filas de la tabla o los recuadros de la rejilla. */
  lineas?: number;
  forma?: 'lineas' | 'tabla' | 'recuadros';
}) {
  return (
    <div className={`nx-esqueleto nx-esqueleto--${forma}`} role="status">
      <span className="nx-solo-lector">{T.app.cargando}</span>
      {Array.from({ length: forma === 'tabla' ? lineas + 1 : lineas }, (_, i) => (
        <span key={i} className="nx-esqueleto__linea" aria-hidden="true" />
      ))}
    </div>
  );
}

/**
 * Lo que se ve mientras llega el trozo de JS de una página: su cabecera y
 * dos tarjetas, al ancho de una página de verdad.
 */
export function EsqueletoDePagina() {
  return (
    <div className="nx-pagina">
      <div className="nx-esqueleto nx-esqueleto--pagina" role="status">
        <span className="nx-solo-lector">{T.app.cargando}</span>
        <span className="nx-esqueleto__linea nx-esqueleto__titulo" aria-hidden="true" />
        <span className="nx-esqueleto__bloque" aria-hidden="true" />
        <span className="nx-esqueleto__bloque" aria-hidden="true" />
      </div>
    </div>
  );
}

/**
 * Una lista o un panel sin nada que enseñar.
 *
 * Con su icono en un círculo: un texto suelto en mitad de una tarjeta se leía
 * como un error de maquetación más que como «aquí no hay nada, y está bien».
 */
export function Vacio({
  titulo,
  detalle,
  accion,
  icono = 'bandeja',
}: {
  titulo: string;
  detalle?: string;
  accion?: ReactNode;
  icono?: NombreIcono;
}) {
  return (
    <div className="nx-vacio">
      <span className="nx-vacio__icono">
        <Icono nombre={icono} />
      </span>
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
