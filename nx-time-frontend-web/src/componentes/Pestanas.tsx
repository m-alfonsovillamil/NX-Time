/**
 * Pestañas con el patrón de ARIA: flechas para moverse, Tab para salir.
 *
 * Solo la pestaña activa entra en el orden de tabulación (`tabIndex` 0); las
 * demás se alcanzan con las flechas, Inicio y Fin. Es lo que espera quien usa
 * un lector de pantalla, y lo que hace que cruzar una fila de pestañas cueste
 * una pulsación y no una por pestaña.
 */

import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react';

export function Pestanas<K extends string>({
  etiqueta,
  pestanas,
  activa,
  alCambiar,
  children,
}: {
  /** Qué se elige con ellas («Estado de las ausencias»). */
  etiqueta: string;
  pestanas: readonly { clave: K; texto: string }[];
  activa: K;
  alCambiar: (clave: K) => void;
  /** El contenido de la pestaña activa. */
  children: ReactNode;
}) {
  const base = useId();
  const botones = useRef(new Map<K, HTMLButtonElement>());

  function mover(evento: KeyboardEvent, indice: number) {
    const ultimo = pestanas.length - 1;
    const destino =
      evento.key === 'ArrowRight'
        ? indice === ultimo ? 0 : indice + 1
        : evento.key === 'ArrowLeft'
          ? indice === 0 ? ultimo : indice - 1
          : evento.key === 'Home'
            ? 0
            : evento.key === 'End'
              ? ultimo
              : null;
    if (destino === null) return;
    evento.preventDefault();
    const clave = pestanas[destino]?.clave;
    if (clave === undefined) return;
    alCambiar(clave);
    botones.current.get(clave)?.focus();
  }

  return (
    <div className="nx-pestanas">
      <div role="tablist" aria-label={etiqueta} className="nx-pestanas__lista">
        {pestanas.map((p, i) => (
          <button
            key={p.clave}
            ref={(nodo) => {
              if (nodo === null) botones.current.delete(p.clave);
              else botones.current.set(p.clave, nodo);
            }}
            type="button"
            role="tab"
            id={`${base}-${p.clave}`}
            aria-selected={p.clave === activa}
            aria-controls={`${base}-panel`}
            tabIndex={p.clave === activa ? 0 : -1}
            className="nx-pestanas__pestana"
            onClick={() => alCambiar(p.clave)}
            onKeyDown={(e) => mover(e, i)}
          >
            {p.texto}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${base}-panel`} aria-labelledby={`${base}-${activa}`} tabIndex={0}>
        {children}
      </div>
    </div>
  );
}
