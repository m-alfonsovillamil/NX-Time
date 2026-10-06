/**
 * Una cifra con su nombre: «43h 05m, esta semana», «2 ausencias por aprobar».
 *
 * Había tres copias (`Resumen`, `PanelEmpresa`, `Analitica`) con dos estilos
 * distintos, y dentro de una tarjeta blanca una de ellas no se veía: su fondo
 * era `surface-container`, que en el tema claro también es blanco. Ahora es una
 * sola, con su icono, su barra de progreso si la tiene y un enlace si lleva a
 * alguna parte.
 *
 * **La cifra va delante en el HTML** y el CSS la pinta debajo del nombre. Así
 * un lector de pantalla oye «2 Ausencias por aprobar», que es como se dice, y
 * a la vista el nombre hace de título del recuadro.
 */

import { Children, type ReactNode } from 'react';
import { Link } from 'react-router';

import { Icono, type NombreIcono } from './Icono';

/** `destacada`: hay algo (un contador que no es cero). `alerta`: algo que no debería pasar desapercibido. */
export type TonoDeCifra = 'normal' | 'destacada' | 'alerta';

/**
 * La rejilla. **Nunca tres y una sola debajo**: con cuatro van de dos en dos
 * o las cuatro en fila; con seis, de tres en tres o las seis. Cuántas caben
 * lo decide el ancho de la tarjeta (una consulta de contenedor), no el de la
 * pantalla: la misma rejilla va en media columna de Mi jornada y a lo ancho
 * de la analítica.
 */
export function Cifras({ children, etiqueta }: { children: ReactNode; etiqueta?: string }) {
  const cuantas = Children.toArray(children).length;
  return (
    <div className="nx-cifras-contenedor">
      <ul className="nx-cifras" data-cuantas={cuantas} aria-label={etiqueta}>
        {children}
      </ul>
    </div>
  );
}

export function Cifra({
  etiqueta,
  valor,
  detalle,
  icono,
  tono = 'normal',
  progreso,
  a,
}: {
  etiqueta: string;
  valor: string | number;
  detalle?: string;
  icono?: NombreIcono;
  tono?: TonoDeCifra;
  /** De 0 a 1: lo gastado de una bolsa, las horas de la semana sobre las esperadas. */
  progreso?: number;
  /** La ruta a la que lleva, si lleva a alguna. */
  a?: string;
}) {
  const contenido = (
    <>
      {/* El espacio no se ve (es flex), pero sin él un lector de pantalla
          lee «2Ausencias por aprobar». */}
      <span className="nx-cifra__valor">{valor}</span>{' '}
      <span className="nx-cifra__cabeza">
        {icono !== undefined && (
          <span className="nx-cifra__icono">
            <Icono nombre={icono} tamano={20} />
          </span>
        )}
        <span className="nx-cifra__etiqueta">{etiqueta}</span>
      </span>
      {detalle !== undefined && (
        <>
          {' '}
          <span className="nx-cifra__detalle">{detalle}</span>
        </>
      )}
      {progreso !== undefined && (
        <span className="nx-cifra__carril" aria-hidden="true">
          <span
            className={`nx-cifra__relleno${progreso > 1 ? ' nx-cifra__relleno--pasado' : ''}`}
            style={{ width: `${Math.round(Math.min(1, Math.max(0, progreso)) * 100)}%` }}
          />
        </span>
      )}
    </>
  );
  const clase = `nx-cifra nx-cifra--${tono}`;
  return (
    <li>
      {a !== undefined ? (
        <Link className={`${clase} nx-cifra--enlace`} to={a}>
          {contenido}
        </Link>
      ) : (
        <div className={clase}>{contenido}</div>
      )}
    </li>
  );
}
