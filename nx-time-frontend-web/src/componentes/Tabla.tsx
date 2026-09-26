/**
 * Una tabla que en el móvil se vuelve tarjetas.
 *
 * Es el componente que más va a usar la parte de gestión, y el motivo de que
 * exista en vez de un `<table>` suelto en cada pantalla es el móvil: la web es
 * hoy el único cliente para quien tenga iPhone, y una tabla de seis columnas en
 * 375 px o se sale o se lee con lupa.
 *
 * **Un solo marcado para los dos tamaños.** Por debajo de 640 px el CSS pinta
 * cada fila como una tarjeta y pone delante de cada celda el nombre de su
 * columna, que viaja en `data-etiqueta`. Duplicar el contenido en una lista
 * aparte para el móvil habría sido más fácil de maquetar y el doble de DOM, y
 * dos sitios donde se puede olvidar una columna.
 */

import type { ReactNode } from 'react';

export interface Columna<F> {
  clave: string;
  cabecera: string;
  celda: (fila: F) => ReactNode;
  /** Cifras: alineadas a la derecha y con dígitos de ancho fijo, para que se puedan comparar de un vistazo. */
  numerica?: boolean;
}

export function Tabla<F>({
  titulo,
  columnas,
  filas,
  claveDeFila,
}: {
  /** Para quien no ve la tabla: un lector de pantalla lo anuncia al entrar en ella. */
  titulo: string;
  columnas: readonly Columna<F>[];
  filas: readonly F[];
  claveDeFila: (fila: F) => string | number;
}) {
  return (
    <div className="nx-tabla-contenedor">
      <table className="nx-tabla">
        <caption className="nx-solo-lector">{titulo}</caption>
        <thead>
          <tr>
            {columnas.map((c) => (
              <th key={c.clave} scope="col" className={c.numerica === true ? 'nx-tabla__numerica' : undefined}>
                {c.cabecera}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filas.map((fila) => (
            <tr key={claveDeFila(fila)}>
              {columnas.map((c) => (
                <td
                  key={c.clave}
                  data-etiqueta={c.cabecera}
                  className={c.numerica === true ? 'nx-tabla__numerica' : undefined}
                >
                  {c.celda(fila)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
