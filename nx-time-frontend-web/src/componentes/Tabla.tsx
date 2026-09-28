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
 *
 * **Si no cabe a lo ancho, se desplaza dentro de su caja, y entonces tiene que
 * poder recibir el foco**: sin él, quien usa el teclado no puede moverla y se
 * pierde las columnas de la derecha (WCAG 2.1.1; axe lo llama
 * `scrollable-region-focusable`). Solo cuando se desborda, porque una tabla que
 * cabe no tiene nada que desplazar y sería una parada del tabulador de más.
 * Salió en el CI de W8: la analítica cabía a 1280 px con las fuentes de
 * Windows y no con las de Linux.
 */

import { useEffect, useRef, useState, type ReactNode } from 'react';

/** Si el elemento tiene más contenido a lo ancho del que enseña, y lo sigue mientras cambie de tamaño. */
function useSeDesborda<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [desborda, setDesborda] = useState(false);
  useEffect(() => {
    const el = ref.current;
    // jsdom (los tests) no tiene ResizeObserver ni mide nada: ahí nunca desborda.
    if (el === null || typeof ResizeObserver === 'undefined') return;
    const medir = () => setDesborda(el.scrollWidth > el.clientWidth + 1);
    const observador = new ResizeObserver(medir);
    observador.observe(el);
    // La tabla crece sin que cambie la caja cuando llegan más filas.
    if (el.firstElementChild !== null) observador.observe(el.firstElementChild);
    medir();
    return () => observador.disconnect();
  }, []);
  return { ref, desborda };
}

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
  const { ref, desborda } = useSeDesborda<HTMLDivElement>();
  return (
    <div
      ref={ref}
      className="nx-tabla-contenedor"
      {...(desborda && { tabIndex: 0, role: 'region', 'aria-label': titulo })}
    >
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
